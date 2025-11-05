# AWFL Compiler (Scala 3)

Compile AWFL DSL workflows defined in Scala into runnable YAML plus JSON Schema type definitions consumed by the AWFL Functions runtime.

- Status: early preview (0.1.0-SNAPSHOT)
- Language: Scala 3.3.1
- Build: sbt 1.9+
- License: MIT

## Overview
Given a Scala object that implements `us.awfl.core.Workflow[In, Out]`, this tool:
- Emits one or more workflow YAML files
- Infers input/output types and writes JSON Schemas
- Writes a workflow definition that references those schemas

This repository contains the compiler only. It is not the monorepo and does not include the AWFL Functions runtime.

## Features
- Reflection-based type inference for workflow I/O with sensible fallbacks
- Deterministic YAML generation for all non-empty workflows in an object
- JSON Schema generation from both inferred types and available BaseValue samples
- Path-aware outputs: integrates with a sibling `../functions` repo if present

## Requirements
- JDK 17+
- sbt 1.9+
- Scala 3.3.1 toolchain

## Quick start
1) Ensure you have a Scala object that implements `us.awfl.core.Workflow[In, Out]` and exposes at least:
   - `workflowName: String`
   - `inputVal: us.awfl.dsl.BaseValue[_]`
   - `workflows: List[us.awfl.dsl.Workflow[_]]`

2) From this project directory, run the compiler against your workflow object:
```
# Tries workflows.cli.WorkflowBuilder then workflows.WorkflowBuilder
sbt "run WorkflowBuilder"

# Fully qualified name
sbt "run workflows.cli.WorkflowBuilder"

# Explicit main
sbt "runMain us.awfl.compiler.Main WorkflowBuilder"

# With optional output directory for YAML files
sbt "run WorkflowBuilder out-dir"
```

3) Inspect the generated outputs (see Paths and outputs below).

## CLI usage
- Usage: `Main <WorkflowClassName> [OutputDirectory]`
- Examples:
  - `Main workflows.codebase.workflows.WorkflowBuilder`
  - `Main WorkflowBuilder`

When launched via sbt, use one of the commands shown in Quick start.

## What gets generated
- YAML: All non-empty workflows are compiled and written to the output directory (default: `./yaml_gens`). Filenames are based on `workflowName` plus any workflow-specific suffix.
- JSON Schemas: Input and output schemas are ensured under a types directory (see Paths and outputs). The compiler attempts to infer types via reflection:
  - If inference succeeds, types are normalized to fully qualified names
  - If it cannot infer, it falls back to `<FQCN>.Input` for input and `services.Llm.ChatToolResponse` for output
  - When BaseValue samples are available, schema generation prefers those via encode-registered samples
- Workflow def: A small definition file is written that references the input/output schema names.

## Paths and outputs
The compiler decides where to place types and def files using `PathsUtil`:
- If a sibling repo `../functions` exists, outputs go to:
  - `../functions/workflows/types`
  - `../functions/workflows/defs`
- Otherwise, it writes under this repo:
  - `./functions/workflows/types`
  - `./functions/workflows/defs`
- YAML workflow files go to the argument `OutputDirectory` or to `./yaml_gens` by default.

## Programmatic APIs (optional)
If you embed the compiler, you can call helper APIs directly:
- `us.awfl.compiler.YamlGen.writeWorkflowsYaml(workflows, outputDir, baseName)`
- `us.awfl.compiler.TypesAndDefs.ensureTypesFromBaseValues(typesDir, inName, outName, inputTypeOpt, outputTypeOpt, inputValueOpt, outputValueOpt)`
- `us.awfl.compiler.TypesAndDefs.writeWorkflowDef(defsDir, typesDir, defName, inputTypeOpt, outputTypeOpt, inName, outName)`

## Adding to another project
Until releases are published, use `publishLocal` and SNAPSHOTs.
1) In this repo:
```
sbt publishLocal
```
2) In your project `build.sbt` add:
```
libraryDependencies += "us.awfl" %% "compiler" % "0.1.0-SNAPSHOT"
```
This project depends on:
- `"us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"`
- `"us.awfl" %% "compiler-yaml" % "0.1.0-SNAPSHOT"`
- `"us.awfl" %% "workflow-utils" % "0.1.0-SNAPSHOT"`
- `"io.circe" %% "circe-core" % 0.14.7`
- `"io.circe" %% "circe-yaml" % 0.14.2`

## Troubleshooting
- Class not found: Pass a fully qualified name or a short name that matches one of the defaults tried: `workflows.cli.<Name>` or `workflows.<Name>`.
- Not a Scala object: The compiler requires a Scala object (module). It will exit if you point it at a plain class.
- Does not extend `core.Workflow`: Your object must implement `us.awfl.core.Workflow[In, Out]`.
- No YAML emitted: Only workflows with non-empty step lists are written.

## Development
- Build: `sbt compile`
- Run: `sbt "run <WorkflowClassName> [OutputDirectory]"`
- Scala options: `-deprecation -feature -language:implicitConversions`
- Source layout:
  - `src/main/scala/us/awfl/compiler/*` (entrypoint + helpers)
  - `src/main/scala/us/awfl/compiler/codegen/*` (codegen utilities)

## Contributing
Issues and pull requests are welcome. Please open an issue to discuss substantial changes before submitting a PR.

## License
MIT — see [LICENSE](./LICENSE).
