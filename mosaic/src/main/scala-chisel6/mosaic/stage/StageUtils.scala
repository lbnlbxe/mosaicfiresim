// See LICENSE

package mosaic.stage

import java.io.{File, FileWriter}

trait HasMoSAICStageUtils {

  def writeOutputFile(targetDir: String, fname: String, contents: String): File = {
    val f = new File(targetDir, fname)
    val fw = new FileWriter(f)
    fw.write(contents)
    fw.close
    f
  }

}
