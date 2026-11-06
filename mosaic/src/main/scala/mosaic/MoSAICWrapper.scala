// See LICENSE for license details.

package firesim.mosaic

import chisel3._

import firesim.lib.bridges.{PeekPokeBridge, RationalClockBridge, ResetPulseBridge, ResetPulseBridgeParameters}
import firesim.mosaic.bridges.AxiLiteBridge
import midas.targetutils.GlobalResetCondition

/** A MoSAIC wrapper with its AXI4-Lite control interface attached to the
  * host-driven [[AxiLiteBridge]], so that firmware running in the FireSim
  * driver can program the design (tile coordinates, pico programs, etc.).
  *
  * Unlike [[SimpleMoSAIC]] (a reset-only smoke test with all functional
  * interfaces tied off), this wrapper leaves the control interface live.
  */
class MoSAICWrapper extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
  })

  val mosaicInst = Module(new mosaic.MoSAICChisel("mosaic_2x2_firesim"))

  // Control (AXI4Lite) Interface: driven by the host via the AxiLiteBridge
  val axiControlBridge = AxiLiteBridge(clock, mosaicInst.io.control)

  // enableProcessing is vestigial: MoSAIC ties it to 1 internally
  // (S_CONTROLLER_USS.sv) and leaves the top-level port unconnected.
  mosaicInst.io.enableProcessing := false.B

  io.done := mosaicInst.io.internalResetDone

  when(mosaicInst.io.internalResetDone) {
    printf("MoSAIC has reset successfully\n")
  }
}

/** Harness that drives the [[MoSAICWrapper]] with FireSim-provided clock and
  * reset. Mirrors [[SimpleMoSAICHarness]].
  */
class MoSAICWrapperHarness(implicit val p: org.chipsalliance.cde.config.Parameters) extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())

  // Boilerplate code:
  // The peek-poke bridge must still be instantiated even though it's
  // functionally unused. This will be removed in a future PR.
  val dummy          = WireInit(false.B)
  val peekPokeBridge = PeekPokeBridge(clock, dummy)

  // Drive with default clock provided by a bridge.
  clock := RationalClockBridge().io.clocks.head

  // Drive reset with a bridge.
  val resetBridge = Module(new ResetPulseBridge(ResetPulseBridgeParameters()))
  // In effect, the bridge counts the length of the reset in terms of this clock.
  resetBridge.io.clock := clock
  // Drive with pulsed reset for a default amount of time.
  reset                := resetBridge.io.reset

  // Boilerplate code:
  // Ensures FireSim-synthesized assertions and instrumentation is disabled
  // while 'resetBridge.io.reset' is asserted.  This ensures assertions do not fire at
  // time zero in the event their local reset is delayed (typically because it
  // has been pipelined).
  GlobalResetCondition(resetBridge.io.reset)

  // Custom logic.
  withClockAndReset(clock, reset) {
    val mosaicWrapper = Module(new MoSAICWrapper)

    // Print once when counter 'done' signal asserted.
    val printDone = RegInit(false.B)
    when(mosaicWrapper.io.done && !printDone) {
      printDone := true.B
      printf("MoSAICWrapper has completed!\n")
    }
  }
}
