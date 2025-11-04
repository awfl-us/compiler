// build.sbt
ThisBuild / scalaVersion := "3.3.1"

name := "awfl-workflows"
organization := "us.awfl"
version := "0.1.0-SNAPSHOT"

// Circe Core + Generic + Parser + YAML
libraryDependencies ++= Seq(
  "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT",
  "us.awfl" %% "compiler-yaml" % "0.1.0-SNAPSHOT",
  "us.awfl" %% "workflows" % "0.1.0-SNAPSHOT",
  "io.circe" %% "circe-core"   % "0.14.7",
  // "io.circe" %% "circe-generic"% "0.14.7",
  // "io.circe" %% "circe-parser" % "0.14.7",
  "io.circe" %% "circe-yaml"   % "0.14.2",
  // // Jackson Scala module (Scala 3 compatible)
  // "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.17.2",
  // // Victools JSON Schema generator + Jackson module (Java libs)
  // "com.github.victools" %  "jsonschema-generator"       % "4.36.0",
  // "com.github.victools" %  "jsonschema-module-jackson"  % "4.36.0"
)

publishMavenStyle := true
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")
