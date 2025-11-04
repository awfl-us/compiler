package us.awfl.workflows.codegen

/**
  * Lightweight debug logger for schema/codegen instrumentation.
  * Enable with environment variable SCHEMA_CODEGEN_DEBUG=1
  * or JVM property -Dschema.codegen.debug=true
  */
object DebugLog {
  private lazy val enabledFlag: Boolean = {
    val envOn  = sys.env.get("SCHEMA_CODEGEN_DEBUG").exists(v => v.equalsIgnoreCase("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"))
    val propOn = sys.props.get("schema.codegen.debug").exists(v => v.equalsIgnoreCase("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"))
    envOn || propOn
  }

  def enabled: Boolean = enabledFlag

  private val indentLevel = new ThreadLocal[Int]() {
    override def initialValue(): Int = 0
  }

  private def pad: String = "  " * indentLevel.get()

  def log(msg: => String): Unit = if (enabledFlag) println(s"[SCHEMA_CODEGEN_DEBUG] ${pad}${msg}")
  def warn(msg: => String): Unit = if (enabledFlag) System.err.println(s"[SCHEMA_CODEGEN_DEBUG][warn] ${pad}${msg}")

  def withScope(label: String)(body: => Unit): Unit = {
    if (!enabledFlag) { body; return }
    log(s"BEGIN ${label}")
    indentLevel.set(indentLevel.get() + 1)
    try body
    finally {
      indentLevel.set(Math.max(0, indentLevel.get() - 1))
      log(s"END   ${label}")
    }
  }
}
