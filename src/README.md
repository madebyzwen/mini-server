# Source Code

This directory contains the Mini Server application source code.

The source structure should remain small, understandable, and focused on the responsibilities required by the active project requirements.

## Purpose

Use this directory for implementation code that belongs to the Mini Server runtime.

The source code should cover responsibilities such as:

- Application startup
- HTTP server initialization
- Static file serving
- Request routing
- Site detection
- JSON API handling
- JSON persistence
- Error handling
- Browser launch integration
- Supporting utility code

Application-specific web content does not belong in this directory.

Hosted web applications belong below:

www/

## Structure

The final Java package structure should be chosen during implementation.

The source layout should provide clear separation between major responsibilities without introducing unnecessary layers.

A possible logical separation may include:

- Startup
- Server
- Routing
- Static file handling
- API handling
- Persistence
- Browser integration
- Utilities

This is guidance rather than a mandatory package structure.

The implementation should prefer clarity over architectural complexity.

## Java Compatibility

All Java source code for the initial implementation must remain compatible with Java 8.

Do not use language features or standard library APIs that require a newer Java runtime.

Build configuration must enforce the approved Java target level.

## Dependencies

Prefer Java standard library functionality where it provides a reasonable solution.

External dependencies should only be introduced when they provide a clear benefit and remain compatible with Java 8.

Avoid adding frameworks solely for convenience when the required functionality can remain simple without them.

## Server Responsibilities

The Java implementation is responsible for:

- Starting the local HTTP server
- Binding to the approved local interface
- Obtaining the dynamically assigned port
- Serving static web content
- Routing API requests
- Determining site scope
- Enforcing application isolation
- Reading and writing JSON persistence files
- Returning appropriate HTTP responses
- Reporting runtime failures
- Starting Microsoft Edge after successful server initialization

The server must remain generic.

It must not contain application-specific business logic for individual hosted web applications.

## Web Content

Static web application content does not belong in this source directory.

The web root is:

www/

Shared browser-side functionality belongs below:

www/_shared/

Individual applications belong below:

www/<site>/

## Source Code Principles

Source code should:

- Be written in English
- Use English comments
- Remain understandable without unnecessary abstraction
- Keep responsibilities clearly separated
- Avoid duplicated logic
- Avoid machine-specific paths
- Avoid embedded credentials or secrets
- Handle failures explicitly
- Preserve application isolation
- Avoid exposing arbitrary filesystem access
- Remain consistent with active requirements and architectural decisions

## Coding Agent Guidance

Before implementing or modifying source code:

1. Read AGENTS.md.
2. Check requirements/INDEX.md.
3. Read the active requirement or requirements relevant to the task.
4. Check docs/ARCHITECTURE.md.
5. Check applicable decisions in docs/DECISIONS.md.
6. Review tasks/ACTIVE.md.

Do not introduce behavior that conflicts with an active requirement or approved decision.

If implementation work reveals a missing or contradictory requirement, document the issue rather than silently choosing a permanent project behavior.

## Testing

Source code changes should be accompanied by relevant tests where practical.

Tests belong below:

tests/

Do not claim that behavior has been verified unless the corresponding test or manual verification has actually been performed.

## Current State

No Mini Server source code has been implemented yet.