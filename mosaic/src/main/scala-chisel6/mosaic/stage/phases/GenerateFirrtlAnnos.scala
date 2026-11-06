// See LICENSE

package mosaic.stage.phases

import firrtl.AnnotationSeq
import firrtl.annotations.{JsonProtocol}
import firrtl.options.Viewer.view
import firrtl.options._
import mosaic.stage._

/** Writes FIRRTL annotations into a file */
class GenerateFirrtlAnnos extends Phase with PreservesAll with HasMoSAICStageUtils {

  override val prerequisites = Seq(Dependency[mosaic.stage.MoSAICChiselStage])

  override def transform(annotations: AnnotationSeq): AnnotationSeq = {
    val targetDir = view[StageOptions](annotations).targetDir
    val fileName = s"${view[MoSAICOptions](annotations).longName.get}.anno.json"

    val annos = annotations.view.flatMap {
      // Remove TargetDirAnnotation so that we can pass as argument to FIRRTL
      // Remove CustomFileEmission, those are serialized automatically by Stages
      case (_: Unserializable | _: TargetDirAnnotation | _: CustomFileEmission) =>
        None
      case a =>
        Some(a)
    }

    writeOutputFile(targetDir, fileName, JsonProtocol.serialize(annos.toSeq))

    annotations
  }

}
