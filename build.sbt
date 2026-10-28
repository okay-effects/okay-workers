/** okay-workers: Cloudflare Workers for okay-scala programs (specs/workers.md) — package okay.workers */
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / organization := "io.github.okay-effects"
ThisBuild / homepage := Some(url("https://github.com/okay-effects/okay-workers"))
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:imports,privates,locals")
ThisBuild / resolvers += "okay-scala" at "https://okay-effects.github.io/okay-scala/maven"
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / versionScheme := Some("early-semver")
/** RELEASES, ON GITHUB PAGES: `sbt publish` writes both platforms as a Maven repository into `target/pages-maven/`,
 * which goes to the `gh-pages` branch under `maven/` (RELEASING.md) */
val pagesRepository = settingKey[File]("the Maven repository a release is written to, for GitHub Pages")
ThisBuild / pagesRepository := (ThisBuild / baseDirectory).value / "target" / "pages-maven"
ThisBuild / publishTo := Some(MavenCache("pages", (ThisBuild / pagesRepository).value))
ThisBuild / Compile / packageDoc / publishArtifact := false

val okayScala = "0.1.1"

lazy val workers = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("workers"))
  .settings(name := "okay-workers",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-scala" % okayScala,
      "org.scalameta" %%% "munit" % "1.2.3" % Test))
  // the tests' SQLite: a JDBC driver, the user's to choose
  .jvmSettings(libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.1.0" % Test,
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala-jvm",
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "scala-jvm")
  .jsSettings(Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala-js",
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "scala-js")

lazy val root = (project in file(".")).aggregate(workers.jvm, workers.js).settings(publish / skip := true)
