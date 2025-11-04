package us.awfl.workflows.codegen

import io.circe.{Json, JsonObject, JsonNumber}

object ExampleSchema {
  def fromJson(json: Json): JsonObject = json.fold(
    jsonNull = JsonObject("type" -> Json.fromString("null")),
    jsonBoolean = b => JsonObject("type" -> Json.fromString("boolean")),
    jsonNumber = n => numberSchema(n),
    jsonString = s => JsonObject("type" -> Json.fromString("string")),
    jsonArray = arr => arraySchema(arr.toList),
    jsonObject = obj => objectSchema(obj)
  )

  private def numberSchema(n: JsonNumber): JsonObject =
    if (n.toLong.isDefined) JsonObject("type" -> Json.fromString("integer"))
    else JsonObject("type" -> Json.fromString("number"))

  private def arraySchema(items: List[Json]): JsonObject = {
    val itemSchemas = items.map(fromJson)
    val uniqueSchemas = dedupSchemas(itemSchemas)

    val itemsJson: Json =
      if (uniqueSchemas.isEmpty) Json.obj() // unconstrained items
      else if (uniqueSchemas.size == 1) Json.fromJsonObject(uniqueSchemas.head)
      else Json.obj(
        "anyOf" -> Json.arr(uniqueSchemas.map(s => Json.fromJsonObject(s)): _*)
      )

    JsonObject(
      "type" -> Json.fromString("array"),
      "items" -> itemsJson
    )
  }

  private def objectSchema(obj: JsonObject): JsonObject = {
    val props: Iterable[(String, Json)] = obj.toMap.map { case (k, v) =>
      k -> Json.fromJsonObject(fromJson(v))
    }
    val required = Json.arr(obj.keys.map(Json.fromString).toList: _*)

    JsonObject(
      "type" -> Json.fromString("object"),
      "properties" -> Json.fromJsonObject(JsonObject.fromIterable(props)),
      "required" -> required,
      "additionalProperties" -> Json.fromBoolean(true)
    )
  }

  private def dedupSchemas(schemas: List[JsonObject]): List[JsonObject] = {
    val seen = scala.collection.mutable.LinkedHashSet[String]()
    val out = scala.collection.mutable.ListBuffer[JsonObject]()
    schemas.foreach { s =>
      val key = Json.fromJsonObject(s).noSpaces
      if (!seen.contains(key)) { seen += key; out += s }
    }
    out.toList
  }
}
