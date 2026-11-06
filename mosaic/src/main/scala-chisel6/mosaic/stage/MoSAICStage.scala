// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package mosaic.stage

import circt.stage.{ChiselStage, CIRCTTargetAnnotation, CIRCTTarget}
import firrtl.options.{Shell}
import firrtl.options.Viewer.view
import firrtl.{AnnotationSeq}
import firrtl.options.{Phase, PhaseManager, Shell, Dependency}

final class MoSAICChiselStage extends ChiselStage {
  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    val pm = new PhaseManager(
      targets = Seq(
        Dependency[chisel3.stage.phases.Checks],
        Dependency[chisel3.stage.phases.AddImplicitOutputFile],
        Dependency[chisel3.stage.phases.AddImplicitOutputAnnotationFile],
        Dependency[chisel3.stage.phases.MaybeAspectPhase],
        Dependency[chisel3.stage.phases.AddSerializationAnnotations],
        Dependency[chisel3.stage.phases.Convert],
        Dependency[chisel3.stage.phases.AddDedupGroupAnnotations],
        Dependency[chisel3.stage.phases.MaybeInjectingPhase],
        Dependency[circt.stage.phases.AddImplicitOutputFile],
        Dependency[circt.stage.phases.Checks],
        Dependency[circt.stage.phases.CIRCT]
      ),
      currentState = Seq(
        Dependency[firrtl.stage.phases.AddDefaults],
        Dependency[firrtl.stage.phases.Checks]
      )
    )
    pm.transform(annotations :+ CIRCTTargetAnnotation(CIRCTTarget.CHIRRTL))
  }
}

class MoSAICStage extends ChiselStage {
  override val shell = new Shell("mosaic") with MoSAICCli with circt.stage.CLI {
    // These are added by firrtl.options.Shell (which we must extend because we are a Stage)
    override protected def includeLoggerOptions = false
  }
  override def run(annotations: AnnotationSeq): AnnotationSeq = {
    val enableSFCFIRRTLEmissionPasses = if (view[MoSAICOptions](annotations).enableSFCFIRRTLEmission) {
      Seq(Dependency[mosaic.stage.phases.LegacyFirrtl2Emission])
    } else {
      Seq.empty
    }
    val pm = new PhaseManager(
      targets = Seq(
        Dependency[mosaic.stage.phases.Checks],
        Dependency[mosaic.stage.phases.TransformAnnotations],
        Dependency[mosaic.stage.phases.PreElaboration],
        Dependency[MoSAICChiselStage],
        Dependency[mosaic.stage.phases.GenerateFirrtlAnnos],
      ) ++ enableSFCFIRRTLEmissionPasses,
      currentState = Seq(
        Dependency[firrtl.stage.phases.AddDefaults],
        Dependency[firrtl.stage.phases.Checks]
      )
    )
    pm.transform(annotations)
  }
  override final def invalidates(a: Phase): Boolean = false
}
