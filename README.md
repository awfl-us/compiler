AWFL Compiler (Scala 3)

Summary
- This subproject compiles AWFL DSL workflows from Scala into runnable YAML plus JSON Schema type definitions used by the AWFL Functions runtime.
- You point it at a Scala object that implements us.awfl.core.Workflow[In, Out]. It:
  - Emits one or more workflow YAML files
  - Infers input/output types and emits JSON Schemas
  - Writes a workflow def that references those schemas

Project scope
- This repository is only the compiler for workflows. It is not the monorepo and does not include the Functions runtime.
- By default it writes types/defs into a sibling functions repo if present, or into a local ./functions directory otherwise (see Paths and outputs).

Status
- Scala 3.3.1
- Version: 0.1.0-SNAPSHOT
- Organization: us.awfl

Prerequisites
- JDK 17+
- sbt 1.9+

Quick start
1) Ensure you have a Scala object that implements us.awfl.core.Workflow[In, Out]. The object should expose at least:
   - workflowName: String
   - inputVal: us.awfl.dsl.BaseValue[_]
   - workflows: List[us.awfl.dsl.Workflow[_]]
   This project does not create workflows; it consumes them.

2) Run the compiler against your workflow object:
   - From this project directory:
     - sbt "run WorkflowBuilder"            # tries workflows.cli.WorkflowBuilder then workflows.WorkflowBuilder
     - sbt "run workflows.cli.WorkflowBuilder"  # fully qualified
     - Or: sbt "runMain us.awfl.compiler.Main WorkflowBuilder"
   - You can optionally pass an output directory for the YAML files:
     - sbt "run WorkflowBuilder out-dir"

3) Inspect outputs (see Paths and outputs below).

CLI usage
- Usage: Main <WorkflowClassName> [OutputDirectory]
- Examples:
  - Main workflows.codebase.workflows.WorkflowBuilder
  - Main WorkflowBuilder
- When launched via sbt, use one of the commands above under Quick start.

What gets generated
- YAML: All non-empty workflows from your object are compiled to YAML and written to OutputDirectory (default: ./yaml_gens). Filenames are derived from workflowName and each workflow’s optional name suffix.
- JSON Schemas: Input and output schemas are ensured under a types directory (see Paths and outputs). The compiler attempts to infer types from source via reflection:
  - If it can infer, it normalizes to fully qualified names
  - If not, it falls back to <FQCN>.Input for input and services.Llm.ChatToolResponse for output
  - When BaseValue samples are available from your workflows, schema generation prefers those via encode-registered samples
- Workflow def: A definition file is written that references the input/output schema names.

Paths and outputs
- The compiler decides where to place types and def files using PathsUtil:
  - If a sibling repo ../functions exists, outputs go to:
    - ../functions/workflows/types
    - ../functions/workflows/defs
  - Otherwise, it writes to directories under this repo:
    - ./functions/workflows/types
    - ./functions/workflows/defs
- YAML workflow files go to the argument OutputDirectory or to ./yaml_gens by default.

Programmatic use (optional)
- You can call into the helper APIs directly from Scala code if you embed the compiler:
  - us.awfl.compiler.YamlGen.writeWorkflowsYaml(workflows, outputDir, baseName)
  - us.awfl.compiler.TypesAndDefs.ensureTypesFromBaseValues(typesDir, inName, outName, inputTypeOpt, outputTypeOpt, inputValueOpt, outputValueOpt)
  - us.awfl.compiler.TypesAndDefs.writeWorkflowDef(defsDir, typesDir, defName, inputTypeOpt, outputTypeOpt, inName, outName)

Adding to another project
- Until releases are published, use publishLocal and SNAPSHOTs:
  - In this repo: sbt publishLocal
  - In your project build.sbt add:
    - libraryDependencies += "us.awfl" %% "compiler" % "0.1.0-SNAPSHOT"
- This project itself depends on:
  - "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"
  - "us.awfl" %% "compiler-yaml" % "0.1.0-SNAPSHOT"
  - "us.awfl" %% "workflow-utils" % "0.1.0-SNAPSHOT"
  - "io.circe" %% "circe-core" % 0.14.7
  - "io.circe" %% "circe-yaml" % 0.14.2

Troubleshooting
- Class not found: Ensure you pass a fully qualified name or a short name that matches one of the default packages tried: workflows.cli.<Name> or workflows.<Name>.
- Not a Scala object: The compiler requires a Scala object (module). It will exit if you point it at a plain class.
- Does not extend core.Workflow: Your object must implement us.awfl.core.Workflow[In, Out].
- No YAML emitted: Only workflows with non-empty step lists are written.

Development
- Build: sbt compile
- Run: sbt "run <WorkflowClassName> [OutputDirectory]"
- Scala options: -deprecation -feature -language:implicitConversions
- Source layout:
  - src/main/scala/us/awfl/compiler/* (entrypoint + helpers)
  - src/main/scala/us/awfl/compiler/codegen/* (codegen utilities)

License
- See LICENSE in this repository.