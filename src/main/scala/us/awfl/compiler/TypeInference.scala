package us.awfl.compiler

import java.nio.file.Path
import us.awfl.compiler.PathsUtil._
import scala.util.Try

object TypeInference {
  private def classBaseName(fqcn: String): String = fqcn.split('.').lastOption.getOrElse(fqcn)
  def normalizeFqcn(typeFqcn: String): String = typeFqcn.replace('$', '.')

  // Full-type schema name: drop leading "workflows." then replace '.' with '-'
  def schemaNameFromFqcn(typeFqcn: String): String = {
    val trimmed = if (typeFqcn.startsWith("workflows.")) typeFqcn.stripPrefix("workflows.") else typeFqcn
    trimmed.replace('.', '-')
  }

  // Derive workflow Input/Result type names when they are provided via inherited type members.
  // Specifically handle Agents that extend us.awfl.workflows.traits.ToolWorkflow, which overrides
  // its type members to ToolWorkflow.Input and ToolWorkflow.Result. In such cases we should
  // canonically use the parent trait's type members instead of <Agent>.Input/<Agent>.Result.
  //
  // Note: We return type names as strings for downstream schema naming. Actual schema derivation
  // uses runtime BaseValue samples via ApiFacade.ensureTypesFromBaseValues.
  def inferInOutTypes(fqcn: String): (Option[String], Option[String]) = {
    val toolWorkflowFqcn = "us.awfl.workflows.traits.ToolWorkflow"

    def loadClass(name: String): Option[Class[?]] = Try(Class.forName(name)).toOption

    val moduleClassOpt: Option[Class[?]] =
      // Prefer Scala object module class if present
      loadClass(fqcn + "$").orElse(loadClass(fqcn))

    val toolWorkflowClassOpt: Option[Class[?]] = loadClass(toolWorkflowFqcn)

    val isToolWorkflow: Boolean = (for {
      m  <- moduleClassOpt
      tw <- toolWorkflowClassOpt
    } yield tw.isAssignableFrom(m)).getOrElse(false)

    if (isToolWorkflow) {
      // Minimal validation signal in logs to aid troubleshooting without changing caller flow
      println(s"TypeInference: Detected ToolWorkflow inheritance for $fqcn; using $toolWorkflowFqcn.Input/Result")
      (Some(s"$toolWorkflowFqcn.Input"), Some(s"$toolWorkflowFqcn.Result"))
    } else {
      // Fallback: callers will default to <fqcn>.Input and a known Result type
      (None, None)
    }
  }
}
