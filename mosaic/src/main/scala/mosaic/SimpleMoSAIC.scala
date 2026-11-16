package firesim.mosaic

import chisel3._

import org.chipsalliance.cde.config.Parameters

import firesim.lib.bridges.{PeekPokeBridge, RationalClockBridge, ResetPulseBridge, ResetPulseBridgeParameters}
import midas.targetutils.GlobalResetCondition

class SimpleMoSAIC extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
  })

  val simpleMoSAIC = Module(new mosaic.MoSAICChisel("mosaic_2x2_firesim"))

  // Disable Control (AXI4Lite) Interface
  simpleMoSAIC.io.control.aw.valid := false.B
  simpleMoSAIC.io.control.aw.bits.addr := 0.U
  simpleMoSAIC.io.control.w.valid := false.B
  simpleMoSAIC.io.control.w.bits.data := 0.U
  simpleMoSAIC.io.control.w.bits.strb := 0.U
  simpleMoSAIC.io.control.b.ready := false.B
  simpleMoSAIC.io.control.ar.valid := false.B
  simpleMoSAIC.io.control.ar.bits.addr := 0.U
  simpleMoSAIC.io.control.r.ready := false.B

  // Disable enableProcessing
  simpleMoSAIC.io.enableProcessing := false.B

  io.done := simpleMoSAIC.io.internalResetDone

  // io.done := 1.U
  
  when(simpleMoSAIC.io.internalResetDone) {
    printf("MoSAIC has reset successfully\n")
  }
}

class SimpleMoSAICHarness(implicit val p: Parameters) extends RawModule {
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
    val smplMoSAIC = Module(new SimpleMoSAIC)

    // Print once when counter 'done' signal asserted.
    val printDone = RegInit(false.B)
    when(smplMoSAIC.io.done && !printDone) {
      printDone := true.B
      printf("SimpleMoSAIC has completed!\n")
    }
  }
}
