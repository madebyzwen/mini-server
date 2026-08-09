# Source Code

This directory contains the Java source tree for Mini Server.

The project uses Maven as its authoritative build system and follows the conventional Maven source layout defined by D-019.

## Source Layout

Production Java source code belongs below:

    src/main/java/

The base Java package is:

    io.github.madebyzwen.miniserver

The corresponding base source directory is:

    src/main/java/io/github/madebyzwen/miniserver/

Automated Java tests belong below:

    src/test/java/

The corresponding base test directory is:

    src/test/java/io/github/madebyzwen/miniserver/

The exact subpackage and class structure may be designed during implementation, but it must remain small, understandable, and consistent with the active requirements and approved architectural decisions.

## Responsibilities

Production source code is responsible for Mini Server runtime behavior such as:

- Application startup
- Per-installation instance locking
- Runtime state handling
- HTTP server initialization
- Dynamic loopback port allocation
- Static file serving
- Request routing
- Site detection
- JSON API handling
- JSON persistence
- Error handling and diagnostics
- Microsoft Edge launch integration
- Supporting utility functionality where required

The server implementation must remain generic.

Application-specific business logic for individual hosted web applications must not be introduced into the Java server.

## Java Compatibility

Mini Server v1.0 targets Java 8.

Production source code must therefore remain compatible with the Java 8 language level and approved Java 8 runtime.

Do not use language features or runtime APIs that require a newer Java version unless the project target is changed by an approved decision.

The Maven build must enforce the approved Java target.

Development may use a newer JDK, but successful compilation on a newer JDK does not replace final verification against the approved Java 8 runtime.

## Build System

The authoritative Java build configuration is:

    pom.xml

at the repository root.

Maven is responsible for:

- Compiling production source code
- Compiling automated Java tests
- Managing approved dependencies
- Running automated Java tests
- Producing build artifacts
- Supporting the later packaging process

Build behavior must not be duplicated independently in this directory.

Generated Maven output such as:

    target/

is build output and must not be committed as source content.

## Dependencies

Prefer Java standard library functionality when it provides a clear and maintainable solution.

External dependencies may be introduced through Maven when they provide a clear technical benefit.

Dependencies must:

- Remain compatible with the approved Java target
- Have a clear project purpose
- Avoid unnecessary framework complexity
- Be declared through the authoritative Maven build configuration

Exact dependency and plugin versions will be selected during implementation preparation.

Do not introduce dependencies that contradict active requirements or approved decisions.

## Web Content

Hosted web application content does not belong in the Java source tree.

The runtime web root is:

    www/

Shared browser-side functionality belongs below:

    www/_shared/

Individual hosted applications belong below:

    www/<site>/

The maintained reusable starter-template source belongs below:

    template/

The `template/` directory is packaging input and is not part of the runtime web root.

The distributed template artifact is:

    miniweb-template.zip

## Persistence

Application persistence remains below the corresponding hosted application directory:

    www/<site>/data/data.json

Java source code must derive and control persistence locations according to the active requirements.

Client input must never be interpreted as an arbitrary persistence filesystem path.

Persistence data below an application's `data/` directory must not be exposed through normal static file serving.

## Runtime State

Per-installation runtime state belongs outside the web root.

The intended runtime location is:

    .runtime/

Runtime state includes information such as:

    .runtime/instance.lock
    .runtime/instance.json

Runtime state is created and managed by the running Mini Server implementation.

It is not source code and must not be committed.

## Source Code Principles

Source code should:

- Be written in English
- Use English comments
- Remain understandable without unnecessary abstraction
- Keep responsibilities clearly separated
- Avoid duplicated logic
- Avoid machine-specific absolute paths
- Avoid embedded credentials or secrets
- Handle failures explicitly
- Preserve loopback-only server binding
- Preserve operating-system-assigned dynamic port allocation
- Preserve the per-installation single-instance guarantee
- Preserve URL-derived application scoping
- Preserve controlled persistence path mapping
- Avoid exposing arbitrary filesystem access
- Remain consistent with active requirements and approved architectural decisions

Subpackages and helper classes may be introduced when they improve clarity, but the implementation should prefer a small understandable structure over unnecessary architectural layers.

## Automated Tests

Automated Java test source code belongs below:

    src/test/java/

The top-level:

    tests/

directory is used for test documentation and other test-related project material and is not the Maven Java test source directory.

Source changes should be accompanied by relevant automated tests where the behavior can reasonably be tested.

Do not claim that behavior has been verified unless the corresponding automated or manual verification has actually been performed.

## Coding Agent Guidance

Before implementing or modifying Java source code:

1. Read `AGENTS.md`.
2. Check `requirements/INDEX.md`.
3. Read the active requirement or requirements relevant to the task.
4. Check `docs/ARCHITECTURE.md`.
5. Check applicable decisions in `docs/DECISIONS.md`.
6. Review `tasks/ACTIVE.md`.
7. Check the authoritative root `pom.xml` once it exists.

Do not silently invent permanent project behavior when requirements or decisions are missing or contradictory.

If implementation work reveals a missing or contradictory requirement, document the issue and request clarification rather than introducing an undocumented architectural decision.

## Current State

The Maven source structure is defined by D-019.

Implementation of the Mini Server Java source code will be performed later through Codex according to the active requirements, approved decisions, and implementation tasks.