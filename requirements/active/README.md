# Active Requirements

This directory contains requirements that are approved for implementation and belong to the current development scope.

Requirements stored here are binding for ongoing implementation work unless they are explicitly changed, superseded, or moved to another requirement state.

## Current Scope

The active requirements currently define the scope of the initial v1.0 release.

The following requirements are active:

- REQ-001 — Static File Serving
- REQ-002 — Dynamic Port Allocation
- REQ-003 — JSON Persistence API
- REQ-004 — JavaScript Client API
- REQ-005 — Example Application and Template Package
- REQ-006 — Startup and Browser Launch
- REQ-007 — Error Handling and File Permissions
- REQ-008 — Runtime and Distribution

The central overview and current requirement status are maintained in:

requirements/INDEX.md

Concrete implementation work derived from these requirements is maintained in:

tasks/ACTIVE.md

## Requirement Authority

Each individual requirement document is the authoritative source for the behavior defined by that requirement.

Do not infer additional behavior from:

- Requirement filenames
- Task descriptions
- Roadmap entries
- Placeholder documents
- Implementation details that are not supported by an approved requirement or decision

When a requirement conflicts with an implementation task, the requirement takes precedence unless the requirement itself is deliberately changed.

Architectural and technical decisions referenced by requirements are maintained in:

docs/DECISIONS.md

The current system architecture is maintained in:

docs/ARCHITECTURE.md

## Workflow

A requirement belongs in this directory only while it is approved for active implementation.

The normal lifecycle is:

Idea
→ Backlog
→ Active
→ Implemented
→ Released
→ Archived

After a requirement has been fulfilled and released, it should be moved to the corresponding version directory below:

requirements/archive/

For version 1.0, the intended archive location is:

requirements/archive/v1.0/

Archived requirements should preserve the historical behavior of the released version.

Changes to released behavior should normally be defined through new requirements rather than by rewriting archived requirements.

## Coding Agent Guidance

Before implementing a task:

1. Read requirements/INDEX.md.
2. Identify the requirement or requirements relevant to the task.
3. Read only those active requirement documents needed for the current work.
4. Check related decisions in docs/DECISIONS.md.
5. Check relevant architecture in docs/ARCHITECTURE.md.
6. Check tasks/ACTIVE.md for the concrete implementation task.

Do not routinely read every active requirement when the current task only depends on a subset of them.

If an active requirement is ambiguous or conflicts with another authoritative project document, do not silently choose an interpretation. Record or report the conflict so it can be resolved explicitly.
