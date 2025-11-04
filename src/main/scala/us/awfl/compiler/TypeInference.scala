package us.awfl.compiler

import java.nio.file.Path
import us.awfl.compiler.PathsUtil._

object TypeInference {
  private def classBaseName(fqcn: String): String = fqcn.split('.').lastOption.getOrElse(fqcn)
  def normalizeFqcn(typeFqcn: String): String = typeFqcn.replace('$', '.')

  // Full-type schema name: drop leading "workflows." then replace '.' with '-'
  def schemaNameFromFqcn(typeFqcn: String): String = {
    val trimmed = if (typeFqcn.startsWith("workflows.")) typeFqcn.stripPrefix("workflows.") else typeFqcn
    trimmed.replace('.', '-')
  }

  // Simplified: string-parsing inference removed. Types are derived at runtime from BaseValue samples.
  // Keep API stable by returning (None, None); callers should use encode-based derivation path.
  def inferInOutTypes(fqcn: String): (Option[String], Option[String]) = (None, None)
}
