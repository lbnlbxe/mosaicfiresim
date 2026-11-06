#ifndef __AXILITE_H
#define __AXILITE_H
// See LICENSE for license details.

#include <cstdint>
#include <vector>

#include "core/bridge_driver.h"

/**
 * MMIO register addresses of the AxiLiteBridgeModule widget.
 *
 * The field order MUST match the control-register attach order in
 * `midas.widgets.AxiLiteBridgeModule` (the generated header contains
 * static_asserts checking the offsets against this struct).
 */
struct AXILITEBRIDGEMODULE_struct {
  uint64_t in_addr;
  uint64_t in_wdata;
  uint64_t start_write;
  uint64_t start_read;
  uint64_t out_rdata;
  uint64_t out_resp;
  uint64_t done;
};

/**
 * @brief AxiLite Bridge Driver class
 *
 * Host-side half of the AxiLiteBridge: issues AXI4-Lite transactions to the
 * target's control plane (e.g. MoSAIC's tile control registers) by
 * programming the widget's control registers over MMIO.
 *
 * Only one transaction may be in flight: issue a transaction, advance the
 * target (e.g. with peek_poke.step) until done() returns true, then consume
 * the result before issuing the next one.
 */
class axilite_t : public bridge_driver_t {
public:
  /// The identifier for the bridge type used for casts.
  static char KIND;

  axilite_t(simif_t &sim,
            const AXILITEBRIDGEMODULE_struct &mmio_addrs,
            unsigned index,
            const std::vector<std::string> &args);
  ~axilite_t() override;

  /**
   * Issue a write transaction to `addr` (non-blocking).
   *
   * @return false if a transaction is still in flight (the request was
   *         dropped; the caller must wait for done() and retry).
   */
  bool issue_write(uint32_t addr, uint32_t data);

  /**
   * Issue a read transaction from `addr` (non-blocking).
   *
   * @return false if a transaction is still in flight.
   */
  bool issue_read(uint32_t addr);

  /// True when the last issued transaction has completed.
  bool done();

  /// Data of the last completed read transaction. Valid once done().
  uint32_t rdata();

  /// AXI response code (2 bits) of the last completed transaction.
  uint32_t resp();

  /// True while a transaction is outstanding (host-side bookkeeping).
  bool in_flight() const { return in_flight_; }

private:
  const AXILITEBRIDGEMODULE_struct mmio_addrs;
  bool in_flight_ = false;
};

#endif // __AXILITE_H
