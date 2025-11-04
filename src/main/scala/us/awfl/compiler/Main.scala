package us.awfl.compiler

import java.nio.file.{Files, Paths}
import us.awfl.dsl._
import us.awfl.dsl
import us.awfl.compiler.PathsUtil._
import WorkflowLoader._
import YamlGen._
import us.awfl.compiler.TypeInference._
import us.awfl.compiler.workflows.codegen.ApiFacade

// Alias to avoid confusion with dsl.Workflow
import us.awfl.core.{Workflow as CoreWorkflow}

object Main {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      System.err.println(
        "Usage: Main <WorkflowClassName> [OutputDirectory]\n" +
          "Examples:\n" +
          "  Main workflows.codebase.workflows.WorkflowBuilder\n" +
          "  Main WorkflowBuilder   # shorthand (tries workflows.cli.WorkflowBuilder)\n" +
          "\nNote: The workflow must be a Scala object extending core.Workflow[In, Out]."
      )
      sys.exit(1)
    }

    val classNameArg = args(0)
    val outputDir = if (args.length >= 2) Paths.get(args(1)) else Paths.get("yaml_gens")

    val candidates = resolveCandidates(classNameArg)
    val Loaded(fqcn, _cls, module) = loadFirst(candidates)

    println(s"Using defsDir=${defsDir.toAbsolutePath}, typesDir=${typesDir.toAbsolutePath}")

    // Require the new core.Workflow trait with a Scala object module
    if (module == null) {
      System.err.println(s"${fqcn} must be a Scala object extending core.Workflow[In, Out]. Found a plain class.")
      sys.exit(2)
    }
    if (!classOf[CoreWorkflow].isAssignableFrom(module.getClass)) {
      System.err.println(s"${fqcn} does not extend core.Workflow[In, Out]. Please implement the trait.")
      sys.exit(2)
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
