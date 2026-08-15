# REQ-010 — Configurable Start Sites

## Requirement ID

REQ-010

## Title

Configurable Start Sites

## Status

Active

## Purpose

Mini Server must use two selection levels to determine which existing hosted applications are automatically opened during a normal Mini Server start action:

1. Shared installation-level approval defines the applications eligible for automatic opening.
2. Private current-user selection may reduce that approved selection.

The effective selection contains only applications that currently exist, are Shared-approved, and, when a Private configuration exists, are Private-selected. Shared is always the upper bound; Private configuration must never elevate or activate an application that Shared has not approved.

Start-site configuration affects automatic browser opening only. It does not define application discovery, static serving, authentication, authorization, or access control.

## Revision Context

This active, unreleased v1.1 requirement was refined before T-016 implementation to add Private current-user filtering over the Shared installation approval list. It remains REQ-010 and does not add another v1.1 functional requirement.

## Historical Relationship

REQ-006 — Startup, Browser Launch, and Server Lifetime was released with v1.0.0 and remains unchanged under:

```text
requirements/archive/v1.0/
```

REQ-010 defines v1.1 start-site selection while preserving the released application-serving and local runtime model.

## Configuration Locations

The Shared installation-level configuration file is:

```text
<installation-root>\config\start-sites.txt
```

The Private current-user configuration file is:

```text
%APPDATA%\MiniServer\config\start-sites.txt
```

Shared configuration belongs to the installation and is shared when the physical installation is shared. Private configuration belongs to the current Windows user and can differ between users of the same installation.

Private Mini Server configuration is distinct from both:

- Private application persistence at `%APPDATA%\MiniServerData\`
- Local runtime coordination state at `%LOCALAPPDATA%\MiniServer\runtime\`

Neither start-site file may be stored in those locations.

## Distributed Default

The v1.1 distribution must contain the Shared file:

```text
config\start-sites.txt
```

with this initial active entry:

```text
example
```

The distribution must not contain a pre-created Private current-user file. The `example` application has no special hard-coded Java behavior and opens only when it is in the effective selection.

## Shared Approval

Shared configuration is authoritative for which existing applications are eligible for automatic opening. A physically valid application below `www/` that is absent from the effective Shared list is not eligible for automatic opening, regardless of Private contents.

Shared approval controls automatic start-site selection only. Application discovery and normal serving remain based on the established `www/` application model. A valid application may therefore remain directly reachable when it is not Shared-approved.

## Private Selection

Private configuration allows the current user to reduce the valid Shared selection. It cannot add an application outside Shared.

Conceptually, when the Private file exists:

```text
effective start sites
    = valid existing applications
      ∩ Shared-approved applications
      ∩ Private-selected applications
```

Private entries absent from the current valid Shared selection are ignored, even when the corresponding directory exists below `www/`.

## Configuration Format

Both files are simple UTF-8 text with one effective first-level application name per line.

Parsing and normalization follow these rules independently for each file:

- Leading and trailing whitespace is ignored.
- Empty lines are ignored.
- A trimmed line beginning with `#` is a comment and is ignored.
- Remaining entries refer only to first-level application directories below `www/`.
- Full URLs are invalid.
- Absolute filesystem paths are invalid.
- Relative or multi-level filesystem paths are invalid.
- Entries cannot supply a protocol, host, port, query, or fragment.
- `_shared` is reserved and is never a valid application start site.
- Duplicate effective entries produce only one retained entry within that file.
- The first occurrence determines the retained duplicate position within that file.

Invalid or unsafe entries are ignored and must never become arbitrary URLs or filesystem paths.

## Application Validation and Effective Selection

A Shared entry becomes Shared-approved only when the corresponding valid first-level application directory currently exists below `www/`.

Missing Shared applications:

- Are ignored
- Do not fail startup
- Do not create directories or applications
- Do not prevent other valid Shared entries from being approved

Private entries are matched only against the resulting valid Shared-approved set. Private configuration cannot create an application, approve a missing application, or bypass Shared.

Application discovery and serving remain based on actual valid first-level directories below `www/`. Adding an application below `www/` makes it available through Mini Server without making it eligible for automatic opening.

## Ordering

Shared file order is the canonical automatic-opening order. Private configuration controls inclusion only and must not reorder Shared-approved applications.

For example, if Shared contains `example`, `dashboard`, and `notes` in that order, while Private contains `notes` and then `example`, the effective order is `example` and then `notes`.

## Missing Configuration

If the Shared file does not exist:

- Mini Server remains active.
- No application is opened automatically.
- Private cannot compensate for the missing Shared file.
- Mini Server does not recreate the Shared file during normal runtime startup.

If the Private file does not exist, Mini Server uses the complete valid Shared selection in Shared order. Mini Server does not automatically create the missing Private file.

## Empty Configuration

If Shared is empty or effectively empty, Mini Server remains active and opens no application automatically. Private cannot activate anything when the valid Shared selection is empty.

If the Private file exists but contains no effective selected applications, Mini Server remains active and opens no application automatically for that user. An existing empty or effectively empty Private file is an explicit selection of none and must not fall back to the complete Shared selection.

## Unreadable Configuration

If Shared exists but cannot be read:

- An otherwise successfully running Mini Server remains active.
- No effective automatic-opening URLs are derived.
- Private cannot bypass the Shared failure.
- A concise diagnostic reports the configuration-reading problem.

If Private exists but cannot be read:

- An otherwise successfully running Mini Server remains active.
- No application is opened automatically for that start action.
- Mini Server does not fall back to the complete Shared selection.
- A concise diagnostic reports the configuration-reading problem.

The conservative Private behavior prevents unreadable configuration from presenting applications that the user may have explicitly deselected.

## Shared Changes

Removing an application from Shared removes it from every user's effective automatic-opening selection on the next normal start action, even when it remains listed in a Private file.

If Shared later re-adds the application:

- A user with no Private file receives it again as part of the complete valid Shared selection.
- A user with an existing Private file receives it only when that file still selects it.

A newly added Shared application is therefore selected by default for users without a Private file but does not enter an existing explicit Private selection automatically.

## First Start

On a first start, Mini Server:

1. Completes normal local startup.
2. Binds to `127.0.0.1` using port `0`.
3. Obtains the actual active dynamic port.
4. Confirms server readiness.
5. Publishes valid runtime state.
6. Releases `startup.lock` while retaining `instance.lock`.
7. Reads and evaluates Shared configuration.
8. Reads and evaluates Private configuration if it exists.
9. Computes the effective Private subset of the valid Shared selection.
10. Preserves Shared order.
11. Constructs local URLs using the actual active port.
12. Opens the URLs through the REQ-009 mechanism.
13. Continues server lifetime independently of browser lifetime.

## Repeated Start

On a repeated start, Mini Server:

1. Reuses the existing server and its active port.
2. Does not start another HTTP server or request another port.
3. Rereads and reevaluates current Shared configuration.
4. Rereads and reevaluates current Private configuration if it exists.
5. Recomputes the effective Private subset of the valid Shared selection.
6. Preserves Shared order.
7. Constructs URLs using the existing active port.
8. Opens the URLs through the REQ-009 mechanism.
9. Exits normally.

Both configuration files are evaluated on every normal start action. Changes therefore take effect on the next `start.bat` invocation without restarting the active HTTP server. No file watcher or active reload service is required, and neither file is automatically rewritten during normal runtime startup.

## Runtime and Persistence Boundary

Start-site evaluation must not alter:

- Runtime instance state
- Active port state
- Stop tokens
- Startup or instance locking
- Shared application persistence
- Private application persistence
- Persistence locking or atomic writes
- MiniApi behavior
- Server lifetime

Private start-site configuration is Mini Server user configuration, not application persistence or runtime coordination state.

## Acceptance Criteria

REQ-010 is fulfilled when all of the following are true:

- The v1.1 source and distribution contain the Shared `config\start-sites.txt` file.
- The distributed Shared configuration contains `example` as its initial active entry.
- The distribution does not package a pre-created Private current-user configuration file.
- No Java code gives `example` special browser-opening behavior.
- Shared configuration is read from `<installation-root>\config\start-sites.txt`.
- Private configuration is read from `%APPDATA%\MiniServer\config\start-sites.txt`.
- Private configuration is not stored in `%APPDATA%\MiniServerData\` or `%LOCALAPPDATA%\MiniServer\runtime\`.
- Both files use the same UTF-8 line-oriented syntax.
- Leading and trailing whitespace is ignored in both files.
- Empty lines and trimmed lines beginning with `#` are ignored in both files.
- Effective entries are limited to first-level application directory names below `www/`.
- Full URLs, protocols, hosts, ports, queries, and fragments are rejected or ignored.
- Absolute, relative, and multi-level filesystem paths are rejected or ignored.
- `_shared` is never accepted as an application start site.
- Invalid or unsafe entries never become arbitrary URLs or filesystem paths.
- Duplicates within either file retain only their first occurrence.
- A Shared entry becomes approved only when its valid first-level application directory currently exists below `www/`.
- Missing applications are ignored without failing startup, creating directories, or blocking other valid Shared entries.
- Effective start sites are the valid existing applications in the Shared-approved set and, when Private exists, in the Private-selected set.
- Private never enables an application outside the current valid Shared selection.
- Shared order is canonical, and Private inclusion cannot reorder it.
- A missing Shared file leaves the server active and opens no application automatically.
- A missing Shared file is not recreated during normal runtime startup.
- Empty or effectively empty Shared configuration leaves the server active and opens no application automatically.
- Private cannot compensate for missing, empty, effectively empty, or unreadable Shared configuration.
- A missing Private file selects the complete valid Shared selection without creating a Private file.
- An existing empty or effectively empty Private file opens no application and does not fall back to Shared.
- An unreadable Shared file leaves the server active, opens no application automatically, and produces a concise diagnostic.
- An unreadable Private file leaves the server active, opens no application automatically rather than falling back to Shared, and produces a concise diagnostic.
- Removing an application from Shared removes its effective eligibility on the next normal start even when a stale Private entry remains.
- Re-adding a Shared application selects it for users without a Private file and selects it for users with a Private file only when that file contains it.
- First-start evaluation occurs only after readiness, active-port discovery, valid runtime-state publication, and release of `startup.lock` while retaining `instance.lock`.
- First-start URLs use the newly assigned actual active port.
- A repeated start reuses the existing server and port without starting another server or requesting another port.
- Both files are reread and the effective selection is recomputed on every normal start action, including repeated starts.
- Changes to either file while the server is active take effect on the next normal start action without a server restart.
- Neither configuration file is automatically rewritten during normal runtime startup.
- Application discovery and serving remain independent of Shared approval and Private selection.
- Applications absent from the effective selection remain available through normal Mini Server serving.
- Resulting URLs are passed in Shared order to the browser-opening mechanism defined by REQ-009.
- Start-site evaluation does not alter runtime state, active ports, stop tokens, locking, persistence, MiniApi behavior, or server lifetime.
- The implementation and parser remain compatible with Java 8.

## Constraints

The files are intentionally simple UTF-8 line-oriented configuration. They are not JSON, XML, YAML, key/value configuration, or a general-purpose settings framework.

No runtime file watcher or active-server configuration reload service is required. Evaluation on every normal start action is sufficient.

## Related Decisions

- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-014 — Not Intended for Public Internet Use
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch
- D-027 — Shared and Private Start-Site Selection

## Related Requirements

- REQ-006 — Startup, Browser Launch, and Server Lifetime (released with v1.0.0)
- REQ-009 — Default Browser Launch

## Related Architecture

See `docs/ARCHITECTURE.md`, especially:

- Storage and Runtime Boundaries
- Start-Site Configuration
- Startup and Browser Launch

## Related Tasks

- T-016 — Implement Configurable Start Sites
- T-017 — Verify v1.1 Release Scope

## Target Release

v1.1
