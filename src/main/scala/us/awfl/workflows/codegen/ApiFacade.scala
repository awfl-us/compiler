package us.awfl.workflows.codegen

import io.circe.{Json, JsonObject}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import core.TypeInference._
import us.awfl.dsl.BaseValue

object ApiFacade {
  // Derivation disabled: return None to force placeholders elsewhere
  def deriveSchemaForType(typeFqcn: String): Option[JsonObject] = None

  // Ensure type files exist; prefer SchemaDeriver sample JSON -> ExampleSchema; fallback to container-primitive handling then placeholders
  // Write-only behavior: do not read existing files; always (over)write derived or placeholder schemas.
  def ensureTypesWithRealSchema(
    typesDir: Path,
    inSchemaName: String,
    outSchemaName: String,
    inputTypeOpt: Option[String],
    outputTypeOpt: Option[String]
  ): Unit = {
    def ensureDir(p: Path): Unit = {
      if (!Files.exists(p)) Files.createDirectories(p)
    }

    DebugLog.withScope(s"ensureTypesWithRealSchema typesDir=${typesDir}, inSchema=${inSchemaName}, outSchema=${outSchemaName}, inputType=${inputTypeOpt.getOrElse("(none)")}, outputType=${outputTypeOpt.getOrElse("(none)")}") {
      ensureDir(typesDir)

      def isAutoPlaceholder(obj: JsonObject): Boolean = {
        val tpe = obj.apply("type").flatMap(_.asString)
        val props = obj.apply("properties").flatMap(_.asObject)
        val addl = obj.apply("additionalProperties").flatMap(_.asBoolean)
        val ok = tpe.contains("object") && props.isEmpty && addl.contains(true)
        if (ok) DebugLog.log("Detected auto placeholder schema (object with additionalProperties=true and no properties)")
        ok
      }

      // Minimal primitive schema builder (avoids creating child files for primitives when unwrapping containers)
      def baseSchemaForNameLocal(typeName0: String): JsonObject = {
        val typeName = if (typeName0 == null) "" else typeName0.replaceAll(raw"\s+", "")
        val out =
          if (typeName.endsWith(".String") || typeName == "String" || typeName == "java.lang.String")
            JsonObject("type" -> Json.fromString("string"))
          else if (typeName.endsWith(".Boolean") || typeName == "Boolean" || typeName == "boolean")
            JsonObject("type" -> Json.fromString("boolean"))
          else if (typeName.endsWith(".Int") || typeName == "Int" || typeName == "int")
            JsonObject("type" -> Json.fromString("integer"))
          else if (typeName.endsWith(".Long") || typeName == "Long" || typeName == "long")
            JsonObject("type" -> Json.fromString("integer"))
          else if (typeName.endsWith(".Double") || typeName == "Double" || typeName == "double" || typeName.endsWith(".Float") || typeName == "Float" || typeName == "float")
            JsonObject("type" -> Json.fromString("number"))
          else if (typeName == "dsl.Field" || typeName.endsWith(".Field"))
            JsonObject("type" -> Json.fromString("object"), "additionalProperties" -> Json.fromBoolean(true))
          else
            JsonObject("type" -> Json.fromString("object"), "additionalProperties" -> Json.fromBoolean(true))
        DebugLog.log(s"baseSchemaForNameLocal(${typeName0}) -> ${Json.fromJsonObject(out).noSpaces}")
        out
      }

      // Ensure a child FQCN type file exists as a placeholder (no derivation)
      def childEnsure(childFqcn: String, parentVisited: Set[String]): JsonObject = {
        DebugLog.log(s"childEnsure childFqcn=${childFqcn}")
        val childName = schemaNameFromFqcn(childFqcn)
        val childPath = typesDir.resolve(childName + ".json")
        DebugLog.log(s"Child schemaName=${childName}, path=${childPath.toAbsolutePath}")

        if (!Files.exists(childPath)) {
          DebugLog.warn(s"Writing placeholder for child ${childFqcn} (derivation removed)")
          FileIO.writePlaceholder(typesDir, childName, childFqcn.split(raw"\.").last, childFqcn)
        } else {
          DebugLog.log(s"Child schema already exists for ${childFqcn}; not rewriting")
        }

        val ref = JsonObject("$ref" -> Json.fromString(s"https://topaigents.dev/workflows/types/${childName}.json"))
        DebugLog.log(s"Emitting $$ref to ${childName}.json for child ${childFqcn}")
        ref
      }

      // Attempt to build a schema from SchemaDeriver sample JSON for a given FQCN
      def schemaFromDeriver(typeFqcn: String): Option[JsonObject] = {
        SchemaDeriver.sampleJsonForType(typeFqcn).map { sampleJson =>
          DebugLog.log(s"Found deriver sample JSON for ${typeFqcn}; building ExampleSchema")
          DebugLog.log(s"Deriver sample JSON for ${typeFqcn}: ${sampleJson.spaces2}")
          val built = ExampleSchema.fromJson(sampleJson)
          DebugLog.log(s"Built ExampleSchema for ${typeFqcn}: ${Json.fromJsonObject(built).spaces2}")
          built
        }
      }

      // Ensure one schema file by name/type; prefer deriver sample -> ExampleSchema; else try container-primitive; else placeholder
      def requireSchema(name: String, typeFqcn: Option[String], visited: Set[String] = Set.empty): Unit = {
        DebugLog.withScope(s"requireSchema name=${name}, typeFqcn=${typeFqcn.getOrElse("(none)")}") {
          val p = typesDir.resolve(name + ".json")
          DebugLog.log(s"Target path: ${p.toAbsolutePath}")

          // Reason accumulator for placeholder decisions
          val reasons = scala.collection.mutable.ListBuffer[String]()
          if (typeFqcn.isEmpty) reasons += "typeFqcn absent"

          // First: deriver sample JSON path
          val deriverSchemaOpt: Option[JsonObject] = typeFqcn.flatMap { tf =>
            val s = schemaFromDeriver(tf)
            if (s.isEmpty) DebugLog.log(s"No deriver sample JSON registered for ${tf}; skipping ExampleSchema path")
            s
          }
          DebugLog.log(s"deriverSchemaOpt.isDefined=${deriverSchemaOpt.isDefined}")
          if (deriverSchemaOpt.isEmpty && typeFqcn.nonEmpty) reasons += s"no deriver sample for ${typeFqcn.get}"

          // If no deriver sample, consider top-level container special-case via Containers.unwrapContainerTopLevel
          val (containerChildOpt, containerArrayOpt): (Option[String], Option[Boolean]) = typeFqcn match {
            case Some(tf) =>
              val (inner: String, isArr: Boolean, wasContainer: Boolean) = Containers.unwrapContainerTopLevel(tf)
              DebugLog.log(s"unwrapContainerTopLevel(${tf}) -> inner=${inner}, isArray=${isArr}, wasContainer=${wasContainer}")
              if (!wasContainer) reasons += "no top-level container"
              if (wasContainer) (Some(inner), Some(isArr)) else (None, None)
            case None => (None, None)
          }

          val containerDerivedOpt: Option[JsonObject] =
            if (deriverSchemaOpt.isEmpty) {
              containerChildOpt match {
                case Some(childFqcn) =>
                  if (Containers.isPrimitiveLike(childFqcn)) {
                    DebugLog.log(s"Top-level container inner ${childFqcn} is primitive-like; inlining base schema")
                    val base = baseSchemaForNameLocal(childFqcn)
                    containerArrayOpt match {
                      case Some(true) =>
                        val obj = JsonObject("type" -> Json.fromString("array"), "items" -> Json.fromJsonObject(base))
                        DebugLog.log(s"Emitting array of primitive-like items: ${Json.fromJsonObject(obj).noSpaces}")
                        Some(obj)
                      case _ => Some(base)
                    }
                  } else {
                    DebugLog.log(s"Top-level container inner ${childFqcn} is non-primitive; ensuring child placeholder and emitting $$ref")
                    val refObj = childEnsure(childFqcn, visited)
                    containerArrayOpt match {
                      case Some(true) =>
                        val obj = JsonObject("type" -> Json.fromString("array"), "items" -> Json.fromJsonObject(refObj))
                        DebugLog.log(s"Emitting array of $$ref items: ${Json.fromJsonObject(obj).noSpaces}")
                        Some(obj)
                      case _ => Some(refObj)
                    }
                  }
                case None =>
                  typeFqcn.flatMap { fq =>
                    DebugLog.log(s"No top-level container; derivation disabled for ${fq}; will write/keep placeholder")
                    None
                  }
              }
            } else None

          val derivedOpt: Option[JsonObject] = deriverSchemaOpt.orElse(containerDerivedOpt)
          DebugLog.log(s"derivedOpt.isDefined=${derivedOpt.isDefined}")

          // Write-only: always write a file based on the best available derived schema or a placeholder; do not read existing files
          derivedOpt match {
            case Some(schemaObj) =>
              val title = typeFqcn.map(_.split(raw"\.").last).getOrElse(name)
              DebugLog.log(s"Writing schema file types/${name}.json (title=${title}) [write-only]")
              val desc = deriverSchemaOpt.map(_ => s"Schema inferred from deriver sample JSON for ${typeFqcn.getOrElse(name)}.").getOrElse(s"Derived (container-primitive only) schema for ${typeFqcn.getOrElse(name)}")
              FileIO.writeTypeFile(typesDir, name, title, schemaObj, desc)
            case None =>
              val title = typeFqcn.map(_.split(raw"\.").last).getOrElse(name)
              val reasonStr = if (reasons.nonEmpty) reasons.mkString("; ") else "no derivation path available"
              DebugLog.warn(s"Writing placeholder for types/${name}.json (title=${title}); derivation removed; reason=${reasonStr} [write-only]")
              FileIO.writePlaceholder(typesDir, name, title, typeFqcn.getOrElse(name))
              System.err.println(s"Warning: Created placeholder types/${name}.json. Please replace with a concrete schema for ${typeFqcn.getOrElse(name)}.")
          }
        }
      }

      requireSchema(inSchemaName, inputTypeOpt)
      requireSchema(outSchemaName, outputTypeOpt)
    }
  }

  // Preferred path when you have real workflow values at hand: register schema samples from SchemaDeriver.encode
  // Then delegate to ensureTypesWithRealSchema to write concrete schemas.
  def ensureTypesFromBaseValues(
    typesDir: Path,
    inSchemaName: String,
    outSchemaName: String,
    inputTypeOpt: Option[String],
    outputTypeOpt: Option[String],
    inputValueOpt: Option[BaseValue[_]],
    outputValueOpt: Option[BaseValue[_]]
  ): Unit = {
    DebugLog.withScope(s"ensureTypesFromBaseValues inSchema=${inSchemaName}, outSchema=${outSchemaName}") {
      inputTypeOpt.zip(inputValueOpt).headOption.foreach { case (fqcn, inVal) =>
        val json = SchemaDeriver.encode(inVal).json
        DebugLog.log(s"Registering deriver sample JSON for input ${fqcn} via encode()")
        DebugLog.log(s"encode(input ${fqcn}) returned JSON:\n${json.spaces2}")
        SchemaDeriver.registerSampleJson(fqcn, json)
      }
      outputTypeOpt.zip(outputValueOpt).headOption.foreach { case (fqcn, outVal) =>
        val json = SchemaDeriver.encode(outVal).json
        DebugLog.log(s"Registering deriver sample JSON for output ${fqcn} via encode()")
        DebugLog.log(s"encode(output ${fqcn}) returned JSON:\n${json.spaces2}")
        SchemaDeriver.registerSampleJson(fqcn, json)
      }

      // Now write or rewrite schemas using the registered samples (write-only)
      ensureTypesWithRealSchema(
        typesDir,
        inSchemaName,
        outSchemaName,
        inputTypeOpt,
        outputTypeOpt
      )
    }
  }

  def writeWorkflowDef(
    defsDir: Path,
    typesDir: Path,
    defName: String,
    inputTypeOpt: Option[String],
    outputTypeOpt: Option[String],
    inSchemaName: String,
    outSchemaName: String
  ): Unit =
    FileIO.writeWorkflowDef(
      defsDir,
      typesDir,
      defName,
      inputTypeOpt,
      outputTypeOpt,
      inSchemaName,
      outSchemaName
    )
}
