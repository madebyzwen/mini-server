# Scripts

This directory contains project scripts that support development, testing, building, running, and maintenance tasks.

Scripts should provide a simple and predictable way to perform common project operations without requiring developers or coding agents to remember long command sequences.

## Purpose

Use this directory for reusable project automation such as:

- Project setup
- Build commands
- Local server startup
- Test execution
- Linting or validation
- Packaging
- Release preparation
- Other repeatable development tasks

Scripts should remain small, understandable, and directly related to the project.

## Planned Scripts

The initial project structure may provide scripts such as:

setup

run

test

lint

The exact filenames, file extensions, and implementation details depend on the final build and runtime structure.

Not every placeholder script must be implemented if the corresponding operation is already handled cleanly by the selected build system.

## Script Design

Scripts should:

- Be deterministic where practical
- Fail clearly when an operation cannot be completed
- Return a failing exit status when the requested operation fails
- Avoid silently ignoring errors
- Avoid modifying unrelated files
- Avoid requiring administrator privileges for normal development tasks
- Avoid embedding machine-specific absolute paths
- Avoid storing credentials, tokens, or other secrets

## Development Environment

Scripts should be suitable for the normal project development workflow.

Where platform-specific scripts are necessary, their intended environment should be clear from the filename or documentation.

The project may be developed on a Linux-based development environment while the initial Mini Server runtime targets Windows.

Build and test automation should therefore avoid unnecessary assumptions about a single developer workstation.

## Build Integration

Where possible, scripts should delegate build, dependency, and test operations to the project's selected build tooling rather than duplicating build logic.

The authoritative build configuration should remain in the appropriate project build files.

Scripts are convenience entry points, not a second build configuration.

## Coding Agent Guidance

Coding agents may use and update scripts when required by an active task.

Before creating a new script, check whether an existing script or build command already provides the required functionality.

Do not create multiple scripts that perform the same operation without a clear reason.

When changing script behavior, keep relevant documentation and task information consistent.

## Current State

No project scripts have been implemented yet.