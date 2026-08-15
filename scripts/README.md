# Scripts

This directory contains optional convenience and project automation scripts for Mini Server.

Maven is the authoritative build and test system defined by D-019.

Scripts must not become an independent or competing build system.

## Purpose

Scripts may provide convenient entry points for repeatable development, runtime, packaging, validation, or release operations.

Possible uses include:

- Starting Mini Server during development
- Invoking Maven builds
- Invoking automated tests
- Packaging a distribution
- Creating the reusable template package
- Performing release preparation
- Running project validation
- Supporting platform-specific startup behavior where appropriate

A script should only be introduced when it provides a clear practical benefit.

## Maven Integration

The authoritative build configuration is:

    pom.xml

at the repository root.

Build, dependency, compilation, and automated Java test configuration belong in Maven.

Scripts may invoke Maven commands such as:

    mvn test

or later approved build and packaging commands.

Scripts must not duplicate Maven configuration independently.

For example, a script must not maintain its own separate:

- Java compiler target
- Dependency list
- Test framework configuration
- Build output definition
- Packaging dependency configuration

If a build-related setting changes, the authoritative change belongs in `pom.xml`.

## Development and Runtime Scripts

Development convenience scripts may be introduced when they simplify common operations.

Examples may include:

    run
    test
    package
    verify

These names are examples only.

The exact filenames, extensions, platforms, and script set will be chosen during implementation when there is an actual need for them.

Not every possible convenience script must exist.

If the Maven command itself is already sufficiently simple, an additional wrapper script is unnecessary.

## Windows Runtime Launch

Mini Server v1.0 targets Windows for the normal user-facing runtime experience.

The distribution provides `start.bat`, `configure.bat`, and `stop.bat`.

Its responsibilities may include starting the Java application in the intended way and supporting the normal desktop-launch experience.

The Mini Server Java implementation remains responsible for:

- Local per-user/computer single-instance handling
- Runtime state below `%LOCALAPPDATA%\MiniServer\runtime\`
- Loopback server startup
- Dynamic port allocation
- Repeated-start detection
- Selecting the active server port
- Windows default-browser launch behavior defined by the active requirements
- Root-only configure behavior using the same single-instance server

A launcher script must not implement a separate competing instance-management or port-selection mechanism.

`start.bat` and `configure.bat` use detached `javaw.exe` invocation with
batch-relative absolute paths. `configure.bat` passes the explicit `configure`
command. `stop.bat` retains its synchronous authenticated-stop command. The
distribution verifier requires all three launchers and rejects packaged Private
configuration.

In particular, it must not:

- Scan for available TCP ports
- Select a fixed server port
- Maintain independent server-instance state
- Bypass the Java single-instance mechanism

## Template Packaging

The maintained reusable starter-template source is stored at:

    template/

The distributed template artifact is:

    miniweb-template.zip

A packaging script may later be introduced to create this archive.

If template packaging becomes part of the Maven build, any convenience script should invoke the authoritative Maven packaging operation rather than implementing a second independent packaging process.

The final packaging workflow will be defined during implementation preparation.

## Cross-Platform Development

Development may take place on platforms other than the Windows runtime target.

Project automation should avoid unnecessary dependence on one developer workstation.

Where platform-specific scripts are necessary, their intended platform must be clear from their filename or documentation.

Cross-platform development scripts and Windows runtime-launch scripts may have different responsibilities.

## Script Design

Scripts should:

- Be small and understandable
- Have one clear purpose
- Fail clearly when the requested operation fails
- Return an appropriate failing exit status where applicable
- Avoid silently ignoring errors
- Avoid modifying unrelated project files
- Avoid machine-specific absolute paths
- Avoid requiring administrator privileges for normal operations
- Avoid embedded credentials, tokens, or secrets
- Delegate authoritative build operations to Maven
- Remain consistent with active requirements and approved decisions

## Build Outputs

Scripts must not treat generated build artifacts as source files.

Normal generated Maven output such as:

    target/

is disposable build output.

Legacy project-local runtime state such as:

    .runtime/

is also not source content and remains ignored as a safeguard. The target application runtime state is stored outside the installation below `%LOCALAPPDATA%\MiniServer\runtime\`.

Scripts must not intentionally commit generated build output or local runtime state.

## Coding Agent Guidance

Before creating or modifying a script:

1. Read `AGENTS.md`.
2. Check the relevant active requirements.
3. Check `docs/ARCHITECTURE.md`.
4. Check applicable decisions in `docs/DECISIONS.md`.
5. Review the corresponding implementation task in `tasks/ACTIVE.md`.
6. Check whether Maven already provides the required operation.

Do not create a wrapper script merely because one might be convenient in theory.

Create scripts only when they solve a concrete development, runtime, packaging, or release need.

Do not introduce an independent build configuration through scripts.

## Current State

Maven is defined as the authoritative build system.

The `scripts/` directory remains available for future convenience automation.

No concrete project scripts are required until implementation or packaging work demonstrates a need for them.

Concrete scripts will be created later through Codex where required by the approved implementation tasks.
