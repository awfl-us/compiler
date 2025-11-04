import scala.util.Try

object WorkflowLoader {
  final case class Loaded(fqcn: String, cls: Class[?], module: AnyRef)

  def resolveCandidates(classNameArg: String): List[String] =
    if (classNameArg.contains('.')) List(classNameArg)
    else List(s"workflows.cli.$classNameArg", s"workflows.$classNameArg")

  def loadFirst(candidates: List[String]): Loaded = {
    def tryLoad(names: List[String]): Option[Loaded] = names match {
      case Nil => None
      case name :: rest =>
        // Prefer Scala object module class (name + "$"), then fall back to plain class
        val moduleAttempt = Try {
          val moduleClass = Class.forName(name + "$")
          val moduleInstance = moduleClass.getField("MODULE$").get(null).asInstanceOf[AnyRef]
          Loaded(name, moduleClass, moduleInstance)
        }.toOption

        moduleAttempt
          .orElse {
            // Fallback: plain class (static methods)
            Try {
              val c = Class.forName(name)
              Loaded(name, c, null.asInstanceOf[AnyRef])
            }.toOption
          }
          .orElse(tryLoad(rest))
    }

    tryLoad(candidates).getOrElse {
      System.err.println(s"Class not found. Tried: ${candidates.mkString(", ")}")
      sys.exit(2)
      throw new RuntimeException("unreachable")
    }
  }

  def findWorkflowsMethod(cls: Class[?], module: AnyRef): Option[java.lang.reflect.Method] = {
    val tryOn: List[Class[?]] = List(cls) ++ (Option(module).map(_.getClass).toList)
    val names = List("workflows", "workflow")
    names.view
      .flatMap { m =>
        tryOn.view.flatMap(c => Try(c.getMethod(m)).toOption)
      }
      .headOption
  }
}
