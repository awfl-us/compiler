package us.awfl.workflows.codegen

object ReflectionUtil {
  // Try loading a class by common Scala/JVM name patterns, including nested types with '$'
  def tryLoadClass(fqcn: String): Option[Class[?]] = {
    def lastDotToDollar(s: String): String = {
      val idx = s.lastIndexOf('.')
      if (idx >= 0) s.substring(0, idx) + "$" + s.substring(idx + 1) else s
    }

    def typesDollars(s: String): String = {
      val parts = s.split(raw"\.")
      val typeStartIdx = parts.indexWhere(p => p.nonEmpty && p.charAt(0).isUpper)
      if (typeStartIdx <= 0) s
      else {
        val pkg = parts.take(typeStartIdx).mkString(".")
        val types = parts.drop(typeStartIdx).mkString("$")
        if (pkg.isEmpty) types else s"$pkg.$types"
      }
    }

    val candidates = List(
      fqcn,
      lastDotToDollar(fqcn),
      typesDollars(fqcn)
    ).distinct

    candidates.view.flatMap { n =>
      try Some(Class.forName(n))
      catch { case _: Throwable => None }
    }.headOption
  }
}
