# Active Requirements

This directory contains requirements that are explicitly approved for implementation and belong to the current development scope.

Requirements stored here are binding for ongoing implementation work unless they are deliberately changed, superseded, released, or moved to another requirement state.

## Current Scope

Mini Server v1.1 has an approved implementation scope containing exactly:

- REQ-009 — Default Browser Launch
- REQ-010 — Configurable Start Sites

Both requirements target v1.1 and are authoritative for the approved functionality they define.

The earlier v1.1 planning notes remain historical context. The active requirements, current architecture, and approved decisions take precedence if a planning note differs.

The central overview and current requirement status are maintained in `requirements/INDEX.md`.

Concrete implementation work derived from approved requirements is maintained in `tasks/ACTIVE.md`.

## Requirement Authority

Each individual requirement document is the authoritative source for the behavior defined by that requirement.

Do not infer additional behavior from:

- Requirement filenames
- Task descriptions
- Roadmap entries
- Planning notes
- Placeholder documents
- Implementation details not supported by an approved requirement or decision

When a requirement conflicts with an implementation task, the requirement takes precedence unless the requirement itself is deliberately changed.

Architectural and technical decisions are maintained in `docs/DECISIONS.md`. The current system architecture is maintained in `docs/ARCHITECTURE.md`.

## Workflow

A requirement belongs in this directory only while it is approved for active implementation.

The normal lifecycle is:

```text
Idea
→ Backlog
→ Active
→ Implemented
→ Released
→ Archived
```

After a requirement has been fulfilled and released, it should be moved to the corresponding version directory below `requirements/archive/`.

Archived requirements preserve the historical behavior of a released version. Changes to released behavior should normally be defined through new requirements rather than by rewriting archived requirements.

## Coding Agent Guidance

Before implementing a task:

1. Read `requirements/INDEX.md`.
2. Identify the approved requirement or requirements relevant to the task.
3. Read only those active requirement documents needed for the current work.
4. Check related decisions in `docs/DECISIONS.md`.
5. Check relevant architecture in `docs/ARCHITECTURE.md`.
6. Check `tasks/ACTIVE.md` for the concrete implementation task.

If an active requirement is ambiguous or conflicts with another authoritative project document, do not silently choose an interpretation. Record or report the conflict so it can be resolved explicitly.
