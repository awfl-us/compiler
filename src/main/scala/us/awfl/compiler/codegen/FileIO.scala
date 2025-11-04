package us.awfl.compiler.workflows.codegen

import io.circe.{Json, JsonObject}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets

object FileIO {
  def ensureDir(p: Path): Unit = {
    if (!Files.exists(p)) Files.createDirectories(p)
  }

  def isAutoPlaceholder(obj: JsonObject): Boolean = {
    val tpe = obj.apply("type").flatMap(_.asString)
    val props = obj.apply("properties").flatMap(_.asObject)
    val addl = obj.apply("additionalProperties").flatMap(_.asBoolean)
    tpe.contains("object") && props.isEmpty && addl.contains(true)
  }

  private def withId(name: String, schemaObj: JsonObject): JsonObject = {
    if (schemaObj.apply("$id").isEmpty)
      schemaObj.add("$id", Json.fromString(s"https://topaigents.dev/workflows/types/${name}.json"))
    else schemaObj
  }

  def writeTypeFile(typesDir: Path, name: String, title: String, schemaObj: JsonObject, description: String): Unit = {
    ensureDir(typesDir)
    val schemaWithId = withId(name, schemaObj)
    val json = Json.obj(
      ("name", Json.fromString(name)),
      ("description", Json.fromString(description)),
      ("schema", Json.fromJsonObject(schemaWithId))
    )
    val p = typesDir.resolve(name + ".json")
    Files.writeString(p, json.spaces2, StandardCharsets.UTF_8)
    println(s"Wrote types/${name}.json with derived schema at ${p.toAbsolutePath}")
  }

  def writePlaceholder(typesDir: Path, name: String, title: String, typeFqcn: String): Unit = {
    val schema = JsonObject(
      "$schema" -> Json.fromString("https://json-schema.org/draft/2020-12/schema"),
      "$id" -> Json.fromString(s"https://topaigents.dev/workflows/types/${name}.json"),
      "title" -> Json.fromString(title),
      "description" -> Json.fromString(s"Placeholder schema for ${typeFqcn}; replace with a concrete JSON Schema."),
      "type" -> Json.fromString("object"),
      "additionalProperties" -> Json.fromBoolean(true)
    )
    val desc = s"Auto-generated placeholder schema for ${typeFqcn} (replace with a concrete schema)."
    writeTypeFile(typesDir, name, title, schema, desc)
  }

  def writeWorkflowDef(
    defsDir: Path,
    typesDir: Path,
    defName: String,
    inputTypeOpt: Option[String],
    outputTypeOpt: Option[String],
    inSchemaName: String,
    outSchemaName: String
  ): Unit = {
    ensureDir(defsDir)
    val base = List(
      ("$schema", Json.fromString("https://json-schema.org/draft/2020-12/schema")),
      ("$id", Json.fromString(s"https://topaigents.dev/workflows/defs/${defName}.json")),
      ("name", Json.fromString(defName)),
      ("description", Json.fromString("Auto-generated workflow definition.")),
      ("inputSchema", Json.fromString(inSchemaName)),
      ("outputSchema", Json.fromString(outSchemaName))
    ) ++ inputTypeOpt.map(t => ("inputType", Json.fromString(t))) ++ outputTypeOpt.map(t => ("outputType", Json.fromString(t)))

    val json = Json.obj(base: _*)
    val p = defsDir.resolve(defName + ".json")
    Files.writeString(p, json.spaces2, StandardCharsets.UTF_8)
    println(s"Wrote defs/${defName}.json at ${p.toAbsolutePath}")
  }
}
