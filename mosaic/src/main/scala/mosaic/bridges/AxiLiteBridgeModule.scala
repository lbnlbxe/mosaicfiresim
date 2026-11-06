// See LICENSE for license details.

// NOTE: This file is compiled by FireSim's build (Chisel 3.6), where the
// `midas.widgets` widget machinery is available. It is excluded from this
// repository's own Chisel 6 build (see build.sbt), which only elaborates the
// target-side of bridges. The `make firesim` target symlinks this tree into
// FireSim, which compiles it alongside the rest of the target sources.

package midas.widgets

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridgeutils._
import firesim.mosaic.bridges.{AxiLiteBridgeTargetIO, AxiLiteKey}

/** The host-land half of the [[firesim.mosaic.bridges.AxiLiteBridge]].
  *
  * Implements a single-beat AXI4-Lite master FSM, driven by control registers
  * written by the `axilite_t` host-side driver:
  *
  *   - `in_addr`:     address for the next transaction
  *   - `in_wdata`:    write data for the next transaction
  *   - `start_write`: pulse to run a write transaction
  *   - `start_read`:  pulse to run a read transaction
  *   - `out_rdata`:   data returned by the last read transaction
  *   - `out_resp`:    AXI response code of the last completed transaction
  *   - `done`:        raised when a transaction completes; cleared when the
  *                    next transaction is accepted
  *
  * Only one transaction may be in flight; the host must poll `done` before
  * issuing the next `start_write`/`start_read`.
  */
class AxiLiteBridgeModule(key: AxiLiteKey)(implicit p: Parameters)
    extends BridgeModule[HostPortIO[AxiLiteBridgeTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val io    = IO(new WidgetIO())
    val hPort = IO(HostPort(new AxiLiteBridgeTargetIO(key.addrWidth, key.dataWidth)))

    // Level-style channels: stream the current signal values in both
    // directions every cycle. The FSM itself holds handshakes stable until
    // they fire, so streaming adds no protocol hazards.
    hPort.toHost.hReady   := true.B
    hPort.fromHost.hValid := true.B

    val axi = hPort.hBits

    // Control registers. NB: attach order defines the address map and MUST
    // match the field order of AXILITEBRIDGEMODULE_struct in the C++ driver.
    val inAddr     = genWOReg(Wire(UInt(key.addrWidth.W)), "in_addr")
    val inWData    = genWOReg(Wire(UInt(key.dataWidth.W)), "in_wdata")
    val startWrite = Pulsify(genWORegInit(Wire(Bool()), "start_write", false.B), pulseLength = 1)
    val startRead  = Pulsify(genWORegInit(Wire(Bool()), "start_read", false.B), pulseLength = 1)
    val outRData   = Wire(UInt(key.dataWidth.W))
    genROReg(outRData, "out_rdata")
    val outResp = Wire(UInt(2.W))
    genROReg(outResp, "out_resp")
    val doneReg  = RegInit(false.B)
    val doneWire = Wire(Bool())
    doneWire := doneReg
    genROReg(doneWire, "done")

    // Master outputs, registered and held stable until their handshake fires.
    val awvalid = RegInit(false.B)
    val awaddr  = Reg(UInt(key.addrWidth.W))
    val wvalid  = RegInit(false.B)
    val wdata   = Reg(UInt(key.dataWidth.W))
    val wstrb   = Reg(UInt((key.dataWidth / 8).W))
    val arvalid = RegInit(false.B)
    val araddr  = Reg(UInt(key.addrWidth.W))

    axi.awvalid := awvalid
    axi.awaddr  := awaddr
    axi.wvalid  := wvalid
    axi.wdata   := wdata
    axi.wstrb   := wstrb
    axi.bready  := true.B
    axi.arvalid := arvalid
    axi.araddr  := araddr
    axi.rready  := true.B

    val awFire = awvalid && axi.awready
    val wFire  = wvalid && axi.wready
    val bFire  = axi.bready && axi.bvalid
    val arFire = arvalid && axi.arready
    val rFire  = axi.rready && axi.rvalid

    val sIdle :: sWrite :: sWriteResp :: sRead :: sReadResp :: Nil = Enum(5)
    val state = RegInit(sIdle)

    outRData := 0.U
    outResp  := 0.U

    switch(state) {
      is(sIdle) {
        when(startWrite) {
          awaddr  := inAddr
          awvalid := true.B
          wdata   := inWData
          wstrb   := Fill(key.dataWidth / 8, true.B)
          wvalid  := true.B
          doneReg := false.B
          state   := sWrite
        }.elsewhen(startRead) {
          araddr  := inAddr
          arvalid := true.B
          doneReg := false.B
          state   := sRead
        }
      }
      is(sWrite) {
        // MoSAIC's AXI4-Lite slave (TileRegBlock) samples awvalid & wvalid
        // together and asserts both readies simultaneously.
        when(awFire && wFire) {
          awvalid := false.B
          wvalid  := false.B
          state   := sWriteResp
        }
      }
      is(sWriteResp) {
        when(bFire) {
          outResp := axi.bresp
          doneReg := true.B
          state   := sIdle
        }
      }
      is(sRead) {
        when(arFire) {
          arvalid := false.B
          state   := sReadResp
        }
      }
      is(sReadResp) {
        when(rFire) {
          outRData := axi.rdata
          outResp  := axi.rresp
          doneReg  := true.B
          state    := sIdle
        }
      }
    }

    genCRFile()

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(
        base,
        sb,
        "axilite_t",
        "axilite",
      )
    }
  }
}
