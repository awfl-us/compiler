package us.awfl.node

import us.awfl.dsl.*
import us.awfl.dsl.CelOps.*
import us.awfl.yaml
import us.awfl.yaml.encodeCel

import java.time.Instant

object Compiler {

  case class GeneratedModule(filename: String, content: String)

  private def sanitizeId(s: String): String =
    s.replaceAll("[^A-Za-z0-9_.-]", "_")

  private def toRoutePath(className: String, wfName: Option[String]): String = {
    val base = s"${className}${wfName.map(n => s"-$n").getOrElse("")}"
    
    // Keep close to topicContextYoj style: kebab-ish path, no dots
    
    s"/" + sanitizeId(base.replace('.', '-')) + "/run"
  }

  private def nowIso: String = Instant.now().toString

  // Minimal structural introspection for steps → JSON model embedded in JS
  private def stepToJs(step: Step[_, _]): String = step match {
    case c: Call[_, _] =>
      val callName = c.call
      val hasResult = Option(c.resultValue).nonEmpty
      val argsStr = yaml.encode(c.args).json.noSpaces
      s"""{ "kind": "Call", "name": "${sanitizeId(c.name)}", "call": "${callName}", "args": ${argsStr}, "hasResult": ${hasResult} }"""

    case r: Return[_] =>
      val valueStr = yaml.encode(r.value).json.noSpaces
      s"""{ "kind": "Return", "name": "${sanitizeId(r.name)}", "value": ${valueStr} }"""

    case f: For[_, _] =>
      s"""{ "kind": "For", "name": "${sanitizeId(f.name)}", "itemName": "${f.name}Value", "in": "${encodeCel(f.in.resolver.path)}" }"""

    case fr: ForRange[_] =>
      val fromStr = encodeCel(fr.from)
      val toStr = encodeCel(fr.to)
      s"""{ "kind": "ForRange", "name": "${sanitizeId(fr.name)}", "idx": "${fr.name}Idx", "from": "${fromStr}", "to": "${toStr}" }"""

    case fd: Fold[_, _] =>
      val listStr = encodeCel(fd.list.resolver.path)
      s"""{ "kind": "Fold", "name": "${sanitizeId(fd.name)}", "itemName": "${fd.item}", "list": "${listStr}" }"""

    case sw: Switch[_, _] =>
      s"""{ "kind": "Switch", "name": "${sanitizeId(sw.name)}" }"""

    case tr: Try[_, _] =>
      s"""{ "kind": "Try", "name": "${sanitizeId(tr.name)}" }"""

    case Raise(_, raise) =>
      val valueStr = yaml.encode(raise).json.noSpaces
      s"""{ "kind": "Raise", "name": "Raise", "value": ${valueStr} }"""

    case b: Block[_, _] =>
      val (steps, _) = b.run
      s"""{ "kind": "Block", "name": "${sanitizeId(b.name)}", "steps": [${steps.map(stepToJs).mkString(",")}] }"""

    case fm: FlatMap[_, _, _] =>
      // Just render the inner step
      stepToJs(fm.step)
  }

  private def workflowModelJson[T](workflow: Workflow[T], className: String): String = {
    val (steps, _result) = workflow.steps
    val stepsJson = steps.map(stepToJs).mkString(",")
    val wfName = workflow.name.getOrElse("")
    val id = sanitizeId(s"${className}${workflow.name.map(n => s"-$n").getOrElse("")}")
    s"""
    {
      "id": "${id}",
      "className": "${className}",
      "workflowName": ${if (wfName.isEmpty) "null" else s"\"$wfName\""},
      "generatedAt": "${nowIso}",
      "steps": [${stepsJson}]
    }
    """.stripMargin
  }

  def compile[T](workflow: Workflow[T], className: String): GeneratedModule = {
    val route = toRoutePath(className, workflow.name)
    val model = workflowModelJson(workflow, className)

    val js = s"""
// Auto-generated from DSL workflow by node.Compiler
// Style mirrored from functions/jobs/context/topicContextYoj.js

import express from 'express';
// Optional: wire Pub/Sub publishing here if needed later
// import { PubSub } from '@google-cloud/pubsub';

const router = express.Router();

const WORKFLOW_MODEL = ${model};

router.post('${route}', async (req, res) => {
  try {
    const input = req?.body || {};

    // TODO: Interpret WORKFLOW_MODEL.steps to execute the workflow imperatively.
    // For now, just echo the model and input so the endpoint is functional.

    return res.status(200).json({ ok: true, workflow: WORKFLOW_MODEL, input });
  } catch (err) {
    console.error('Error in ${route}:', err);
    return res.status(400).json({ error: err?.message || 'Failed to run workflow' });
  }
});

export default router;
""".stripMargin

    val fileBase = sanitizeId(s"${className}${workflow.name.map(n => s"-$n").getOrElse("")}").replace('.', '-')
    val filename = s"${fileBase}.js"
    GeneratedModule(filename, js)
  }
}
