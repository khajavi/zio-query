import BuildHelper._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import zio.sbt.githubactions.Step

name := "zio-query"

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.github.io/zio-query/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "adamgfraser",
        "Adam Fraser",
        "adam.fraser@gmail.com",
        url("https://github.com/adamgfraser")
      )
    ),
    // zio-sbt-ci: this project predates zio-sbt-ecosystem, so `lint`/`prepare` commands aren't
    // available here - the lint step below is overridden to reuse this build's own `check` alias.
    ciEnabledBranches    := Seq("master", "zio1"),
    ciDefaultJavaVersion := "8",
    ciTargetJavaVersions := Seq("8", "11"),
    ciCheckWebsiteBuildProcess := Seq(
      Step.SingleStep(name = "Check Document Generation", run = Some("sbt docs/compileDocs"))
    ),
    // README.md isn't generated from docs/index.md in this repo, and docs are published to npm by
    // the existing site.yml workflow, so zio-sbt-ci shouldn't generate its own competing jobs here.
    ciUpdateReadmeJobs := Seq.empty,
    ciPostReleaseJobs  := Seq.empty
  )
)

// `lint` (from zio-sbt-ecosystem's Commands.scala) isn't on the classpath here; reuse this
// build's own `check` alias instead, keeping the drift-check step zio-sbt-ci prepends untouched.
ThisBuild / ciLintJobs := (ThisBuild / ciLintJobs).value.map { job =>
  job.copy(steps = job.steps.map {
    case s: Step.SingleStep if s.name == "Lint" => s.copy(run = Some("sbt --no-colors check"))
    case other                                  => other
  })
}

ThisBuild / ciTargetScalaVersions := targetScalaVersionsFor(zioQueryJVM, zioQueryJS).value

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

val zioVersion = "2.0.5"

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )
  .aggregate(
    zioQueryJVM,
    zioQueryJS
  )

lazy val zioQuery = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-query"))
  .settings(stdSettings("zio-query"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.query"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-test"     % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .settings(
    scalacOptions ++= {
      if (scalaVersion.value == ScalaDotty)
        Seq.empty
      else
        Seq("-P:silencer:globalFilters=[zio.stacktracer.TracingImplicits.disableAutoTrace]")
    }
  )

lazy val zioQueryJS = zioQuery.js
  .settings(dottySettings)
  .settings(scalaJSUseMainModuleInitializer := true)
  .settings(
    scalacOptions ++= {
      if (scalaVersion.value == ScalaDotty)
        Seq("-scalajs")
      else
        Seq.empty
    }
  )

lazy val zioQueryJVM = zioQuery.jvm
  .settings(dottySettings)

lazy val docs = project
  .in(file("zio-query-docs"))
  .settings(
    publish / skip := true,
    moduleName     := "zio-query-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    ),
    projectName    := "ZIO Query",
    mainModuleName := (zioQueryJVM / moduleName).value,
    projectStage   := ProjectStage.ProductionReady,
    // mdoc (pulled in by zio-sbt-website) needs a newer 2.13 patch than Scala213 above, which is
    // pinned to what the library itself cross-builds against; docs only ever runs on the JVM, so
    // bumping just this project's scalaVersion is safe.
    scalaVersion := "2.13.18"
  )
  .dependsOn(zioQueryJVM)
  .enablePlugins(WebsitePlugin)
