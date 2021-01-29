import com.softwaremill.SbtSoftwareMillBrowserTestJS._

val scala2_11 = "2.11.12"
val scala2_12 = "2.12.12"
val scala2_13 = "2.13.4"
val scala2 = List(scala2_11, scala2_12, scala2_13)
val scala3_M3 = "3.0.0-M3"
val scala3 = List(scala3_M3)

val sttpModelVersion = "1.3.1"

val scalaTestVersion = "3.2.4-M1"
val zioVersion = "1.0.3"
val fs2Version: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "2.1.0"
  case _             => "2.5.0"
}

excludeLintKeys in Global ++= Set(ideSkipProject)

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.shared",
  // doc generation is broken in dotty
  sources in (Compile, doc) := {
    val scalaV = scalaVersion.value
    val current = (sources in (Compile, doc)).value
    if (scala3.contains(scalaV)) Seq() else current
  }
)

val commonJvmSettings = commonSettings ++ Seq(
  scalacOptions ++= Seq("-target:jvm-1.8"),
  ideSkipProject := (scalaVersion.value != scala2_13),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  )
)

val commonJsSettings = commonSettings ++ Seq(
  ideSkipProject := true,
  scalacOptions in Compile ++= {
    if (isSnapshot.value) Seq.empty
    else
      Seq {
        val dir = project.base.toURI.toString.replaceFirst("[^/]+/?$", "")
        val url = "https://raw.githubusercontent.com/softwaremill/sttp-shared"
        s"-P:scalajs:mapSourceURI:$dir->$url/v${version.value}/"
      }
  },
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "1.1.0",
    "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
  )
)

val commonNativeSettings = commonSettings ++ Seq(
  ideSkipProject := true,
  libraryDependencies ++= Seq(
    "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
  )
)

lazy val projectAggregates: Seq[ProjectReference] = if (sys.env.isDefinedAt("STTP_NATIVE")) {
  println("[info] STTP_NATIVE defined, including sttp-native in the aggregate projects")
  core.projectRefs ++ ws.projectRefs ++ akka.projectRefs ++ fs2.projectRefs ++ monix.projectRefs ++ zio.projectRefs
} else {
  println("[info] STTP_NATIVE *not* defined, *not* including sttp-native in the aggregate projects")
  scala2.flatMap(v => List[ProjectReference](core.js(v), ws.js(v))) ++
    scala2.flatMap(v => List[ProjectReference](core.jvm(v), ws.jvm(v), fs2.jvm(v), monix.jvm(v))) ++
    scala3.flatMap(v => List[ProjectReference](core.jvm(v), ws.jvm(v))) ++ // TODO , zio.jvm(v)
    List[ProjectReference](
      akka.jvm(scala2_12),
      akka.jvm(scala2_13),
      monix.js(scala2_12),
      monix.js(scala2_13)
    )
}

val compileAndTest = "compile->compile;test->test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttp-shared")
  .aggregate(projectAggregates: _*)

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = scala2,
    settings = commonJsSettings ++ browserChromeTestSettings
  )
  .nativePlatform(
    scalaVersions = scala2,
    settings = commonNativeSettings
  )

lazy val ws = (projectMatrix in file("ws"))
  .settings(
    name := "ws",
    libraryDependencies += "com.softwaremill.sttp.model" %%% "core" % sttpModelVersion
  )
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = scala2,
    settings = commonJsSettings ++ browserChromeTestSettings
  )
  .nativePlatform(
    scalaVersions = scala2,
    settings = commonNativeSettings
  )
  .dependsOn(core)

lazy val akka = (projectMatrix in file("akka"))
  .settings(
    name := "akka"
  )
  .jvmPlatform(
    scalaVersions = List(scala2_12, scala2_13),
    settings = commonJvmSettings ++ Seq(
      libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.11" % "provided"
    )
  )
  .dependsOn(core)

lazy val fs2 = (projectMatrix in file("fs2"))
  .settings(
    name := "fs2",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "co.fs2" %% "fs2-io" % fs2Version(_)
    )
  )
  .jvmPlatform(
    scalaVersions = scala2,
    settings = commonJvmSettings
  )
  .dependsOn(core)

lazy val monix = (projectMatrix in file("monix"))
  .settings(
    name := "monix",
    libraryDependencies += "io.monix" %%% "monix" % "3.3.0"
  )
  .jvmPlatform(
    scalaVersions = scala2,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = List(scala2_12, scala2_13),
    settings = commonJsSettings ++ browserChromeTestSettings
  )
  .dependsOn(core)

lazy val zio = (projectMatrix in file("zio"))
  .settings(
    name := "zio",
    libraryDependencies ++= Seq("dev.zio" %% "zio-streams" % zioVersion, "dev.zio" %% "zio" % zioVersion)
  )
  .jvmPlatform(
    scalaVersions = scala2,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = List(scala2_12, scala2_13),
    settings = commonJsSettings ++ browserChromeTestSettings
  )
  .dependsOn(core)
