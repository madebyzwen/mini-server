# REQ-011 — Unified Current-User Storage

## Requirement ID

REQ-011

## Title

Unified Current-User Storage

## Status

Active

## Purpose

Mini Server must use one canonical roaming current-user root while keeping Mini Server configuration and Private application persistence in separate subdirectories. Mini Server v1.1 must safely transition Private data released at the v1.0 location without silent data loss.

## Canonical Current-User Hierarchy

The canonical roaming current-user Mini Server root is:

```text
%APPDATA%\MiniServer\
```

Within it, responsibilities are separated with canonical directory casing:

```text
%APPDATA%\MiniServer\Config\
%APPDATA%\MiniServer\Data\
```

`Config` contains current-user Mini Server configuration. `Data` contains current-user application persistence.

The canonical start-site configuration is:

```text
%APPDATA%\MiniServer\Config\start-sites.txt
```

The canonical Private application persistence file for a valid site is:

```text
%APPDATA%\MiniServer\Data\<site>\data.json
```

There is no redundant `data` directory below the Private site directory. For example, the `example` site uses `%APPDATA%\MiniServer\Data\example\data.json`.

## Unchanged Storage Boundaries

Shared application persistence remains:

```text
<installation-root>\www\<site>\data\data.json
```

Local transient runtime coordination remains:

```text
%LOCALAPPDATA%\MiniServer\runtime\
```

Shared persistence below `www`, roaming current-user configuration, roaming current-user application data, and local transient runtime state remain separate concerns. `private` continues to mean current-user profile storage, not authentication, authorization, encryption, or isolation between mutually hostile applications.

## Private Persistence Mapping

For every valid first-level application site, the persistence API continues to derive the site and explicit `private` or `shared` scope from the request URL. Clients cannot supply or override a filesystem path.

New Private reads and modifying operations use only `%APPDATA%\MiniServer\Data\<site>\data.json` after any required compatibility migration. A missing new and legacy file represents a not-yet-created Private store at the new canonical location. Existing JSON object, section, locking, atomic-write, and API response semantics remain unchanged.

## v1.0 Compatibility Migration

Mini Server v1.0.0 released Private persistence at:

```text
%APPDATA%\MiniServerData\<site>\data\data.json
```

For each valid site's Private scope, v1.1 applies this transition before normal Private persistence use:

1. If `%APPDATA%\MiniServer\Data\<site>\data.json` exists, it is authoritative. A legacy file must not overwrite, merge into, or otherwise change it.
2. If the canonical file is absent and the legacy v1.0 file exists, Mini Server migrates the legacy file to the canonical location before completing the requested Private operation.
3. If neither file exists, normal Private persistence uses the canonical location and creates it only when the established API operation requires creation.

Migration preserves the existing file content and JSON data without resetting, truncating, or reinterpreting it. Normal validation still reports invalid stored JSON or a non-object root rather than silently replacing it.

## Migration Safety and Completion

Migration follows the established bounded-locking and atomic-replacement integrity principles for persistence:

- The canonical site directory is created only inside `%APPDATA%\MiniServer\Data\` for a validated existing application site.
- Concurrent attempts are coordinated so a canonical file established by another operation is never overwritten by legacy data.
- The canonical file is established completely and safely before the legacy file is removed.
- A failed migration leaves the legacy file intact and does not report the Private operation as successful.
- Failure must not silently destroy, truncate, overwrite, or partially replace legacy data.
- After successful canonical establishment, the legacy file may be removed.
- Empty legacy `data`, site, and `%APPDATA%\MiniServerData` directories may then be removed on a best-effort basis.
- Failure to remove an obsolete empty directory is nonfatal and does not invalidate successfully migrated canonical data.

There is no permanent alias, merge, fallback, or dual-write model. Once the canonical file exists, all later Private operations use only it, regardless of whether a legacy file remains.

## Compatibility Boundary

This v1.1 transition changes only the current-user directory layout and the derived Private persistence location. It must not change:

- Shared persistence location or contents
- Persistence API routes, scopes, JSON model, or Section semantics
- Site validation or the prohibition on arbitrary client-supplied paths
- Static protection of shared persistence
- Persistence locking and atomic-write guarantees
- MiniApi behavior
- Loopback binding or dynamic port allocation
- Runtime state, stop token, startup lock, or instance lock
- Server lifetime

The implementation must remain compatible with Java 8.

## Acceptance Criteria

REQ-011 is fulfilled when all of the following are true:

- `%APPDATA%\MiniServer\` is the canonical roaming current-user Mini Server root.
- Newly created directories use canonical `MiniServer`, `Config`, and `Data` casing.
- Current-user start-site configuration uses `%APPDATA%\MiniServer\Config\start-sites.txt`.
- Private application persistence uses `%APPDATA%\MiniServer\Data\<site>\data.json`.
- No redundant Private `<site>\data\data.json` layer is created below the new `Data` root.
- Shared persistence remains `<installation-root>\www\<site>\data\data.json`.
- Local runtime state remains `%LOCALAPPDATA%\MiniServer\runtime\`.
- Configuration, Private application data, local runtime state, and Shared persistence remain distinct responsibilities.
- Site and scope continue to be server-derived, and clients cannot provide arbitrary persistence paths.
- The released `%APPDATA%\MiniServerData\<site>\data\data.json` path is treated only as a v1.0 migration source.
- An existing canonical file always takes precedence and is never overwritten or merged with legacy data.
- When canonical is absent and legacy exists, migration occurs before normal Private persistence use.
- Migration preserves existing content and JSON data and follows established locking and atomic-write integrity principles.
- Concurrent migration cannot overwrite a canonical file established by another operation.
- Legacy data is removed only after the canonical file has been safely established.
- Failed migration leaves legacy data intact, fails cleanly, and does not silently destroy, truncate, overwrite, or report success.
- Empty legacy directories are removed only best-effort, and cleanup failure does not invalidate successful migration.
- There is no permanent dual write, merge, alias, or legacy fallback after the canonical file exists.
- If neither file exists, established Private persistence behavior uses the new canonical location.
- Existing JSON model, API, locking, atomic-write, serving-protection, and error behavior regressions continue to pass.
- Shared persistence and local runtime behavior remain unchanged.
- Production code and tests remain compatible with Java 8.

## Constraints

This is a bounded v1.1 filesystem compatibility transition, not a general migration framework or versioned storage subsystem.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-008 — Application Site and Persistence Scope Are Derived from the URL
- D-015 — Persistence Data Is Not Served as Static Content
- D-016 — Application Separation Is Namespace Isolation, Not Authentication
- D-021 — Explicit Shared and Private Persistence Scopes
- D-022 — Explicitly Scoped Persistence API Contract
- D-023 — Concurrency-Safe Persistence Writes
- D-028 — Unified Current-User Mini Server Storage

## Related Requirements

- REQ-003 — JSON Persistence API (released with v1.0.0)
- REQ-010 — Configurable Start Sites

## Related Architecture

See `docs/ARCHITECTURE.md`, especially:

- Storage and Runtime Boundaries
- Private Persistence Migration
- Central Persistence API
- Persistence Concurrency

## Related Tasks

- T-018 — Implement Unified User Storage and Start-Site Selection UX
- T-017 — Verify v1.1 Release Scope

## Target Release

v1.1
