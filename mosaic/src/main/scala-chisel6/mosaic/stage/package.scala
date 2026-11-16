// See LICENSE

package mosaic

import firrtl.AnnotationSeq
import firrtl.options.OptionsView

package object stage {

  implicit object MoSAICOptionsView extends OptionsView[MoSAICOptions] {

    def view(annotations: AnnotationSeq): MoSAICOptions = annotations
      .collect { case a: MoSAICOption => a }
      .foldLeft(new MoSAICOptions()){ (c, x) =>
        x match {
          case TopModuleAnnotation(a)         => c.copy(topModule = Some(a))
          case OutputBaseNameAnnotation(a)    => c.copy(outputBaseName = Some(a))
          case _: LegacySFCAnnotation         => c.copy(enableSFCFIRRTLEmission = true)
        }
      }

  }

}
