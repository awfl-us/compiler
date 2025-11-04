package us.awfl.workflows.codegen

object Containers {
  // Primitive and special types that should not emit $ref
  def isPrimitiveLike(typeName: String): Boolean = {
    val t = if (typeName == null) "" else typeName.replaceAll(raw"\s+", "")
    t.endsWith(".String") || t == "String" || t == "java.lang.String" ||
    t.endsWith(".Boolean") || t == "Boolean" || t == "boolean" ||
    t.endsWith(".Int") || t == "Int" || t == "int" ||
    t.endsWith(".Long") || t == "Long" || t == "long" ||
    t.endsWith(".Double") || t == "Double" || t == "double" || t.endsWith(".Float") || t == "Float" || t == "float" ||
    t == "dsl.Field" || t.endsWith(".Field")
  }

  // Extract inner generic parameter from either Scala-style [T] or Java-style <T>
  def extractGenericInner(typeName: String): Option[String] = {
    if (typeName == null) return None
    val s = typeName.trim
    val lb = s.indexOf('[')
    val ra = s.indexOf('<')
    if (lb >= 0) {
      val start = lb
      var i = start + 1
      var depth = 1
      while (i < s.length && depth > 0) {
        if (s.charAt(i) == '[') depth += 1
        else if (s.charAt(i) == ']') depth -= 1
        i += 1
      }
      if (depth == 0) Some(s.substring(start + 1, i - 1)) else None
    } else if (ra >= 0) {
      val start = ra
      var i = start + 1
      var depth = 1
      while (i < s.length && depth > 0) {
        if (s.charAt(i) == '<') depth += 1
        else if (s.charAt(i) == '>') depth -= 1
        i += 1
      }
      if (depth == 0) Some(s.substring(start + 1, i - 1)) else None
    } else None
  }

  private def baseName(typeName0: String): String = {
    if (typeName0 == null) return ""
    val s = typeName0.trim
    val idx1 = s.indexOf('[')
    val idx2 = s.indexOf('<')
    val cut = if (idx1 >= 0 && idx2 >= 0) math.min(idx1, idx2)
              else if (idx1 >= 0) idx1
              else if (idx2 >= 0) idx2
              else s.length
    s.substring(0, cut)
  }

  private val arrayContainers: Set[String] = Set(
    "dsl.ListValue",
    "ListValue",
    "scala.List",
    "scala.collection.immutable.List",
    "scala.Seq",
    "scala.collection.Seq",
    "scala.collection.immutable.Seq",
    "scala.Vector",
    "scala.Array",
    "java.util.List",
    "java.util.Collection",
    "scala.collection.Iterable",
    "scala.Iterable"
  )

  private val valueContainers: Set[String] = Set(
    "dsl.BaseValue",
    "BaseValue",
    "dsl.Value",
    "Value",
    "dsl.Obj",
    "Obj",
    "scala.Option",
    "Option",
    "java.util.Optional"
  )

  private def normalizeSpaces(s: String): String = if (s == null) "" else s.replaceAll(raw"\s+", "")

  // Unwrap known container FQCNs (even when presented as strings with generics) to their contained type
  def unwrapKnownContainerFqcn(typeName0: String): (String, Boolean /*isArray*/, Boolean /*wasContainer*/) = {
    if (typeName0 == null || typeName0.trim.isEmpty) return ("", false, false)

    val raw = typeName0.trim
    val bn = baseName(raw)

    // Strip whitespace within inner generic
    val innerOpt = extractGenericInner(raw).map(_.trim)

    // Match by exact name or by suffix (e.g., foo.ListValue)
    def isArrayC(name: String): Boolean = arrayContainers.contains(name) || arrayContainers.exists(ac => name.endsWith("." + ac))
    def isValueC(name: String): Boolean = valueContainers.contains(name) || valueContainers.exists(vc => name.endsWith("." + vc))

    if (isArrayC(bn)) {
      val inner = innerOpt.getOrElse("java.lang.Object")
      (inner, true, true)
    } else if (isValueC(bn)) {
      val inner = innerOpt.getOrElse("java.lang.Object")
      (inner, false, true)
    } else {
      // Not a recognized container
      (typeName0, false, false)
    }
  }

  // Recognize special container types and unwrap to their contained type for top-level schema emission
  def unwrapContainerTopLevel(fq0: String): (String, Boolean /*isArray*/, Boolean /*wasContainer*/) = {
    if (fq0 == null || fq0.trim.isEmpty) return ("", false, false)

    val s = normalizeSpaces(fq0)

    // First handle explicit generics and known containers
    val (inner, isArr, wasCont) = unwrapKnownContainerFqcn(s)
    if (wasCont) return (normalizeSpaces(inner), isArr, true)

    // Handle Java array syntax like com.Foo[] or String[] if it ever appears
    if (s.endsWith("[]")) {
      val base = s.stripSuffix("[]")
      return (base, true, true)
    }

    // No container recognized; return as-is
    (fq0, false, false)
  }
}
