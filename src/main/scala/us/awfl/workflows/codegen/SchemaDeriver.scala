package us.awfl.workflows.codegen

import us.awfl.dsl._
import io.circe.{Json, JsonObject}

object SchemaDeriver {
  final case class ObjValue(json: Json)

  // Simple in-memory registry of sample JSON by fully-qualified type name.
  // Populate this from call sites that can materialize representative JSON for a type.
  // When present, ApiFacade will build JSON Schemas from these samples via ExampleSchema.
  private var samples: Map[String, Json] = Map.empty

  def registerSampleJson(typeFqcn: String, json: Json): Unit =
    samples = samples.updated(typeFqcn, json)

  def sampleJsonForType(typeFqcn: String): Option[Json] = samples.get(typeFqcn)

  def encode(value: BaseValue[_]): ObjValue = value match {
    // For Resolved values, materialize a concrete value via Spec.init (through .get or direct for Field)
    case f: FieldValue =>
      val v: String = summon[Spec[String]].init(f.resolver)
      encode(Obj(v))

    case r: Resolved[_] =>
      val concrete: Any = r.get // uses the implicit Spec for the underlying type
      encode(Obj(concrete))

    case Obj(v) => v match {
      case list: List[_] =>
        ObjValue(Json.arr(list.map {
          case bv: BaseValue[_] => encode(bv).json
          case other             => encode(Obj(other)).json
        }*))

      case Some(inner) => encode(obj(inner))
      case None        => ObjValue(Json.Null)

      case opt: OptValue[_] =>
        encode(opt.getOrElse(Value.nil(opt.spec)))
      case opt: OptList[_] =>
        encode(opt.getOrElse(ListValue.nil(opt.spec)))

      case p: Product =>
        val names      = p.productElementNames.toList
        val values     = p.productIterator.toList
        val jsonFields = encodePairs(names.zip(values))
        ObjValue(Json.fromJsonObject(JsonObject.fromIterable(jsonFields)))

      case m: Map[_, _] =>
        val jsonFields = encodePairs(m.asInstanceOf[Map[String, Any]].toList)
        ObjValue(Json.fromJsonObject(JsonObject.fromIterable(jsonFields)))

      case s: String  => ObjValue(Json.fromString(s))
      case d: Double  => ObjValue(Json.fromDouble(d).get)
      case i: Int     => ObjValue(Json.fromInt(i))
      case b: Boolean => ObjValue(Json.fromBoolean(b))

      case other =>
        throw new Exception(s"Provided Spec did not produce a Product type or Map[String, _]: $other")
    }
  }

  private def encodePairs(pairs: List[(String, _)]): List[(String, Json)] =
    pairs.map { case (name, field) =>
      val json = field match {
        case bv: BaseValue[_] => encode(bv).json
        case other            => encode(obj(field)).json
      }
      name -> json
    }
}
