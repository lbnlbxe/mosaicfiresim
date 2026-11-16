// See LICENSE for license details.

package firesim.mosaic.bridges

import chisel3._

import firesim.lib.bridgeutils._

/** Elaboration-time parameters for the [[AxiLiteBridge]].
  *
  * @param addrWidth
  *   Width of the AXI4-Lite address channel in bits.
  * @param dataWidth
  *   Width of the AXI4-Lite data channel in bits.
  */
case class AxiLiteKey(addrWidth: Int = 12, dataWidth: Int = 32)

/** The target-side interface of the AxiLiteBridge.
  *
  * Directions are given from the perspective of the bridge, which acts as the
  * AXI4-Lite master (host software is the originator of transactions).
  *
  * All signals are flat (no ready-valid aggregates) so that each is serialized
  * as its own latency-insensitive pipe channel by the MIDAS compiler.
  *
  * @param addrWidth
  *   Width of the AXI4-Lite address channel in bits.
  * @param dataWidth
  *   Width of the AXI4-Lite data channel in bits.
  */
class AxiLiteBridgeTargetIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val clock = Input(Clock())

  // Host-driven (master -> slave) channels
  val awvalid = Output(Bool())
  val awaddr  = Output(UInt(addrWidth.W))
  val wvalid  = Output(Bool())
  val wdata   = Output(UInt(dataWidth.W))
  val wstrb   = Output(UInt((dataWidth / 8).W))
  val bready  = Output(Bool())
  val arvalid = Output(Bool())
  val araddr  = Output(UInt(addrWidth.W))
  val rready  = Output(Bool())

  // Target (slave -> master) responses
  val awready = Input(Bool())
  val wready  = Input(Bool())
  val bvalid  = Input(Bool())
  val bresp   = Input(UInt(2.W))
  val arready = Input(Bool())
  val rvalid  = Input(Bool())
  val rdata   = Input(UInt(dataWidth.W))
  val rresp   = Input(UInt(2.W))
}

/** A host-driven AXI4-Lite master bridge.
  *
  * Presents a single-beat AXI4-Lite master interface to the target. Host
  * software programs transactions into the bridge's control registers (see the
  * `AxiLiteBridgeModule` widget and the `axilite_t` host-side driver).
  *
  * Intended for driving register-mapped control planes (e.g. MoSAIC's tile
  * control registers); it is not a DMA-capable streaming interface.
  */
class AxiLiteBridge(key: AxiLiteKey) extends BlackBox with Bridge[HostPortIO[AxiLiteBridgeTargetIO]] {
  val moduleName     = "midas.widgets.AxiLiteBridgeModule"
  val io             = IO(new AxiLiteBridgeTargetIO(key.addrWidth, key.dataWidth))
  val bridgeIO       = HostPort(io)
  val constructorArg = Some(key)
  generateAnnotations()
}

object AxiLiteBridge {
  /** Instantiate an [[AxiLiteBridge]] and attach it to a target's AXI4-Lite
    * slave (`AXI4LiteTargetIO`) control interface.
    *
    * @param clock
    *   The target clock the control interface is synchronous to.
    * @param control
    *   The target's AXI4-Lite slave interface. On return it is driven by the
    *   bridge and must not be driven elsewhere.
    */
  def apply(clock: Clock, control: mosaic.AXI4LiteTargetIO): AxiLiteBridge = {
    val key    = AxiLiteKey(control.aw.bits.addr.getWidth, control.w.bits.data.getWidth)
    val bridge = Module(new AxiLiteBridge(key))

    bridge.io.clock := clock

    // Host-driven (master -> slave) channels
    control.aw.valid       := bridge.io.awvalid
    control.aw.bits.addr   := bridge.io.awaddr
    control.w.valid        := bridge.io.wvalid
    control.w.bits.data    := bridge.io.wdata
    control.w.bits.strb    := bridge.io.wstrb
    control.b.ready        := bridge.io.bready
    control.ar.valid       := bridge.io.arvalid
    control.ar.bits.addr   := bridge.io.araddr
    control.r.ready        := bridge.io.rready

    // Target (slave -> master) responses
    bridge.io.awready := control.aw.ready
    bridge.io.wready  := control.w.ready
    bridge.io.bvalid  := control.b.valid
    bridge.io.bresp   := control.b.bits.resp
    bridge.io.arready := control.ar.ready
    bridge.io.rvalid  := control.r.valid
    bridge.io.rdata   := control.r.bits.data
    bridge.io.rresp   := control.r.bits.resp

    bridge
  }
}
