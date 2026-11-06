// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package mosaic.stage

import chisel3.experimental.BaseModule
import firrtl.annotations.{Annotation, NoTargetAnnotation}
import firrtl.options.{HasShellOptions, ShellOption, Unserializable}

trait MoSAICOption extends Unserializable { this: Annotation => }

case class TopModuleAnnotation(clazz: Class[_ <: Any]) extends NoTargetAnnotation with MoSAICOption
private[stage] object TopModuleAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[String](
      longOption = "top-module",
      toAnnotationSeq = a => Seq(TopModuleAnnotation(Class.forName(a).asInstanceOf[Class[_ <: BaseModule]])),
      helpText = "<top module>",
      shortOption = Some("T")
    )
  )
}

/** Optional base name for generated files' filenames */
case class OutputBaseNameAnnotation(outputBaseName: String) extends NoTargetAnnotation with MoSAICOption
private[stage] object OutputBaseNameAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[String](
      longOption = "name",
      toAnnotationSeq = a => Seq(OutputBaseNameAnnotation(a)),
      helpText = "<base name of output files>",
      shortOption = Some("n")
    )
  )
}

/** Optionally generate legacy FIRRTL2 SFC FIRRTL generation */
case class LegacySFCAnnotation() extends NoTargetAnnotation with MoSAICOption
private[stage] object LegacySFCAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[Unit](
      longOption = "emit-legacy-sfc",
      toAnnotationSeq = a => Seq(LegacySFCAnnotation()),
      helpText = "Emit a legacy FIRRTL2 SFC FIRRTL file",
      shortOption = Some("els")
    )
  )
}
