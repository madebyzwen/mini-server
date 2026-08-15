# REQ-010 — Configurable Start Sites

## Requirement ID

REQ-010

## Title

Configurable Start Sites

## Status

Active

## Purpose

Mini Server must use Shared installation approval and Private current-user selection to determine which hosted applications are opened automatically during a normal start action.

Shared defines the current upper bound and canonical order. Private may reduce that set, but it must never elevate an application outside Shared or reorder Shared-approved applications.

When a user has no Private selection, Mini Server must initialize one from the current valid Shared selection and open a built-in welcome and selection page instead of opening applications. The same page provides an intentional replacement-selection workflow whenever the user later visits the Mini Server root.

Start-site configuration controls automatic browser opening only. It does not define application discovery, static serving, authentication, authorization, or access control.

## Revision Context

This active, unreleased v1.1 requirement supersedes its earlier missing-Private behavior before release. T-016 implemented the earlier Shared/Private intersection, while T-018 is planned to implement the approved initialization and selection experience defined here.

## Historical Relationship

REQ-006 — Startup, Browser Launch, and Server Lifetime was released with v1.0.0 and remains unchanged under `requirements/archive/v1.0/`.

REQ-010 defines the current v1.1 start-site behavior while preserving the released loopback, dynamic-port, application-serving, and local-runtime model.

## Configuration Locations

The Shared installation-level configuration file is:

```text
<installation-root>\config\start-sites.txt
```

The canonical Private current-user configuration file is:

```text
%APPDATA%\MiniServer\Config\start-sites.txt
```

Canonical casing for newly created directories is `MiniServer\Config`.

Shared configuration belongs to the installation and is shared when the physical installation is shared. Private configuration belongs to the current Windows user and can differ between users of the same installation.

Private Mini Server configuration is distinct from:

- Private application persistence at `%APPDATA%\MiniServer\Data\<site>\data.json`
- Local runtime coordination at `%LOCALAPPDATA%\MiniServer\runtime\`
- Shared installation configuration at `<installation-root>\config\start-sites.txt`

## Distributed Default

The v1.1 distribution must contain the Shared `config\start-sites.txt` file with this initial active entry:

```text
example
```

The distribution must not contain a pre-created Private current-user file. The `example` application has no special hard-coded Java behavior and is eligible only because the valid Shared configuration lists it.

## Configuration Format and Validation

Both files are simple UTF-8 text with one effective first-level application name per line.

Parsing and normalization follow these rules independently for each file:

- Leading and trailing whitespace is ignored.
- Empty lines are ignored.
- A trimmed line beginning with `#` is a comment and is ignored.
- Remaining entries refer only to first-level application directories below `www/`.
- Full URLs are invalid.
- Absolute, relative, and multi-level filesystem paths are invalid.
- Entries cannot supply a protocol, host, port, query, or fragment.
- `_shared` is reserved and is never a valid application start site.
- Duplicate effective entries retain only their first occurrence within that file.

Invalid or unsafe entries are ignored and must never become arbitrary URLs or filesystem paths.

A Shared entry becomes currently approved only when the corresponding safe first-level application directory currently exists below `www/`. A missing application is ignored without failing startup or creating a directory. Shared approval defines the upper bound and canonical order for every initialization, automatic-opening evaluation, root-page choice list, and save operation.

## Missing Private Initialization

After a normal start action has an active ready server, the actual port is known, and valid runtime state is available, Mini Server checks the canonical Private file.

When the Private file does not exist and Shared can be read and validated:

1. Mini Server resolves the current valid normalized Shared-approved applications.
2. It creates `%APPDATA%\MiniServer\Config\` when required.
3. It safely writes a new UTF-8 Private `start-sites.txt` containing only those canonical application names in Shared order.
4. It does not copy comments, invalid entries, duplicates, or entries for missing applications from Shared.
5. A readable Shared file with no valid applications produces an existing empty Private file.
6. On that initialization start, Mini Server opens only `http://127.0.0.1:<active-port>/` through the REQ-009 Windows-default-browser mechanism.
7. It does not automatically open any application URL on that same start action.

Initialization applies to a first or repeated normal start action that finds the Private file absent. Once the Private file exists, it is not initialized or rewritten by normal start evaluation.

If creation of the Private file fails, Mini Server remains active, reports a concise diagnostic, does not claim successful initialization, and opens the root page if practical.

## Shared Failure During Initialization

When Private is missing but current Shared configuration is missing, unreadable, or otherwise cannot be resolved:

- Mini Server remains active.
- No Private file is fabricated from unavailable Shared state.
- No application is opened automatically.
- The root welcome page is opened if practical.
- The page explains that applications cannot currently be selected because Shared configuration is unavailable.
- Saving is unavailable until Shared can be read and validated.
- A later normal start retries initialization because Private is still absent.

A readable and valid but empty Shared selection is not a Shared failure. Mini Server may create an empty Private file, and the root page reports that no applications are currently available.

## Built-In Root Welcome Page

`GET /` is reserved for a Mini Server-owned welcome and start-site selection page. It is not a directory listing, hosted application, persistence API page, normal file below `www`, or authentication/authorization interface.

The runtime page is English only and has the main visible heading:

```text
Welcome to Mini Server
```

It explains that the user can choose which applications Mini Server opens automatically on future normal start actions. It also explains that saving creates a new selection and completely replaces the existing personal selection.

The page must be a deliberate, pleasant setup experience with:

- A clean modern and responsive layout suitable for normal desktop browser sizes
- Clear application choices, preferably presented as checkbox rows or cards
- All currently available choices initially selected
- A clear primary `Save selection` action
- Clear success feedback after saving
- Useful concise error feedback when loading or saving fails
- The advanced-user path `%APPDATA%\MiniServer\Config\start-sites.txt`
- Self-contained HTML, CSS, and JavaScript
- No CDN, external font, analytics, tracking, internet dependency, external asset, service, or UI framework requirement

## Root-Page Choice Source

Every `GET /` rereads and validates current Shared configuration using the same rules as normal start evaluation.

The page:

- Includes only current valid Shared-approved first-level applications.
- Preserves Shared order.
- Never offers `_shared`, unsafe entries, invalid entries, missing applications, or physical applications absent from Shared.
- Initially checks every currently available Shared-approved application.
- Does not read the Private `start-sites.txt` file for checkbox state or any other page-choice input.

The checkbox state is therefore a fresh replacement selection, not a representation of the currently saved Private selection. The page must not claim otherwise.

When Shared is readable but its valid selection is empty, the page explains that no applications are currently available and still permits saving an empty selection. When Shared cannot be read or validated, the page explains the problem and disables or omits the save action.

## Saving a Replacement Selection

The canonical internal save route is:

```text
POST /__miniserver/start-sites
```

The route accepts `application/json` with exactly one `sites` member whose value is an array of application-name strings, for example:

```json
{
  "sites": ["example", "notes"]
}
```

A malformed JSON body, wrong content type, wrong top-level structure, additional top-level members, or non-string array entry is rejected without changing the Private file.

For a structurally valid request, Mini Server:

1. Rereads and revalidates current Shared configuration at save time.
2. Refuses the save without changing Private when Shared is unavailable or cannot be validated.
3. Treats the submitted names only as requested membership, never as authoritative paths or URLs.
4. Discards submitted names outside the current valid Shared-approved set, including unsafe, reserved, missing, and unapproved names.
5. Deduplicates requested membership and emits accepted names in current Shared order regardless of submitted order.
6. Replaces the complete canonical Private selection with that normalized result.
7. Does not merge with or read the previous Private file to determine the result.
8. Allows an empty `sites` array and writes an existing empty Private file.
9. Writes the Private file safely as UTF-8 using integrity and atomic-replacement principles appropriate to a configuration file.
10. Returns clear success information that the page can display; failure leaves a clear concise error and must not be presented as success.

The request cannot select a target file, filesystem path, URL, protocol, host, port, query, or fragment. The endpoint writes only `%APPDATA%\MiniServer\Config\start-sites.txt`; it cannot write Shared configuration, application persistence, runtime state, or any arbitrary file.

The route is available only through the existing loopback-bound server, is handled as Mini Server infrastructure before application/static routing, and is not a general settings or persistence API. It adds no CORS support and requires the intended JSON contract. The established trusted-local-application model remains unchanged; no authentication or user-account system is introduced.

Saving does not open applications. The replacement selection becomes effective on the next normal `start.bat` action.

## Normal Start with Existing Private Configuration

When the canonical Private file already exists before a normal start action, Mini Server does not initialize or rewrite it. It rereads Shared and Private and computes:

```text
effective start sites
    = current valid Shared-approved applications
      ∩ current Private-selected applications
```

Shared order is authoritative. Consequently:

- Private can reduce Shared but never elevate outside it.
- Private order cannot reorder Shared.
- An existing empty or effectively empty Private file opens no application.
- A stale Private entry removed from Shared is ineffective.
- A newly added Shared entry is not added automatically to an existing Private file.
- Re-adding a Shared entry makes it effective only if the existing Private file contains it.
- Missing Shared leaves the server active, opens no application, and does not recreate Shared.
- Readable but empty or effectively empty Shared leaves the server active and opens no application.
- Unreadable or unresolvable Shared leaves the server active, opens no application, and produces a concise diagnostic.
- An unreadable Private file opens no application rather than falling back to Shared and produces a concise diagnostic.
- The root page is not opened automatically.

Both files are reread on every normal start action, including repeated starts. Live edits take effect on the next start action without restarting the active HTTP server. No file watcher is required.

## Startup Sequences

On a first local start, Mini Server completes server startup, discovers the actual port, confirms readiness, publishes valid runtime state, releases `startup.lock` while retaining `instance.lock`, and then applies the missing-Private initialization or existing-Private evaluation described above. Browser opening never precedes server readiness or valid state publication.

On a repeated start, Mini Server reuses the existing active server and port without starting another server or requesting another port. It then applies the same current configuration decision: initialize and open only `/` when Private is missing, or evaluate Shared intersected with existing Private and open the resulting application URLs in Shared order.

All browser URLs use `127.0.0.1` and the actual active port and are handed to the REQ-009 mechanism.

## Later Manual Reselection

A user may manually visit `http://127.0.0.1:<active-port>/` while Mini Server is running. The same Shared-only page is shown with all current valid Shared choices checked. Saving creates a fresh replacement selection and overwrites the Private file. It does not read or display prior Private checkbox state and does not open applications immediately.

## Runtime, Persistence, and Access Boundary

Start-site initialization, evaluation, page display, and saving must not alter the active port, runtime state, stop token, startup or instance locks, persistence contents, persistence API, MiniApi behavior, or server lifetime except for the single authorized Private configuration write.

Application discovery and serving remain based on actual valid first-level directories below `www/`. Start-site approval and selection do not hide, enable, disable, authenticate, or authorize an application. A valid physical application absent from Shared or Private may remain directly reachable through its normal URL.

## Acceptance Criteria

REQ-010 is fulfilled when all of the following are true:

- Shared configuration is read from `<installation-root>\config\start-sites.txt`.
- Private configuration is read from and written only to `%APPDATA%\MiniServer\Config\start-sites.txt` with canonical directory casing.
- Private configuration remains separate from `%APPDATA%\MiniServer\Data\`, `%LOCALAPPDATA%\MiniServer\runtime\`, and the installation.
- The distribution contains Shared `config\start-sites.txt` with `example` as its active default and no packaged Private file.
- No Java code gives `example` special browser-opening behavior.
- Both files use the specified UTF-8 line syntax, normalization, validation, and first-duplicate retention.
- Only safe current first-level applications below `www/` can become valid Shared entries.
- URLs, paths, `_shared`, invalid entries, and missing applications never become start sites.
- Shared is the upper bound and canonical order for all initialization, UI, save, and automatic-opening behavior.
- After readiness, active-port discovery, and valid runtime-state publication, a missing Private file is initialized from only the current valid normalized Shared list in Shared order.
- Initialization creates `MiniServer\Config` when required and never byte-copies Shared comments, invalid entries, duplicates, or missing applications.
- A readable Shared file with no valid applications may initialize an existing empty Private file.
- The initialization start opens only `http://127.0.0.1:<active-port>/` and no application URL.
- Missing or unreadable Shared during initialization leaves the server active, creates no Private file, opens no application, and opens `/` if practical.
- Shared-unavailable UI explains the problem and does not permit saving.
- A later normal start retries initialization while Private remains absent.
- `GET /` returns the built-in Mini Server welcome and selection page with heading `Welcome to Mini Server`.
- The root page is Mini Server infrastructure rather than an application, directory listing, persistence page, file below `www`, or authorization interface.
- The page is English only, pleasant, responsive, self-contained, and free of external assets, services, frameworks, analytics, tracking, and internet dependencies.
- Every `GET /` derives choices only from current valid Shared configuration in Shared order.
- Every `GET /` initially checks all available Shared-approved applications.
- `GET /` never reads Private configuration to determine checkbox state or page choices.
- The page clearly says saving replaces the personal selection rather than claiming to show current saved state.
- The page shows the canonical advanced-user Private path.
- `POST /__miniserver/start-sites` is the sole canonical selection-save route.
- The save route accepts only the specified JSON object with a `sites` string array and rejects malformed structure without a write.
- The save route rereads and revalidates current Shared before every write and refuses saving when Shared is unavailable.
- Submitted entries outside current valid Shared are discarded and never stored.
- The saved result is deduplicated and ordered according to current Shared regardless of submitted order.
- Saving replaces the whole Private selection and does not merge with or use the previous Private file as input.
- An empty selection creates or replaces the Private file with an empty UTF-8 selection.
- The Private file is written safely and failure is never presented as success.
- The route cannot write Shared configuration, persistence, runtime state, a client-selected target, or an arbitrary path.
- The route remains loopback-only infrastructure, adds no CORS, and does not introduce authentication or accounts.
- Saving provides clear success or concise error feedback and affects the next normal start without opening applications immediately.
- A later manual `GET /` repeats the same Shared-only, all-checked replacement workflow.
- An existing Private file is not initialized or rewritten during normal start evaluation.
- Normal starts with existing Private use current valid Shared intersected with current Private in Shared order.
- Existing empty Private opens none, Private cannot elevate or reorder, stale entries are ineffective, and new Shared entries are not automatically added.
- With existing Private, missing, empty, or unreadable Shared opens no application, leaves the server active, and does not automatically open `/`.
- Unreadable existing Private opens none and reports a concise diagnostic.
- Both files are reread on every normal first or repeated start action; no watcher is required.
- A repeated start reuses the existing active server and port.
- Application discovery, serving, and direct URL access remain independent of start-site configuration.
- Runtime state, active port, stop token, locking, persistence, MiniApi behavior, and server lifetime remain unchanged outside the authorized Private configuration write.
- All implementation remains compatible with Java 8.

## Constraints

The configuration files remain simple UTF-8 line-oriented lists, not a general settings framework. The built-in page and save route are narrowly scoped Mini Server infrastructure.

## Related Decisions

- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-014 — Not Intended for Public Internet Use
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch
- D-027 — Shared and Private Start-Site Selection
- D-029 — Interactive Start-Site Selection and First-Run Initialization

## Related Requirements

- REQ-006 — Startup, Browser Launch, and Server Lifetime (released with v1.0.0)
- REQ-009 — Default Browser Launch
- REQ-011 — Unified Current-User Storage

## Related Architecture

See `docs/ARCHITECTURE.md`, especially:

- Storage and Runtime Boundaries
- Start-Site Configuration
- Built-In Welcome and Start-Site Selection
- Startup and Browser Launch

## Related Tasks

- T-016 — Implement Configurable Start Sites
- T-018 — Implement Unified User Storage and Start-Site Selection UX
- T-017 — Verify v1.1 Release Scope

## Target Release

v1.1
