package us.awfl.workflows.codegen

import io.circe.JsonObject

/**
  * Schema derivation has been removed.
  * These stubs deliberately return None so callers fall back to placeholders
  * or container-primitive handling in ApiFacade.
  */
object SchemaDerivation {
  final case class SchemaWithRefs(schema: JsonObject, childFqcns: Set[String])

  // Derivation disabled: no-op stub
  def deriveSchemaForType(typeFqcn: String): Option[JsonObject] = None

  // Derivation with refs disabled: no-op stub
  def deriveSchemaForTypeWithRefs(typeFqcn: String): Option[SchemaWithRefs] = None
}
