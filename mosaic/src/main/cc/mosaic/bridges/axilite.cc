// See LICENSE for license details.

#include "axilite.h"

char axilite_t::KIND;

axilite_t::axilite_t(simif_t &sim,
                     const AXILITEBRIDGEMODULE_struct &mmio_addrs,
                     unsigned index,
                     const std::vector<std::string> &args)
    : bridge_driver_t(sim, &KIND), mmio_addrs(mmio_addrs) {}

axilite_t::~axilite_t() = default;

bool axilite_t::issue_write(uint32_t addr, uint32_t data) {
  if (in_flight_) {
    return false;
  }
  write(mmio_addrs.in_addr, addr);
  write(mmio_addrs.in_wdata, data);
  // Writing the start register pulses start_write in the widget, which
  // clears the done flag and launches the AXI4-Lite transaction.
  write(mmio_addrs.start_write, 1);
  in_flight_ = true;
  return true;
}

bool axilite_t::issue_read(uint32_t addr) {
  if (in_flight_) {
    return false;
  }
  write(mmio_addrs.in_addr, addr);
  write(mmio_addrs.start_read, 1);
  in_flight_ = true;
  return true;
}

bool axilite_t::done() { return read(mmio_addrs.done) != 0; }

void axilite_t::complete() { in_flight_ = false; }

uint32_t axilite_t::rdata() { return read(mmio_addrs.out_rdata); }

uint32_t axilite_t::resp() { return read(mmio_addrs.out_resp); }
