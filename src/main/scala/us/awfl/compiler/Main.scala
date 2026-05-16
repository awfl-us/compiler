package us.awfl.compiler

import java.nio.file.{Files, Paths}
import us.awfl.dsl._
import us.awfl.dsl
import us.awfl.compiler.PathsUtil._
import WorkflowLoader._
import YamlGen._
import us.awfl.compiler.TypeInference._
import us.awfl.compiler.workflows.codegen.ApiFacade
import scala.util.control.NoStackTrace

// Alias to avoid confusion with dsl.Workflow
import us.awfl.core.{Workflow as CoreWorkflow}

object Main {
  // Lightweight exit signal we can catch to control the final status code without relying on sys.exit
  private final case class Exit(code: Int, message: String = "")
      extends RuntimeException(message)
      with NoStackTrace

  private def die(code: Int, msg: String): Nothing = throw Exit(code, msg)

  def main(args: Array[String]): Unit = {
    // Capture all throwables so we can deterministically signal failure to sbt and CI
    val exitCode: Int = try {
      run(args)
      0
    } catch {
      case Exit(code, msg) =>
        if (msg != null && msg.nonEmpty) System.err.println(msg)
        code
      case t: Throwable =>
        System.err.println("Fatal error while running awfl compiler:")
        t.printStackTrace(System.err)
        1
    }

    // Ensure the process (or sbt's trapped runner) observes a non-zero status on failure.
    try {
      java.lang.System.exit(exitCode)
    } catch {
      // In non-forked sbt runs, System.exit is trapped. Re-throw to surface a failure when non-zero.
      case _: SecurityException if exitCode != 0 =>
        throw new RuntimeException(s"Exiting with non-zero status: $exitCode")
      case _: SecurityException => ()
    }
  }

  private def run(args: Array[String]): Unit = {
    if (args.length < 1) {
      die(
        1,
        "Usage: Main <WorkflowClassName> [OutputDirectory]\n" +
          "Examples:\n" +
          "  Main workflows.codebase.workflows.WorkflowBuilder\n" +
          "  Main WorkflowBuilder   # shorthand (tries workflows.cli.WorkflowBuilder)\n" +
          "\nNote: The workflow must be a Scala object extending core.Workflow[In, Out]."
      )
    }

    val classNameArg = args(0)
    val outputDir = if (args.length >= 2) Paths.get(args(1)) else Paths.get("yaml_gens")

    val candidates = resolveCandidates(classNameArg)
    val Loaded(fqcn, _cls, module) = loadFirst(candidates)

    println(s"Using defsDir=${defsDir.toAbsolutePath}, typesDir=${typesDir.toAbsolutePath}")

    // Require the new core.Workflow trait with a Scala object module
    if (module == null) {
      die(2, s"${fqcn} must be a Scala object extending core.Workflow[In, Out]. Found a plain class.")
    }
    if (!classOf[CoreWorkflow].isAssignableFrom(module.getClass)) {
      die(2, s"${fqcn} does not extend core.Workflow[In, Out]. Please implement the trait.")
    }

    val wfTrait = module.asInstanceOf[CoreWorkflow]
    val workflows = wfTrait.workflows.asInstanceOf[List[dsl.Workflow[_]]]
    val inputValue = wfTrait.inputVal.asInstanceOf[BaseValue[_]]

    writeWorkflowsYaml(workflows, outputDir, wfTrait.workflowName)

    val defName = classNameArg
    val (inputTypeOpt0, outputTypeOpt0): (Option[String], Option[String]) = inferInOutTypes(fqcn)

    val inputTypeOpt = inputTypeOpt0.map(normalizeFqcn).orElse(Some(s"${fqcn}.Input"))
    val outputTypeOpt = outputTypeOpt0.map(normalizeFqcn).orElse(Some("services.Llm.ChatToolResponse"))

    val inSchemaName: String = inputTypeOpt.map(schemaNameFromFqcn).getOrElse(s"${fqcn.split('.').last}_input")
    val outSchemaName: String = outputTypeOpt.map(schemaNameFromFqcn).getOrElse(s"${fqcn.split('.').last}_output")

    println(s"Inferred types: inputType=${inputTypeOpt.getOrElse("<none>")}, outputType=${outputTypeOpt.getOrElse("<none>")}")
    println(s"Schema names: input=$inSchemaName, output=$outSchemaName")

    val outputValueOpt: Option[BaseValue[_]] = workflows.headOption.map(_.steps._2.asInstanceOf[BaseValue[_]])

    // Ensure schemas via ApiFacade using encode()-registered samples when available; placeholder/container fallback inside
    ApiFacade.ensureTypesFromBaseValues(
      typesDir,
      inSchemaName,
      outSchemaName,
      inputTypeOpt,
      outputTypeOpt,
      Some(inputValue),
      outputValueOpt
    )

    ApiFacade.writeWorkflowDef(
      defsDir,
      typesDir,
      defName,
      inputTypeOpt,
      outputTypeOpt,
      inSchemaName,
      outSchemaName
    )
  }
}
