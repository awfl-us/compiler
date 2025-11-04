package us.awfl.compiler

import io.circe.syntax._
import io.circe.yaml._
import io.circe.yaml.syntax._
import io.circe.generic.auto._
import io.circe.{Json, JsonObject}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import us.awfl.dsl._

object YamlGen {
  private val printer = Printer.spaces2.copy(
    dropNullKeys = true,
    preserveOrder = true,
    splitLines = false,
    stringStyle = Printer.StringStyle.DoubleQuoted
  )

  def writeWorkflowsYaml(workflows: List[Workflow[_]], outputDir: Path, baseName: String): List[Path] = {
    if (!Files.exists(outputDir)) Files.createDirectories(outputDir)
    workflows.filter(_.steps._1.nonEmpty).zipWithIndex.map { case (workflow, index) =>
      val yamlWorkflow = us.awfl.yaml.compile(workflow)
      val yamlString = printer.pretty(yamlWorkflow.asJson)

      val name = s"$baseName${workflow.name.map(n => s"-$n").getOrElse("")}"
      val outputPath = outputDir.resolve(s"$name.yaml")
      Files.writeString(outputPath, yamlString, StandardCharsets.UTF_8)
      println(s"Generated: ${outputPath.toAbsolutePath}")
      outputPath
    }
  }
}
