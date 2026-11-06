// See LICENSE

package mosaic.stage.phases

import firrtl.AnnotationSeq
import firrtl.annotations.Annotation
import firrtl.options.{OptionsException, Phase, TargetDirAnnotation}
import mosaic.stage._

import scala.collection.mutable

/** Checks for the correct type and number of command line arguments */
class Checks extends Phase with PreservesAll {

  override def transform(annotations: AnnotationSeq): AnnotationSeq = {
    val targetDir, topModule, outputBaseName = mutable.ListBuffer[Annotation]()

    annotations.foreach {
      case a: TargetDirAnnotation      => a +=: targetDir
      case a: TopModuleAnnotation      => a +=: topModule
      case a: OutputBaseNameAnnotation => a +=: outputBaseName
      case _ =>
    }

    def required(annoList: mutable.ListBuffer[Annotation], option: String): Unit = {
      if (annoList.size != 1) {
        throw new OptionsException(s"Exactly one $option required")
      }
    }

    def optional(annoList: mutable.ListBuffer[Annotation], option: String): Unit = {
      if (annoList.size > 1) {
        throw new OptionsException(s"Too many $option options have been specified")
      }
    }

    required(targetDir, "target directory")
    required(topModule, "top module")

    optional(outputBaseName, "output base name")

    annotations
  }

}
