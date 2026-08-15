# REQ-010 — Configurable Start Sites

## Requirement ID

REQ-010

## Title

Configurable Start Sites

## Status

Active

## Purpose

Mini Server must allow an installation to define which existing hosted applications are automatically opened during a Mini Server start action.

The configuration affects browser opening only. It does not define which applications exist or are served.

## Historical Relationship

REQ-006 — Startup, Browser Launch, and Server Lifetime was released with v1.0.0 and remains unchanged under:

```text
requirements/archive/v1.0/
```

REQ-010 defines v1.1 start-site selection while preserving the released application-serving and local runtime model.

## Configuration Location

The installation-level configuration file is:

```text
<installation-root>/config/start-sites.txt
```

The file belongs to the installation. Users of the same physical shared or network installation therefore share the same start-site configuration.

It is not stored in `%LOCALAPPDATA%`, `%APPDATA%`, runtime state, or application persistence.

## Distributed Default

The v1.1 distribution must contain:

```text
config/start-sites.txt
```

with this initial active entry:

```text
example
```

The `example` application has no special hard-coded Java behavior. It opens only because the distributed configuration lists it.

## Configuration Format

The configuration is UTF-8 text with one effective first-level application directory name per line.

Parsing and normalization follow these rules:

- Leading and trailing whitespace is ignored.
- Empty lines are ignored.
- A trimmed line beginning with `#` is a comment and is ignored.
- Remaining entries refer only to first-level application directories below `www/`.
- Full URLs are invalid.
- Absolute filesystem paths are invalid.
- Relative or multi-level filesystem paths are invalid.
- Entries cannot supply a protocol, host, port, query, or fragment.
- `_shared` is reserved and is never a valid application start site.
- Duplicate effective entries produce only one opening request.
- The first occurrence determines the retained duplicate position.
- Remaining valid entries preserve file order.

Invalid or unsafe entries are ignored and must never become arbitrary URLs or filesystem paths.

## Application Validation

An entry is eligible only when the corresponding valid first-level application directory currently exists below `www/`.

Missing configured applications:

- Are ignored
- Do not fail startup
- Do not create directories or applications
- Do not prevent other valid entries from opening

Application discovery and serving remain based on actual valid first-level directories below `www/`. The configuration selects only which existing applications are opened automatically.

An application remains available when absent from `start-sites.txt`. Adding an application below `www/` requires no configuration change unless it should also open automatically.

## Missing Configuration

If `config/start-sites.txt` does not exist:

- Mini Server starts normally.
- No application is opened automatically.
- Missing configuration is not a server-startup failure.
- Mini Server does not recreate the file automatically during normal runtime startup.

## Empty Configuration

If no effective valid application entries remain, Mini Server starts normally and opens no application automatically.

An intentionally empty file therefore disables automatic application opening.

## Unreadable Configuration

If the file exists but cannot be read, an otherwise successfully running Mini Server remains active.

No URLs are derived from unreadable content. A concise diagnostic should report the configuration-reading problem without unnecessarily exposing application data.

## First Start

On a first start, Mini Server:

1. Completes normal server startup.
2. Obtains the actual active dynamic port.
3. Confirms server readiness.
4. Publishes valid runtime state.
5. Reads `start-sites.txt`.
6. Normalizes and validates entries.
7. Ignores invalid, reserved, duplicate, or missing applications.
8. Constructs URLs for the remaining applications using the actual active port.
9. Opens the URLs through the REQ-009 mechanism.

## Repeated Start

On a repeated start, Mini Server:

1. Reuses the existing server and its active port.
2. Does not start another HTTP server or request another port.
3. Rereads `start-sites.txt` from the installation.
4. Evaluates the current file using the same rules as a first start.
5. Constructs URLs using the existing active port.
6. Opens the URLs through the REQ-009 mechanism.
7. Exits normally.

The configuration is reread on every normal start action. Changing `start-sites.txt` while Mini Server is already running therefore takes effect on the next `start.bat` invocation without restarting the active server.

## Runtime and Persistence Boundary

The start-site configuration must not alter:

- Runtime instance state
- Active port state
- Stop tokens
- Runtime locking
- Shared persistence
- Private persistence
- Persistence locking or atomic writes

## Acceptance Criteria

REQ-010 is fulfilled when all of the following are true:

- The v1.1 source and distribution contain `config/start-sites.txt`.
- The distributed configuration contains `example` as its initial active entry.
- No Java code gives `example` special browser-opening behavior.
- The configuration belongs to the installation and is shared when the physical installation is shared.
- The configuration is not stored in local runtime state, user-profile persistence, or application persistence.
- The file is read as UTF-8 text.
- Leading and trailing whitespace is ignored.
- Empty lines are ignored.
- Lines beginning with `#` after trimming are treated as comments.
- Effective entries are limited to first-level application directory names below `www/`.
- Full URLs, protocols, hosts, ports, queries, and fragments are rejected or ignored.
- Absolute, relative, and multi-level filesystem paths are rejected or ignored.
- `_shared` is never accepted as an application start site.
- Invalid or unsafe entries never become arbitrary URLs or filesystem paths.
- Duplicate effective entries produce one opening request, retaining the first occurrence's position.
- Remaining valid entries preserve configuration-file order.
- An entry is selected only when its valid first-level application directory currently exists below `www/`.
- Missing applications are ignored without failing startup, creating directories, or blocking other valid entries.
- A missing configuration file allows the server to run and results in no automatic application opening.
- A missing configuration file is not recreated automatically during normal runtime startup.
- An empty or effectively empty configuration allows the server to run and results in no automatic application opening.
- An unreadable configuration leaves an otherwise successful server active, derives no URLs from unreadable content, and produces a concise diagnostic.
- First-start evaluation occurs only after readiness, active-port discovery, and valid runtime-state publication.
- First-start URLs use the newly assigned actual active port.
- A repeated start reuses the existing server and port without starting another server or requesting another port.
- A repeated start rereads and reevaluates the current installation configuration.
- Configuration changes made while the server is active take effect on the next normal start action without a server restart.
- Application discovery and serving remain independent of `start-sites.txt`.
- Applications absent from the configuration remain available through Mini Server.
- Resulting URLs are passed in retained order to the browser-opening mechanism defined by REQ-009.
- Start-site evaluation does not alter runtime state, stop tokens, locking, or persistence behavior.
- The implementation and parser remain compatible with Java 8.

## Constraints

The file is intentionally simple UTF-8 line-oriented configuration. It is not JSON, XML, YAML, key/value configuration, or a general-purpose settings framework.

No runtime file watcher or active-server configuration reload service is required. Evaluation on every normal start action is sufficient.

## Related Decisions

- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-014 — Not Intended for Public Internet Use
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch
- D-026 — Shared Installation Start-Site Configuration

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
