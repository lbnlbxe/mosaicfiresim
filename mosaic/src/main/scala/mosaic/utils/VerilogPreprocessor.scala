package mosaic.utils

import scala.io.Source
import scala.util.matching.Regex
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths, Path}

object VerilogPreprocessor {
  
  /**
   * Main preprocessing function that replaces the Makefile and Python script
   */
  def preprocessVerilog(
    outputFile: File,
    allVsrcs: Seq[File],
    preprocessDefines: Seq[String],
    verilatorLintoffDefines: Seq[String],
    buildDir: File
  ): Unit = {
    // Create output directory if it doesn't exist
    outputFile.getParentFile.mkdirs()
    
    // Generate define content
    val defineContent = preprocessDefines.map(d => s"`define $d").mkString("\n") + "\n" +
                        verilatorLintoffDefines.map(d => s"/* verilator lint_off $d */").mkString("\n")
    
    // Generate undefine content
    val undefineContent = preprocessDefines.map(d => s"`undef $d").mkString("\n")
    
    // Read all source files
    val vsrcContent = allVsrcs.map(file => Source.fromFile(file).mkString).mkString("\n")
    
    // Combine all content
    val combinedContent = s"$defineContent\n$vsrcContent\n$undefineContent"
    
    // Process includes from the content
    val processedContent = processIncludesFromString(combinedContent, Seq(buildDir.getPath))
    
    // Write the processed content to the output file
    val writer = new PrintWriter(outputFile)
    try {
      writer.write(processedContent)
    } finally {
      writer.close()
    }
  }
  
  /**
   * Process and replace includes directly from a string, returning the processed content
   *
   * NOTE: every `include site is expanded textually, matching Verilog
   * semantics. Files like is_array.vh contain per-site `assign` statements
   * (no `ifndef guard), so they MUST be expanded at each include site;
   * deduplicating includes leaves all but the first site's signals undriven
   * (Verilator: stuck at 0). Files with `ifndef include guards stay safe
   * under repeated expansion because the guards resolve within the single
   * combined output file.
   */
  def processIncludesFromString(content: String, incDirs: Seq[String]): String = {
    // Split content into lines for processing
    val lines = content.split("\n")
    val result = new StringBuilder()

    processLines(lines, result, incDirs)

    result.toString()
  }
  
  /**
   * Regex for Verilog "`include" directive
   */
  private val includeRegex : Regex = """^\s*`include\s+"(.*)"""".r
    
  private def processLines(
    lines: Array[String],
    result: StringBuilder,
    incDirs: Seq[String]
  ): Unit = {

    // Process each line
    lines.zipWithIndex.foreach { case (line, idx) =>
      includeRegex.findFirstMatchIn(line) match {
        case Some(m) if m.group(1) == "uvm_macros.svh" =>
          // Keep this include directive as is
          result.append(line).append("\n")

        case Some(m) =>
          val includeFile = m.group(1)
          println(s"[INFO] Replacing includes for $includeFile at line ${idx + 1}")

          // Find and process the include file
          findIncludeContent(includeFile, incDirs) match {
            case Some(includeContent) =>
              // Process the include file content recursively
              val includeLines = includeContent.split("\n")
              processLines(includeLines, result, incDirs)

            case None =>
              sys.error(s"[ERROR] Include file $includeFile not found in $incDirs")
          }

        case None =>
          // Copy the line as is
          result.append(line).append("\n")
      }
    }
  }
  
  private def findIncludeContent(fileName: String, incDirs: Seq[String]): Option[String] = {
    // Find the include file in the include directories
    incDirs.map(dir => Paths.get(dir, fileName).toString)
          .find(path => Files.exists(Paths.get(path)))
          .map(path => Source.fromFile(path).mkString)
  }
}