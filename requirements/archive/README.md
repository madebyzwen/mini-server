# Archived Requirements

This directory contains requirements that are no longer part of the active implementation scope because they have been completed, released, superseded, or otherwise closed.

Archived requirements preserve the historical state of the project.

They should normally not be modified after they have been archived.

## Purpose

Use this directory to preserve completed or closed requirements without keeping them in the active working set.

Archived requirements provide historical context for:

- Released functionality
- Previous implementation scope
- Requirement evolution
- Superseded behavior
- Closed requirements

## Release Archives

Completed requirements should normally be grouped by release.

Example:

requirements/archive/
└── v1.0/
    ├── REQ-001-static-file-serving.md
    ├── REQ-002-dynamic-port-allocation.md
    └── ...

When a release is completed, the fulfilled active requirements for that release should be moved into the corresponding release archive directory.

## Historical Integrity

Archived requirements represent historical project state.

Do not silently rewrite archived requirements to reflect newer behavior.

If released behavior changes later:

1. Create a new requirement.
2. Reference the earlier archived requirement where useful.
3. Document the new behavior separately.
4. Preserve the archived requirement as part of project history.

## Superseded Requirements

If a requirement is superseded rather than completed normally, it should remain understandable from the archive.

Where useful, add a short note identifying the requirement or decision that replaced it.

The historical content itself should not be rewritten merely to match the replacement.

## Reading Guidance

Coding agents should not routinely read archived requirements.

Archived requirements should only be consulted when historical context is necessary, for example when:

- Investigating why existing behavior was implemented
- Comparing current behavior with a previous release
- Tracing a superseded requirement
- Debugging a regression involving older functionality

Current implementation work should normally be based on:

requirements/INDEX.md

and the relevant files in:

requirements/active/

## Naming

Archived requirement files should retain their original filenames and requirement identifiers.

Requirement identifiers must never be reused for different requirements.

## Current Archive

No requirements have been archived yet.