// See LICENSE

package mosaic.stage.phases

import chisel3.RawModule
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq
import firrtl.options.Viewer.view
import firrtl.options.{Dependency, Phase, StageOptions}
import mosaic.stage._

/** Constructs a generator function that returns a top module with given config parameters */
class PreElaboration extends Phase with PreservesAll with HasMoSAICStageUtils {

  override val prerequisites = Seq(Dependency[Checks])
  override val optionalPrerequisiteOf = Seq(Dependency[chisel3.stage.phases.Elaborate])

  override def transform(annotations: AnnotationSeq): AnnotationSeq = {

    val stageOpts = view[StageOptions](annotations)
    val rOpts = view[MoSAICOptions](annotations)
    val topMod = rOpts.topModule.get

    // Prefer a no-arg constructor; fall back to the (Parameters) constructor
    // used by harnesses (e.g. FireSim-style `implicit val p: Parameters`)
    // with empty parameters.
    val gen = () =>
      (
        try {
          topMod.getConstructor().newInstance()
        } catch {
          case _: NoSuchMethodException =>
            topMod
              .getConstructor(classOf[org.chipsalliance.cde.config.Parameters])
              .newInstance(org.chipsalliance.cde.config.Parameters.empty)
        }
      ) match {
        case a: RawModule => a
      }

    ChiselGeneratorAnnotation(gen) +: annotations
  }

}
