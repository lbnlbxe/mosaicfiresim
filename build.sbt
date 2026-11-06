val chisel6Version = "6.6.0"
val chiselTestVersion = "6.0.0"
val scalaVersionFromChisel = "2.13.12"

val chisel3Version = "3.6.1"

lazy val root = Project("mosaicRoot", file("."))

lazy val mosaicDir = file("./mosaic")
lazy val toolsDir = file("./tools")
lazy val firrtlDir = toolsDir / "firrtl2"
lazy val cdeDir = toolsDir / "cde"
lazy val firesimDir = toolsDir / "firesim"

// keep chisel/firrtl specific class files, rename other conflicts
val chiselFirrtlMergeStrategy = CustomMergeStrategy.rename { dep =>
  import sbtassembly.Assembly.{Project, Library}
  val nm = dep match {
    case p: Project => p.name
    case l: Library => l.moduleCoord.name
  }
  if (Seq("firrtl", "chisel3", "chisel").contains(nm.split("_")(0))) { // split by _ to avoid checking on major/minor version
    dep.target
  } else {
    "renamed/" + dep.target
  }
}

lazy val commonSettings = Seq(
  organization := "cag.amcr.lbl.gov",
  version := "0.1.0",
  scalaVersion := scalaVersionFromChisel,
  assembly / test := {},
  assembly / assemblyMergeStrategy := {
    case PathList("chisel3", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    case PathList("chisel", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    case PathList("firrtl", "stage", xs @ _*) => chiselFirrtlMergeStrategy
    case PathList("META-INF", _*) => MergeStrategy.discard
    // should be safe in JDK11: https://stackoverflow.com/questions/54834125/sbt-assembly-deduplicate-module-info-class
    case x if x.endsWith("module-info.class") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Ytasty-reader",
    "-Ymacro-annotations"), // fix hierarchy API
  // unmanagedBase := (root / unmanagedBase).value,
  allDependencies := {
    // drop specific maven dependencies in subprojects in favor of Chipyard's version
    val dropDeps = Seq(("edu.berkeley.cs", "rocketchip"))
    allDependencies.value.filterNot { dep =>
      dropDeps.contains((dep.organization, dep.name))
    }
  },
  libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.3.1",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,

  exportJars := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal))

/**
  * It has been a struggle for us to override settings in subprojects.
  * An example would be adding a dependency to rocketchip on midas's targetutils library,
  * or replacing dsptools's maven dependency on chisel with the local chisel project.
  *
  * This function works around this by specifying the project's root at src/ and overriding
  * scalaSource and resourceDirectory.
  */
def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      Compile / scalaSource := baseDirectory.value / "main" / "scala",
      Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
    )
}

lazy val chisel6Settings = Seq(
  libraryDependencies ++= Seq("org.chipsalliance" %% "chisel" % chisel6Version),
  addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chisel6Version cross CrossVersion.full)
)
lazy val chisel3Settings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chisel3Version),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chisel3Version cross CrossVersion.full)
)

lazy val chiselSettings = chisel6Settings ++ Seq(
  libraryDependencies ++= Seq(
    "org.apache.commons" % "commons-lang3" % "3.12.0",
    "org.apache.commons" % "commons-text" % "1.9"
  )
)

lazy val scalaTestSettings =  Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.+" % "test"
  )
)

lazy val firrtl2 = freshProject("firrtl2", firrtlDir)
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(Antlr4Plugin)
  .settings(commonSettings)
  .settings(
    sourceDirectory := firrtlDir / "src",
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-language:existentials",
      "-language:implicitConversions"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.14" % "test",
      "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0" % "test",
      "com.github.scopt" %% "scopt" % "4.1.0",
      "org.json4s" %% "json4s-native" % "4.1.0-M4",
      "org.apache.commons" % "commons-text" % "1.10.0",
      "com.lihaoyi" %% "os-lib" % "0.8.1",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"),
    Antlr4 / antlr4GenVisitor := true,
    Antlr4 / antlr4GenListener := true,
    Antlr4 / antlr4PackageName := Option("firrtl2.antlr"),
    Antlr4 / antlr4Version := "4.9.3",
    Antlr4 / javaSource := (Compile / sourceManaged).value,
    buildInfoPackage := "firrtl2",
    buildInfoUsePackageAsPath := true,
    buildInfoKeys := Seq[BuildInfoKey](buildInfoPackage, version, scalaVersion, sbtVersion)
  )

lazy val firrtl2_bridge = freshProject("firrtl2_bridge", firrtlDir / "bridge")
  .dependsOn(firrtl2)
  .settings(commonSettings)
  .settings(chiselSettings)

  lazy val cde = (project in cdeDir)
  .settings(commonSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "cde/src/chipsalliance/rocketchip")

// Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS.
// Minimal in scope (should only depend on Chisel/Firrtl that is
// cross-compilable between FireSim Chisel 3.* and MoSAIC Chisel 6+)
lazy val midas = (project in firesimDir / "sim/midas/targetutils")
  .settings(commonSettings)
  .settings(chiselSettings)

// Provides API for bridges to be created in the target.
// Includes target-side of FireSim-provided bridges and their interfaces that are shared
// between FireSim and the target. Minimal in scope (should only depend on Chisel/Firrtl that is
// cross-compilable between FireSim Chisel 3.* and MoSAIC Chisel 6+)
lazy val firesim = (project in firesimDir / "sim/firesim-lib")
  .dependsOn(midas)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(scalaTestSettings)

lazy val mosaic = (project in mosaicDir)
  .settings(name := "mosaic")
  .dependsOn(firrtl2_bridge)
  .dependsOn(cde, midas, firesim)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(
    // AxiLiteBridgeModule is the host-land half of the AxiLiteBridge and
    // depends on FireSim's `midas.widgets` machinery, which is only on the
    // classpath when this tree is symlinked into FireSim (make firesim).
    // Exclude it from this repo's Chisel 6 build.
    Compile / unmanagedSources / excludeFilter :=
      (Compile / unmanagedSources / excludeFilter).value || "AxiLiteBridgeModule.scala"
  )
