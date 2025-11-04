package us.awfl.compiler

import io.circe.JsonObject
import java.nio.file.Path
import us.awfl.compiler.workflows.codegen.ApiFacade
import us.awfl.dsl.BaseValue

// Thin compatibility shim delegating to workflows.codegen.ApiFacade
// Keep signatures stable to avoid breaking existing references.
object TypesAndDefs {
  def deriveSchemaForType(typeFqcn: String): Option[JsonObject] =
    ApiFacade.deriveSchemaForType(typeFqcn)

  def ensureTypesWithRealSchema(
    typesDir: Path,
    inSchemaName: String,
    outSchemaName: String,
    inputTypeOpt: Option[String],
    outputTypeOpt: Option[String]
  ): Unit =
    ApiFacade.ensureTypesWithRealSchema(
      typesDir,
      inSchemaName,
      outSchemaName,
      inputTypeOpt,
      outputTypeOpt
    )

  // Preferred path when you have real workflow values at hand: register schema samples from SchemaDeriver.encode
  def ensureTypesFromBaseValues(
    typesDir: Path,
    inSchemaName: String,
    outSchemaName: String,
    inputTypeOpt: Option[String],
    outputTypeOpt: Option[String],
    inputValueOpt: Option[BaseValue[_]],
    outputValueOpt: Option[BaseValue[_]]
  ): Unit =
    ApiFacade.ensureTypesFromBaseValues(
      typesDir,
      inSchemaName,
      outSchemaName,
      inputTypeOpt,
      outputTypeOpt,
      inputValueOpt,
      outputValueOpt
    )

  def writeWorkflowDef(
    defsDir: Path,
    typesDir: Path,
    defName: String,
    inputTypeOpt: Option[String],
    outputTypeOpt: Option[String],
    inSchemaName: String,
    outSchemaName: String
  ): Unit =
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
