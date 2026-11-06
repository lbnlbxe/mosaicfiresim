// See LICENSE for license details
//
// Full MoSAIC simulation top: programs the design through the AxiLiteBridge
// (reset picos, load firmware, set coordinates, start processing) before
// running the simulation, mirroring the MoSAIC-P38 FPGA shell flow in
// shell/fpga/fp/{wrapper_sv.h,set_system.c}.
//
// For a reset-only smoke test without the AxiLiteBridge, see
// simple_mosaic_top.cc (select it with MOSAIC_SIM_TOP in
// src/main/makefrag/mosaic/driver.mk).

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <string>
#include <vector>

#include "bridges/axilite.h"
#include "bridges/clock.h"
#include "bridges/heartbeat.h"
#include "bridges/peek_poke.h"
#include "core/simif.h"
#include "core/simulation.h"
#include "core/systematic_scheduler.h"

// Default location of the MoSAIC pico firmware hex files (overridable at
// compile time, or at runtime with +fw-pico0=<path> / +fw-pico1=<path>).
// MOSAIC_FW_DIR is passed as a bare token (quoting does not survive
// verilator's -CFLAGS plumbing) and stringified here.
#ifndef MOSAIC_FW_DIR
#define MOSAIC_FW_DIR .
#endif
#define MOSAIC_FW_DIR_STR_(x) #x
#define MOSAIC_FW_DIR_STR(x) MOSAIC_FW_DIR_STR_(x)
#define MOSAIC_FW_DIR_PATH MOSAIC_FW_DIR_STR(MOSAIC_FW_DIR)

// simulation top-level class. always should extend 'simulation_t' (and implement it's virtual functions).
// it also extends 'systematic_scheduler_t' to provide the '+max-cycles' simulator CLI arg ("plusarg") to terminate a simulation.
class mosaic_top_t : public systematic_scheduler_t, public simulation_t
{
public:
    mosaic_top_t(simif_t &simif,
                 widget_registry_t &registry,
                 const std::vector<std::string> &args);

    int simulation_run() override;

    bool simulation_timed_out() override { return !terminated; }

private:
    simif_t &simif;
    /// Reference to the peek-poke bridge.
    peek_poke_t &peek_poke;
    /// Reference to the AXI4-Lite control bridge.
    axilite_t &axilite;
    /// Flag to indicate that the simulation was terminated.
    bool terminated = false;

    ////////////////
    // Stepping helpers
    ////////////////

    /// Advance the target by `cycles` cycles, servicing bridge channels.
    void step(size_t cycles);

    /// Advance the target until the outstanding AXI4-Lite transaction
    /// completes (bounded by `max_cycles`); aborts on timeout.
    void axilite_wait_done(size_t max_cycles = 1024);

    /// Blocking AXI4-Lite write through the bridge. Aborts on AXI error.
    void axilite_write(uint32_t addr, uint32_t data);

    /// Blocking AXI4-Lite read through the bridge. Aborts on AXI error.
    uint32_t axilite_read(uint32_t addr);

    ////////////////
    // MoSAIC tile control-register protocol
    // (mirrors MoSAIC-P38 shell/fpga/fp/{wrapper_sv.h,set_system.c})
    ////////////////

    // Offsets of the registers within a tile's AXI4-Lite register block.
    static constexpr uint32_t TILE_STAT     = 0x0;
    static constexpr uint32_t TILE_CMND     = 0x4;
    static constexpr uint32_t TILE_ADDR     = 0x8;
    static constexpr uint32_t TILE_DATA     = 0xc;
    static constexpr uint32_t TILE_COORDS   = 0x10;
    static constexpr uint32_t TILE_RXCOUNT  = 0x14;

    // Operations written to the CMND register.
    static constexpr uint32_t OP_WRITE = 1;
    static constexpr uint32_t OP_READ  = 2;
    static constexpr uint32_t OP_STATUS = 3;
    static constexpr uint32_t OP_RISCV = 4;

    // Cycles to let a tile-side command take effect after its CMND write
    // completes on the AXI4-Lite control bus.
    static constexpr size_t TILE_CMD_SETTLE = 64;

    /// Direct write of a tile control register.
    void tile_write_reg(uint32_t tile, uint32_t offset, uint32_t data);

    /// Direct read of a tile control register.
    uint32_t tile_read_reg(uint32_t tile, uint32_t offset);

    /// Indirect (CMND-driven) write of tile memory at `addr`.
    void indirect_write(uint32_t tile, uint32_t addr, uint32_t data);

    /// Indirect (CMND-driven) read of tile memory at `addr`.
    uint32_t indirect_read(uint32_t tile, uint32_t addr);

    /// Write the pico control register (rv_op): 0x00 resets, 0x01 starts.
    void rv_set_status(uint32_t tile, uint32_t data);

    /// Read back the pico control register via the status op.
    uint32_t rv_get_status(uint32_t tile);

    /// Load a hex firmware image into a tile's memory (format: optional
    /// '@<hex-addr>' lines set the word address; each other line is one
    /// 32-bit word; address increments by one word per line).
    void rv_load_firmware(uint32_t tile, const std::string &hexfile);

    ////////////////
    // MoSAIC system bring-up
    ////////////////

    /// Program all tiles: reset picos, load firmware, set coordinates,
    /// and start processing.
    void setup_mosaic();

    // Tile base addresses for the 2x2 configuration, in row-major order.
    static constexpr uint32_t TILE_ADDRESSES[2][2] = {
        {0x000, 0x100},
        {0x200, 0x300},
    };

    // Tile coordinates for the 2x2 configuration, in row-major order.
    static constexpr uint32_t TILE_COORDINATES[2][2] = {
        {0x00, 0x08},
        {0x01, 0x09},
    };

    // Pico tiles and their firmware images.
    std::string fw_pico0 = MOSAIC_FW_DIR_PATH "/pico_scratchpad.hex"; // tile 0x000
    std::string fw_pico1 = MOSAIC_FW_DIR_PATH "/test_tile_nop.hex";   // tile 0x300
};

mosaic_top_t::mosaic_top_t(simif_t &simif,
                           widget_registry_t &registry,
                           const std::vector<std::string> &args)
    : systematic_scheduler_t(args), simulation_t(registry, args), simif(simif),
      peek_poke(registry.get_widget<peek_poke_t>()),
      axilite(registry.get_widget<axilite_t>())
{
    // helper to verify that simulation is running
    registry.add_widget(
        new heartbeat_t(simif, registry.get_widget<clockmodule_t>(), args));

    for (const auto &arg : args) {
        auto consume = [&](const std::string &key, std::string &value) {
            if (arg.rfind(key, 0) == 0) { value = arg.substr(key.size()); return true; }
            return false;
        };
        consume("+fw-pico0=", fw_pico0);
        consume("+fw-pico1=", fw_pico1);
    }
}

void mosaic_top_t::step(size_t cycles)
{
    peek_poke.step(cycles, false);
    while (!peek_poke.is_done()) {
        for (auto *bridge : registry.get_all_bridges()) {
            bridge->tick();
        }
    }
}

void mosaic_top_t::axilite_wait_done(size_t max_cycles)
{
    // Phase 1: wait for the start command to be accepted, which clears the
    // widget's done flag. Control-register writes take target cycles to reach
    // the widget, so done() may still read 1 from the PREVIOUS transaction
    // here; a single-phase poll returns immediately and lets the caller
    // sample stale results (the bug behind the all-zero readbacks).
    size_t waited = 0;
    while (axilite.done()) {
        step(1);
        if (++waited > max_cycles) {
            fprintf(stderr, "[AXILITE] Timeout waiting for transaction acceptance\n");
            abort();
        }
    }
    // Phase 2: wait for the transaction to complete (done reasserts).
    waited = 0;
    while (!axilite.done()) {
        step(1);
        if (++waited > max_cycles) {
            fprintf(stderr, "[AXILITE] Timeout waiting for transaction completion\n");
            abort();
        }
    }
    axilite.complete();
}

void mosaic_top_t::axilite_write(uint32_t addr, uint32_t data)
{
    if (!axilite.issue_write(addr, data)) {
        fprintf(stderr, "[AXILITE] issue_write while transaction in flight\n");
        abort();
    }
    axilite_wait_done();
    if (axilite.resp() != 0) {
        fprintf(stderr, "[AXILITE] Write to 0x%x failed with AXI resp 0x%x\n", addr, axilite.resp());
        abort();
    }
}

uint32_t mosaic_top_t::axilite_read(uint32_t addr)
{
    if (!axilite.issue_read(addr)) {
        fprintf(stderr, "[AXILITE] issue_read while transaction in flight\n");
        abort();
    }
    axilite_wait_done();
    if (axilite.resp() != 0) {
        fprintf(stderr, "[AXILITE] Read from 0x%x failed with AXI resp 0x%x\n", addr, axilite.resp());
        abort();
    }
    return axilite.rdata();
}

void mosaic_top_t::tile_write_reg(uint32_t tile, uint32_t offset, uint32_t data)
{
    axilite_write(tile + offset, data);
}

uint32_t mosaic_top_t::tile_read_reg(uint32_t tile, uint32_t offset)
{
    // Upstream MoSAIC quirk (S_CONTROLLER_USS.sv): tile_S_AXI_ARADDR is
    // driven from control_S_AXI_AWADDR (copy-paste bug), so a read is served
    // from the register at the LAST-WRITTEN AW offset rather than the AR
    // offset. The AR *tile* routing still uses the true AR address, so only
    // the register offset is affected.
    //
    // Workaround: before a read, prime the widget's awaddr with a write to
    // the offset being read:
    //   - 0x0 (STAT), 0x14 (RX_PACKET_COUNT), 0x18: undecoded in TileRegBlock
    //     (write ACKed, no register updated) -> safe on the same tile.
    //   - 0xc (DATA): decoded; prime on a DIFFERENT tile so the target tile's
    //     Data_ register (e.g. holding an indirect-read result) is untouched.
    //   - 0x4 (CMND), 0x8 (ADDR), 0x10 (COORDINATES): decoded, CMND triggers
    //     ops -> never primed; callers must read these right after writing
    //     the same offset (which leaves awaddr already correct).
    switch (offset) {
    case 0x0:
    case 0x14:
    case 0x18:
        tile_write_reg(tile, offset, 0);
        break;
    case 0xc: {
        uint32_t other = (tile == TILE_ADDRESSES[0][0]) ? TILE_ADDRESSES[0][1]
                                                        : TILE_ADDRESSES[0][0];
        tile_write_reg(other, offset, 0);
        break;
    }
    default:
        break;
    }
    return axilite_read(tile + offset);
}

void mosaic_top_t::indirect_write(uint32_t tile, uint32_t addr, uint32_t data)
{
    tile_write_reg(tile, TILE_ADDR, addr);
    tile_write_reg(tile, TILE_DATA, data);
    tile_write_reg(tile, TILE_CMND, OP_WRITE);
    // Let the tile-side operation take effect before the next command.
    step(TILE_CMD_SETTLE);
}

uint32_t mosaic_top_t::indirect_read(uint32_t tile, uint32_t addr)
{
    tile_write_reg(tile, TILE_ADDR, addr);
    tile_write_reg(tile, TILE_CMND, OP_READ);
    step(TILE_CMD_SETTLE);
    // dp_ram's doutb is a REGISTERED read (doutb <= mem[addrb]), but the
    // tile captures Data_ <= mem_dout at the csrEn pulse one cycle after the
    // CMND write - before doutb has updated. The first command therefore
    // yields mem[previous-address]; issuing the command a second time makes
    // the second csrEn pulse capture the settled doutb = mem[addr]. No side
    // effects: Command_Op stays OP_READ, so the memory access just repeats.
    tile_write_reg(tile, TILE_CMND, OP_READ);
    step(TILE_CMD_SETTLE);
    return tile_read_reg(tile, TILE_DATA);
}

void mosaic_top_t::rv_set_status(uint32_t tile, uint32_t data)
{
    tile_write_reg(tile, TILE_ADDR, 0x0);
    tile_write_reg(tile, TILE_DATA, data & 0xFF);
    tile_write_reg(tile, TILE_CMND, OP_RISCV);
    step(TILE_CMD_SETTLE);
}

uint32_t mosaic_top_t::rv_get_status(uint32_t tile)
{
    tile_write_reg(tile, TILE_ADDR, 0x0);
    tile_write_reg(tile, TILE_CMND, OP_STATUS);
    step(TILE_CMD_SETTLE);
    return tile_read_reg(tile, TILE_STAT) & 0xFF;
}

void mosaic_top_t::rv_load_firmware(uint32_t tile, const std::string &hexfile)
{
    std::ifstream f(hexfile);
    if (!f.is_open()) {
        fprintf(stderr, "[FW] Error: cannot open firmware file %s\n", hexfile.c_str());
        abort();
    }
    printf("[FW] Loading %s into tile 0x%03X\n", hexfile.c_str(), tile);

    std::string line;
    uint32_t addr = 0;
    size_t words = 0;
    while (std::getline(f, line)) {
        if (line.empty()) { continue; }
        if (line[0] == '@') {
            addr = (uint32_t)strtoul(line.c_str() + 1, nullptr, 16);
        } else {
            uint32_t data = (uint32_t)strtoul(line.c_str(), nullptr, 16);
            indirect_write(tile, addr, data);
            // The hex format is word addressed.
            addr += 1;
            words++;
        }
    }
    // Clear the command (as the reference shell does after loading).
    tile_write_reg(tile, TILE_CMND, 0x0);
    printf("[FW] Loaded %zu words into tile 0x%03X\n", words, tile);
}

void mosaic_top_t::setup_mosaic()
{
    printf("[MOSAIC] Programming MoSAIC tiles\n");

    // 0) Reset the picos (rv_control = 0).
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            rv_set_status(TILE_ADDRESSES[j][i], 0x00);
        }
    }

    // 1) Load firmware into the pico instruction memories.
    rv_load_firmware(TILE_ADDRESSES[0][0], fw_pico0);
    rv_load_firmware(TILE_ADDRESSES[1][1], fw_pico1);

    // 2) Initialize tile coordinates.
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            tile_write_reg(TILE_ADDRESSES[j][i], TILE_COORDS, TILE_COORDINATES[j][i]);
        }
    }

    // 3) Start the picos (rv_control = 1).
    rv_set_status(TILE_ADDRESSES[0][0], 0x01);
    rv_set_status(TILE_ADDRESSES[1][1], 0x01);

    printf("[MOSAIC] Started picos (rv status 0x000 -> 0x%02X, 0x300 -> 0x%02X)\n",
           rv_get_status(TILE_ADDRESSES[0][0]),
           rv_get_status(TILE_ADDRESSES[1][1]));
}

// DOC include start: Loop
int mosaic_top_t::simulation_run()
{
    int exit_code = 0;

    // Let the target settle out of reset before programming it.
    step(256);

    // Program the tiles: reset, load firmware, set coordinates, start.
    setup_mosaic();

    // infinite loop until '+max-cycles' value is reached (within 'systematic_scheduler_t')
    while (!terminated && !finished_scheduled_tasks())
    {
        // step forward maximum amount of allowable cycles
        peek_poke.step(get_largest_stepsize(), false);
        // while the simulation is running N cycles, run all simulation bridges
        while (!peek_poke.is_done() && !terminated)
        {
            for (auto *bridge : registry.get_all_bridges())
            {
                // do bridge work
                bridge->tick();
                // if a bridge has finished then fully exit
                if (bridge->terminate())
                {
                    exit_code = bridge->exit_code();
                    terminated = true;
                    break;
                }
            }
        }
    }

    // Report tile state after processing.
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            uint32_t tile = TILE_ADDRESSES[j][i];
            printf("[MOSAIC] tile 0x%03X: stat=0x%02X rx_packet_count=%u\n",
                   tile,
                   tile_read_reg(tile, TILE_STAT) & 0xFF,
                   tile_read_reg(tile, TILE_RXCOUNT));
        }
    }
    printf("[MOSAIC] instr word 0 of tile 0x000: 0x%08X\n",
           indirect_read(TILE_ADDRESSES[0][0], 0));

    return exit_code;
}
// DOC include end: Loop

// DOC include start: RegisterWithMain
// used in firesim's 'main' to instantiate the custom C++ class you want for a simulation.
// in this case our 'mosaic_top_t'
std::unique_ptr<simulation_t>
create_simulation(simif_t &simif,
                  widget_registry_t &registry,
                  const std::vector<std::string> &args)
{
    return std::make_unique<mosaic_top_t>(simif, registry, args);
}
// DOC include end: RegisterWithMain
