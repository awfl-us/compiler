// build.sbt
ThisBuild / scalaVersion := "3.3.1"

name := "compiler"
organization := "us.awfl"

// Versioning via git tags managed by sbt-dynver
ThisBuild / versionScheme := Some("early-semver")
// If your tags are not prefixed with "v", uncomment:
// ThisBuild / dynverTagPrefix := ""

// Maven Central metadata
ThisBuild / description := "Compile AWFL DSL workflows into YAML and JSON Schemas"
ThisBuild / homepage := Some(url("https://github.com/awfl-us/compiler"))
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/awfl-us/compiler"),
    "scm:git:https://github.com/awfl-us/compiler.git",
    Some("scm:git:ssh://git@github.com/awfl-us/compiler.git")
  )
)
ThisBuild / developers := List(
  Developer(id = "awfl-us", name = "AWFL", email = "opensource@awfl.us", url = url("https://github.com/awfl-us"))
)
ThisBuild / pomIncludeRepository := { _ => false }

ThisBuild / version ~= { v =>
  if (sys.env.get("CI").contains("true")) v
  else "0.1.0-SNAPSHOT"
}

publishMavenStyle := true

// Dependencies
libraryDependencies ++= Seq(
  "us.awfl" %% "dsl" % "0.2.0",
  "us.awfl" %% "compiler-yaml" % "0.2.0",
  "us.awfl" %% "workflow-utils" % "0.2.0",
  "io.circe" %% "circe-core"   % "0.14.7",
  "io.circe" %% "circe-yaml"   % "0.14.2"
)

// dependencyOverrides += "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"
// dependencyOverrides += "us.awfl" %% "compiler-yaml" % "0.1.0-SNAPSHOT"
// dependencyOverrides += "us.awfl" %% "workflow-utils" % "0.1.0-SNAPSHOT"

scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")
