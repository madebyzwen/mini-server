# Agent Instructions

## Purpose

This file defines how Codex and other coding agents should work with this repository.

These instructions describe the project workflow and documentation rules. They do not define the application's technical architecture unless explicitly stated.

## General Rules

- Use English for source code, comments, filenames, repository content, and primary documentation.
- Keep project information in the designated authoritative document instead of duplicating the same information across multiple files.
- If a project fact changes, update its authoritative source first.
- Do not infer project decisions from missing or placeholder content.
- Do not treat an empty or placeholder document as approval to make an architectural or functional decision.
- When a requirement or decision is unclear, preserve the ambiguity instead of silently choosing an implementation.
- Prefer small, understandable changes over broad refactoring unless a task explicitly requires restructuring.
- Do not modify unrelated parts of the project while implementing a task.
- Preserve backwards compatibility unless a requirement explicitly permits breaking changes.

## Authoritative Project Documents

Use the following documents for their respective purposes:

- `README.md`
  - Public project overview
  - Setup and usage documentation
  - Developer-facing introduction
  - English is the primary language
  - German documentation should also be included in the same README

- `docs/ARCHITECTURE.md`
  - Current authoritative system architecture
  - Component boundaries
  - Runtime structure
  - Important technical relationships

- `docs/DECISIONS.md`
  - Deliberate technical and architectural decisions
  - Reasons for those decisions
  - Constraints that must be preserved until explicitly changed

- `docs/PROJECT_NOTES.md`
  - Working knowledge
  - Useful discoveries
  - Observations that may help future development
  - Information that does not yet qualify as a formal decision or requirement

- `docs/DEBUGGING.md`
  - Known problems
  - Reproduction information
  - Root causes
  - Verified fixes and diagnostic procedures

- `docs/ROADMAP.md`
  - Possible future development
  - Ideas that are not currently approved requirements

- `requirements/INDEX.md`
  - Central index of project requirements
  - Status and location of requirement documents

- `requirements/active/`
  - Requirements currently approved for implementation

- `requirements/backlog/`
  - Proposed, deferred, or not-yet-approved requirements

- `requirements/archive/`
  - Requirements completed as part of released versions

- `tasks/ACTIVE.md`
  - Current implementation work
  - Concrete tasks derived from active requirements

- `releases/`
  - Release-specific scope and release documentation

- `CHANGELOG.md`
  - User-visible and technically relevant changes between released versions

## Reading Strategy

Before implementing a task:

1. Read this `AGENTS.md`.
2. Read `requirements/INDEX.md`.
3. Read only the active requirement documents relevant to the task.
4. Read the relevant parts of `docs/ARCHITECTURE.md`.
5. Read relevant entries from `docs/DECISIONS.md`.
6. Read `tasks/ACTIVE.md` if the task is already tracked there.
7. Consult `docs/PROJECT_NOTES.md` and `docs/DEBUGGING.md` only when relevant.

Do not routinely reread archived requirements unless the current task depends on historical behavior or a previous release decision.

## Requirements Workflow

- New approved functionality belongs in `requirements/active/`.
- Ideas that are not yet approved belong in `requirements/backlog/`.
- Concrete implementation work may be tracked in `tasks/ACTIVE.md`.
- Tasks should reference the requirement that created them when applicable.
- When a release is completed, fulfilled requirements should be moved to the appropriate version directory under `requirements/archive/`.
- Archived requirements remain historical records and should not normally drive new implementation work.
- New changes to previously released behavior should be expressed as new requirements instead of modifying archived requirements.

## Decisions Workflow

When an implementation requires a meaningful architectural or technical decision:

- Check whether the decision has already been made in `docs/DECISIONS.md`.
- If it has, follow the existing decision.
- If it has not, do not silently create a permanent project rule.
- Record newly approved decisions in `docs/DECISIONS.md`.
- Avoid copying the full decision into other documents. Reference the authoritative decision instead when needed.

## Notes and Learning

During implementation, record useful project-specific discoveries in `docs/PROJECT_NOTES.md` when they may help future work.

Examples include:

- Non-obvious runtime behavior
- Important limitations
- Tooling observations
- Compatibility findings
- Unexpected interactions between components

Do not use project notes as a substitute for requirements or architectural decisions.

## Debugging

When a defect is investigated and the root cause or a reliable diagnostic procedure is discovered, document it in `docs/DEBUGGING.md` if it is likely to be useful again.

A debugging entry should distinguish between:

- Symptoms
- Cause
- Verification
- Fix
- Remaining limitations

Do not record unverified guesses as established causes.

## Code and Documentation

- Keep implementation and documentation consistent.
- Update documentation when a change makes existing documentation incorrect.
- Do not duplicate project constants or constraints across multiple documentation files when a single authoritative source can be referenced.
- Generated files should not be manually edited unless explicitly required.
- Do not commit temporary files, build outputs, IDE state, credentials, secrets, or machine-specific configuration unless the project explicitly requires them.

## Git Workflow

- Keep commits focused and understandable.
- Do not rewrite published history unless explicitly requested.
- Do not commit secrets or credentials.
- Before committing, review the actual diff.
- Commit messages should describe the purpose of the change.
- Do not create releases or version tags unless explicitly requested.

## Release Workflow

- If the user asks to create or publish a release and explicitly specifies patch, minor, or major, use that release type.
- If the user asks to create or publish a release without specifying its type, ask exactly one clarifying question: "Which release type should be created: Patch, Minor, or Major?"
- Do not infer the release type from recent changes and do not choose it automatically.
- Once the release type is known, dispatch `.github/workflows/release.yml` on `main` with the corresponding `release_type`.
- Real user-requested publication uses `dry_run=false`; do not ask the user about dry-run mode during normal publication.
- Do not manually edit the release version, locally create the authoritative release ZIP, create the tag, or manually upload release assets as a substitute for the workflow.
- Monitor the GitHub Actions run and do not report success until it completes successfully and the GitHub Release exists.
- If the workflow fails, report the failure and relevant workflow/job information. Do not silently publish manually.
- After success, report the previous released version, release type, new version, successful test/build status, release artifact name, and GitHub Release/tag.

## Testing

- Run the tests relevant to a change before considering the task complete.
- Add or update tests when behavior changes and automated testing is practical.
- Do not claim that something was tested unless the corresponding test or verification was actually performed.
- Record known untested areas or limitations when relevant.

## Placeholders

Some project files may initially contain placeholder content.

A placeholder means:

- the document exists intentionally;
- its subject has not yet been defined;
- no decision should be inferred from the absence of content.

Do not remove placeholder status until meaningful project-specific content has been added.
