package mosaic

import sys.process._
import scala.io.Source
import java.io.File
import java.nio.file.{Files, Paths, StandardCopyOption}

import chisel3._
import chisel3.util.HasBlackBoxPath
import chisel3.experimental._ // To enable experimental features

class mosaic(
  bw:           Int        = 32,
  ddr4Config:   DDR4Config = new DDR4Config(),
  mosaicConfig: String     = "mosaic_2x2_firesim")
    extends BlackBox(
      Map(
        "BW" -> IntParam(bw)
      )
    )
    with HasBlackBoxPath {
  val ddr4Ctrl: Boolean = mosaicConfig.contains("ddr")
  val bwb:      Int     = bw / 8

  val io = IO(
    new SVMoSAICIO(
      ddr4Ctrl   = ddr4Ctrl,
      bw         = bw,
      ddr4Config = ddr4Config
    )
  )

  // Locate the repository's `mosaic` directory. This BlackBox elaborates both
  // from the repository root (the repo's own sbt build, where user.dir is the
  // repo root) and from FireSim's build (where user.dir is
  // <repo>/tools/firesim/sim and this scala tree is symlinked in), so search
  // upwards for the marker directory instead of assuming user.dir.
  private val mosaicChiselDir: String = {
    val marker   = Paths.get("mosaic/src/main/resources/mosaic/perl")
    var candidate = Paths.get(System.getProperty("user.dir")).toAbsolutePath
    var found: java.nio.file.Path = null
    while (found == null && candidate != null) {
      if (Files.exists(candidate.resolve(marker))) {
        found = candidate
      } else {
        candidate = candidate.getParent
      }
    }
    require(
      found != null,
      s"Could not locate the mosaic source directory (searched upwards from ${System.getProperty("user.dir")})"
    )
    found.toString + "/mosaic"
  }
  val mosaicVsrcDir   = s"${mosaicChiselDir}/src/main/resources/vsrc"
  val mosaicPerlDir   = s"${mosaicChiselDir}/src/main/resources/mosaic/perl"
  val mosaicGitDir    = s"${mosaicChiselDir}/src/main/resources/mosaic/MoSAIC-P38"
  val mosaicBuildDir  = s"${mosaicGitDir}/build"
  val mosaicPerlRunDir = s"${mosaicGitDir}/tools/generate"
  val mosaicFileList  = s"${mosaicGitDir}/icarus/file_list.txt"
  val mosaicPreProcOutputFile = s"${mosaicVsrcDir}/mosaic.preprocessed.sv"

  // Source and destination Perl paths
  val sourceMoSAICPerlScript = Paths.get(mosaicPerlDir + "/" + mosaicConfig + ".pl")
  val destMoSAICPerlDir = Paths.get(mosaicPerlRunDir)
  val destMoSAICPerlScript = Paths.get(mosaicPerlRunDir + "/" + mosaicConfig + ".pl")
  
  // Check if source script exists
  if (!Files.exists(sourceMoSAICPerlScript)) {
    println(s"Error: Source script not found at ${sourceMoSAICPerlScript.toAbsolutePath}")
    System.exit(1)
  }

  // Create destination directory if it doesn't exist
  Files.createDirectories(Paths.get(mosaicVsrcDir))

  // Copy the script file
  Files.copy(sourceMoSAICPerlScript, destMoSAICPerlScript, StandardCopyOption.REPLACE_EXISTING)
  println(s"[INFO] Copied ${mosaicConfig}.pl to ${destMoSAICPerlScript.toAbsolutePath}")
  
  // Set execute permissions
  destMoSAICPerlScript.toFile.setExecutable(true)

  val perlBuild = Process(
    Seq(
      "perl",
      s"-I${mosaicPerlRunDir}",
      s"./${mosaicConfig}.pl"
    ),
    destMoSAICPerlDir.toFile)
  require(perlBuild.! == 0, "Failed to run MoSAIC Perl Build step")

  // Delete the script after execution
  if (Files.deleteIfExists(destMoSAICPerlScript)) {
    println(s"[INFO] Deleted temporary script: ${destMoSAICPerlScript.toAbsolutePath}")
  } else {
    println(s"[WARN] Could not delete script at ${destMoSAICPerlScript.toAbsolutePath}")
  }

  val fileList = Source
    .fromFile(mosaicFileList)
    .getLines()
    .toList
    .filter(_.nonEmpty)
    .filterNot(_.startsWith("#"))
    .filterNot(_.contains("Testbench"))
    .map(_.replaceAll("^\\.{2}(.*)$","$1"))
    .map(mosaicGitDir +  _)
    .map(new File(_))

  // pre-process the verilog to remove "includes" and combine into one file
  utils.VerilogPreprocessor.preprocessVerilog(
    outputFile = new File(mosaicPreProcOutputFile),
    allVsrcs = fileList,
    preprocessDefines = Seq(
      "MOSAIC_FIRESIM"
      ),
    verilatorLintoffDefines = Seq(
      "UNUSEDPARAM",
      "PINCONNECTEMPTY",
      "GENUNNAMED",
      "IMPLICIT", 
      "WIDTHEXPAND",
      "WIDTHTRUNC",
      "UNSIGNED",
      "CASEINCOMPLETE",
      "BLKSEQ",
      "SYNCASYNCNET"
    ),
    buildDir = new File(mosaicBuildDir)
  )

  // add wrapper/blackbox after it is pre-processed
  addPath(mosaicPreProcOutputFile)
}
