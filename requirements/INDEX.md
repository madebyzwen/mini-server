# Requirements Index

This document is the central index for project requirements.

It provides a compact overview of the current requirement state without requiring all individual requirement documents to be read. Detailed requirement descriptions are stored in the corresponding requirement directories.

---

## Requirement States

Requirements are organized into the following areas:

### Active

Location:

```text
requirements/active/
```

Contains approved requirements that are part of the current implementation scope. Active requirements are binding for ongoing development.

### Backlog

Location:

```text
requirements/backlog/
```

Contains ideas or requirements that may be implemented later but are not currently approved. Backlog items must not be treated as active requirements.

### Archive

Location:

```text
requirements/archive/
```

Contains requirements completed as part of a released version or otherwise closed. Archived requirements preserve historical project state and should normally not be modified after release.

If released behavior needs to change, create a new requirement instead of rewriting an archived requirement.

---

## Active Requirements

The following requirements are approved for Mini Server v1.1 implementation:

- REQ-009 — Default Browser Launch
  - `active/REQ-009-default-browser-launch.md`
  - Target release: v1.1
- REQ-010 — Configurable Start Sites
  - `active/REQ-010-configurable-start-sites.md`
  - Target release: v1.1
- REQ-011 — Unified Current-User Storage
  - `active/REQ-011-unified-current-user-storage.md`
  - Target release: v1.1

These three active requirements define the complete currently approved v1.1 functional scope. No other v1.1 feature is approved by this index.

---

## Backlog Requirements

No backlog requirement documents have been created yet.

---

## Archived Requirements

REQ-001 through REQ-008 were released as Mini Server v1.0.0 and are archived in `requirements/archive/v1.0/`:

- REQ-001 — Static File Serving
  - `archive/v1.0/REQ-001-static-file-serving.md`
- REQ-002 — Dynamic Port Allocation
  - `archive/v1.0/REQ-002-dynamic-port-allocation.md`
- REQ-003 — JSON Persistence API
  - `archive/v1.0/REQ-003-json-api.md`
- REQ-004 — JavaScript Client API
  - `archive/v1.0/REQ-004-javascript-client-api.md`
- REQ-005 — Example Application and Template Package
  - `archive/v1.0/REQ-005-example-and-template.md`
- REQ-006 — Startup, Browser Launch, and Server Lifetime
  - `archive/v1.0/REQ-006-startup-and-browser-launch.md`
- REQ-007 — Error Handling and File Permissions
  - `archive/v1.0/REQ-007-error-handling-and-file-permissions.md`
- REQ-008 — Runtime and Distribution
  - `archive/v1.0/REQ-008-runtime-and-distribution.md`

The future v1.1 archive placeholder is `requirements/archive/v1.1/`. Active v1.1 requirements must not move there until v1.1 is actually released.

---

## Requirement Naming

Requirement documents use a stable identifier and a short descriptive name.

Format:

```text
REQ-<number>-<short-description>.md
```

Examples:

```text
REQ-001-static-file-serving.md
REQ-002-dynamic-port-allocation.md
REQ-003-json-api.md
```

Requirement identifiers must never be reused.

---

## Requirement Document Structure

Each requirement should contain at least:

- Requirement ID
- Title
- Status
- Purpose
- Description
- Acceptance criteria
- Relevant constraints
- Related decisions
- Related tasks or a reference to the task tracker
- Target release

Requirements describe what the system must provide. Detailed implementation choices should normally remain in architecture or decision documents unless they are themselves part of the requirement.

---

## Workflow

The normal requirement lifecycle is:

```text
Idea
→ Backlog
→ Active
→ Implemented
→ Released
→ Archived
```

A requirement should only be moved to Active when it has been sufficiently defined and approved for implementation.

When a release is completed, fulfilled requirements should be moved into the corresponding release archive directory. This index must be updated whenever the status or location of a requirement changes.

---

## Reading Guidance for Coding Agents

Before implementing a task:

1. Read this index.
2. Identify the relevant active requirements.
3. Read only the detailed requirement documents required for the task.
4. Check the related architecture and decision documents.
5. Check `tasks/ACTIVE.md` for the concrete implementation task.
6. Do not routinely read archived requirements unless historical context is required.

This approach keeps the working context small while preserving complete project history.
