# Requirements Index

This document is the central index for project requirements.

It provides a compact overview of the current requirement state without requiring all individual requirement documents to be read.

The detailed requirement descriptions are stored in the corresponding requirement directories.

---

## Requirement States

Requirements are organized into the following areas:

### Active

Location:

requirements/active/

Contains approved requirements that are part of the current implementation scope.

Active requirements should be considered binding for ongoing development.

---

### Backlog

Location:

requirements/backlog/

Contains ideas or requirements that may be implemented later but are not currently approved for implementation.

Backlog items must not be treated as active requirements.

---

### Archive

Location:

requirements/archive/

Contains requirements that were completed as part of a released version or were otherwise closed.

Archived requirements represent historical project state.

They should normally not be modified after release.

If released behavior needs to change, create a new requirement instead of rewriting the archived requirement.

---

## Active Requirements

No active requirement documents have been created yet.

The initial requirements for the first implementation will be added before development begins.

---

## Backlog Requirements

No backlog requirement documents have been created yet.

---

## Archived Requirements

No requirements have been archived yet.

---

## Requirement Naming

Requirement documents should use a stable identifier and a short descriptive name.

Recommended format:

REQ-001-short-description.md

Examples:

REQ-001-static-file-serving.md
REQ-002-dynamic-port-allocation.md
REQ-003-json-api.md

Requirement identifiers should not be reused.

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
- Related tasks
- Target release, if known

Requirements should describe what the system must provide.

Detailed implementation choices should normally remain in architecture or decision documents unless they are themselves part of the requirement.

---

## Workflow

The normal requirement lifecycle is:

Idea
→ Backlog
→ Active
→ Implemented
→ Released
→ Archived

A requirement should only be moved to Active when it has been sufficiently defined and approved for implementation.

When a release is completed, fulfilled requirements should be moved into the corresponding release archive directory.

Example:

requirements/archive/v1.0/

The requirement index should be updated whenever requirement status changes.

---

## Reading Guidance for Coding Agents

Before implementing a task:

1. Read this index.
2. Identify the relevant active requirements.
3. Read only those detailed requirement documents needed for the task.
4. Check related architecture and decision documents.
5. Do not routinely read archived requirements unless historical context is required.

This approach keeps the working context small while preserving complete project history.