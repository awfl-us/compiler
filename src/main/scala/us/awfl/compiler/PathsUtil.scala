package us.awfl.compiler

import java.nio.file.{Files, Paths, Path}
import java.nio.charset.StandardCharsets

object PathsUtil {
  val repoRoot: Path = Paths.get("")
  // Support both run contexts: subproject (src/main/scala) and monorepo root (workflows/src/main/scala)
  val srcRootCandidates: List[Path] = List(
    repoRoot.resolve("src/main/scala"),
    repoRoot.resolve("workflows/src/main/scala")
  )

  // Prefer monorepo sibling ../functions when present; otherwise fall back to in-repo functions/*
  def resolveAwflDir(subpath: String): Path = {
    val sibling = repoRoot.resolve("../functions").resolve(subpath)
    val inRepo = repoRoot.resolve("functions").resolve(subpath)
    val chosen =
      if (Files.exists(repoRoot.resolve("../functions"))) sibling
      else inRepo
    chosen.normalize()
  }

  val defsDir: Path = resolveAwflDir("workflows/defs")
  val typesDir: Path = resolveAwflDir("workflows/types")

  def toSourcePath(fqcn: String): Path = {
    val rel = fqcn.replace('.', '/') + ".scala"
    // Find the first candidate that exists
    srcRootCandidates
      .map(_.resolve(rel))
      .find(p => Files.exists(p))
      .getOrElse(srcRootCandidates.head.resolve(rel))
  }

  def readSource(fqcn: String): Option[String] = {
    val p = toSourcePath(fqcn)
    if (Files.exists(p)) Some(Files.readString(p, StandardCharsets.UTF_8)) else None
  }
}