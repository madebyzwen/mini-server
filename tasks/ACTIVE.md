# Active Tasks

This document records the current approved implementation work for Mini Server.

## Current State

Mini Server v1.0.0 has been released. The approved v1.1 scope is defined by REQ-009, revised REQ-010, and REQ-011.

T-015 and T-016 have been completed. T-018 implementation and local automated
verification are present, but its required packaged Windows UX/default-browser
manual verification has not yet been performed, so T-018 remains Planned.
T-017 remains Planned as the final v1.1 verification task after T-018.

## T-015 — Implement Default Browser Launch

Status: Done

Related requirement:

- REQ-009 — Default Browser Launch

Related decisions:

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch

Description:

Replace Microsoft Edge-specific browser-launch implementation with a browser-independent Windows URL-opening mechanism.

The implementation must use `127.0.0.1` and the actual active loopback port for first and repeated starts. It must not alter server lifetime, runtime coordination, persistence, API behavior, dynamic-port allocation, or graceful-stop behavior.

The exact Java 8-compatible Windows mechanism may be selected during implementation preparation, provided it conforms to REQ-009 and D-025.

Acceptance:

- Production launch contains no Microsoft Edge-specific behavior.
- Production launch does not discover browser executables or installation directories.
- Production launch maintains no supported-browser list or browser fallback chain.
- Each URL is submitted through the configured Windows HTTP URL handler.
- Every URL uses `127.0.0.1` and the actual active dynamic port.
- First-start browser opening occurs only after server readiness and valid runtime-state publication.
- A repeated start reuses the active local instance and port.
- A repeated start does not start a second server or request a second port.
- The launch mechanism accepts multiple URLs.
- Multiple URLs preserve caller-provided order.
- Failure to open one URL does not prevent later URL attempts.
- Browser-opening failure does not stop the server.
- Browser-opening failure does not invalidate runtime state or release the instance lock.
- Browser-opening failure does not change the active port or alter persistence.
- A useful concise diagnostic includes the affected manual URL when practical.
- Closing the selected browser does not intentionally stop Mini Server.
- `stop.bat` and authenticated graceful-stop behavior remain unchanged.
- Existing runtime, routing, API, and persistence behavior remains unchanged.
- Relevant automated tests cover first start, repeated start, ordering, and isolated failures.
- Production code and tests remain compatible with Java 8.

---

## T-016 — Implement Configurable Start Sites

Status: Done

Historical scope note: T-016 completed the earlier pre-refinement REQ-010 design. Its lowercase Private path and missing-Private behavior below describe that completed implementation state; T-018 is planned to replace those portions with the current REQ-010 and REQ-011 design before final verification.

Related requirement:

- REQ-010 — Configurable Start Sites

Related decisions:

- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-014 — Not Intended for Public Internet Use
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch
- D-027 — Shared and Private Start-Site Selection

Description:

Implement two-level start-site selection using Shared installation configuration at `<installation-root>\config\start-sites.txt` and Private current-user configuration at `%APPDATA%\MiniServer\config\start-sites.txt`.

Shared defines the upper bound of existing applications approved for automatic opening and their canonical order. Private may reduce that selection but cannot enable an application outside Shared or reorder it. Both configurations affect browser opening only; application discovery and serving remain based on the actual valid first-level directories below `www/`.

Acceptance:

- The future source and distribution contain the Shared `config/start-sites.txt`.
- The distributed Shared file contains `example` as its default active entry.
- The distribution does not package a pre-created Private current-user configuration file.
- Private configuration is read from `%APPDATA%\MiniServer\config\start-sites.txt` as current-user Mini Server configuration.
- Private configuration is not stored in `%APPDATA%\MiniServerData\` or `%LOCALAPPDATA%\MiniServer\runtime\`.
- The Java implementation contains no hard-coded `example` startup behavior.
- Both files are read as UTF-8.
- Leading and trailing whitespace is trimmed.
- Empty lines are ignored.
- A line beginning with `#` after trimming is treated as a comment.
- Effective entries are limited to first-level application names below `www/`.
- Full URLs are rejected or ignored.
- Absolute, relative, and multi-level filesystem paths are rejected or ignored.
- Entries cannot provide a protocol, host, port, query, or fragment.
- `_shared` is invalid as a start site.
- Invalid or unsafe entries are ignored and never become arbitrary URLs or paths.
- Duplicate entries within either file retain only the first occurrence.
- A Shared entry is approved only when its valid directory currently exists below `www/`.
- A Private entry is effective only when it is in the current valid Shared-approved set.
- Private cannot activate a valid existing application that is absent from Shared.
- Shared file order is canonical, and Private inclusion cannot reorder it.
- A missing configured application is ignored without failing startup or creating a directory.
- Missing applications do not prevent other valid applications from opening.
- A missing Shared file leaves the server running and opens no application automatically; Private cannot compensate.
- An empty or effectively empty Shared file leaves the server running and opens no application automatically.
- An unreadable Shared file leaves the server active, derives no URLs, prevents Private bypass, and produces a concise diagnostic.
- A missing Private file selects the complete valid Shared selection.
- An existing empty or effectively empty Private file selects no applications and does not fall back to Shared.
- An unreadable Private file leaves the server active, opens no applications rather than falling back to Shared, and produces a concise diagnostic.
- Neither missing configuration file is recreated automatically during normal runtime startup.
- Private entries outside Shared are ignored even when the corresponding application exists below `www/`.
- Removing a Shared application removes its effective eligibility on the next normal start even when stale Private selection remains.
- Re-adding a Shared application selects it for users without a Private file and for users with a Private file only when that file still contains it.
- A newly added Shared application does not enter an existing explicit Private selection automatically.
- First-start evaluation occurs after readiness and valid runtime-state publication and uses the new actual active port.
- A repeated start reuses the existing active server and port.
- Both configurations are reread and their intersection is recomputed on every normal start action, including repeated starts.
- A change to either file while the server runs affects the next repeated start without restarting the server.
- Application discovery and serving remain independent from `start-sites.txt`.
- Shared approval is not authentication, authorization, or a static-serving allowlist.
- Resulting URLs are passed in Shared order to the REQ-009 browser-opening mechanism.
- No Microsoft Edge-specific launch logic is introduced.
- Runtime state, stop tokens, runtime locking, and persistence boundaries remain unchanged.
- Automated parser/configuration tests cover both files' encoding, normalization, validation, deduplication, and failure cases.
- Automated selection tests cover Shared validation, Private intersection, missing and empty Private behavior, Private entries outside Shared, and Shared canonical ordering.
- Automated first-start and repeated-start tests cover current Shared and Private contents, changes to either file, and active-port use.
- Automated tests cover Shared removal and re-add behavior for users with and without existing Private files.
- Distribution verification covers the Shared default configuration file and entry and confirms that no Private current-user file is packaged.
- Production code, tests, and distribution remain compatible with Java 8.

---

## T-018 — Implement Unified User Storage and Start-Site Selection UX

Status: Planned

Implementation note: source, automated tests, distribution-compatible code,
and current documentation implement this task. Status remains Planned because
the required manual welcome-page, Windows-default-browser, feedback, and
packaged Windows verification has not been performed in the Linux development
environment. This note is not T-017 release-verification evidence.

Related requirements:

- REQ-010 — Configurable Start Sites
- REQ-011 — Unified Current-User Storage

Related decisions:

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-008 — Application Site and Persistence Scope Are Derived from the URL
- D-016 — Application Separation Is Namespace Isolation, Not Authentication
- D-020 — Local Per-User/Computer Runtime Instance
- D-021 — Explicit Shared and Private Persistence Scopes
- D-023 — Concurrency-Safe Persistence Writes
- D-025 — Windows Default Browser Launch
- D-027 — Shared and Private Start-Site Selection
- D-028 — Unified Current-User Mini Server Storage
- D-029 — Interactive Start-Site Selection and First-Run Initialization

Description:

Implement the approved v1.1 current-user storage hierarchy, safe migration of released v1.0 Private application data, and the Mini Server-owned welcome/replacement-selection workflow. Preserve the existing Shared upper bound, canonical ordering, loopback runtime, persistence API, and application-serving boundaries.

Acceptance:

### Unified Current-User Storage

- Canonical current-user configuration is `%APPDATA%\MiniServer\Config\start-sites.txt` using `MiniServer` and `Config` casing for newly created directories.
- Canonical Private persistence is `%APPDATA%\MiniServer\Data\<site>\data.json` using `MiniServer` and `Data` casing.
- The new Private path has no redundant `<site>\data\data.json` layer.
- Shared persistence remains `<installation-root>\www\<site>\data\data.json`.
- Runtime coordination remains `%LOCALAPPDATA%\MiniServer\runtime\`.
- Configuration, Private data, Shared data, and local runtime state remain separate responsibilities.

### v1.0 Private-Data Migration

- `%APPDATA%\MiniServerData\<site>\data\data.json` is recognized only as the released v1.0 migration source.
- An existing canonical file is authoritative and legacy data never overwrites or merges into it.
- When canonical is absent and legacy exists, migration completes before normal Private persistence use.
- Migration preserves existing file content and JSON data.
- Migration coordinates concurrent attempts and follows established bounded-locking and atomic-write integrity principles.
- The legacy file is removed only after the canonical file is safely established.
- Empty legacy directories are removed best-effort, and cleanup failure is nonfatal after successful migration.
- Failed migration leaves legacy data intact and does not silently destroy, truncate, overwrite, or report success.
- There is no permanent dual write, merge, alias, or fallback after canonical exists.
- When neither file exists, normal Private persistence uses the canonical location.

### Missing-Private Initialization

- After readiness, actual-port discovery, and valid runtime-state publication, a missing Private start-site file triggers initialization on a first or repeated normal start action.
- The implementation creates `%APPDATA%\MiniServer\Config\` when required.
- The initial file contains only current valid normalized Shared application names in Shared order.
- Shared comments, invalid entries, duplicates, and missing applications are not copied.
- A readable Shared file with no valid applications may initialize an empty Private file.
- The initialization action opens only `http://127.0.0.1:<active-port>/` through the REQ-009 mechanism and opens no application URL.
- If Shared is unavailable or cannot be resolved, the server remains active, creates no Private file, opens no application, and opens `/` if practical.
- Shared-unavailable UI explains why no applications are selectable and does not permit saving.
- A later start retries initialization while Private remains absent.

### Welcome and Selection Page

- `GET /` serves Mini Server-owned infrastructure rather than a directory listing, application, persistence page, file below `www`, or authorization interface.
- The English-only page has the visible heading `Welcome to Mini Server`.
- The page is deliberately pleasant, clean, modern, responsive, and readable at normal desktop sizes.
- Application choices are clearly presented, preferably as attractive checkbox rows or cards.
- The page has a clear primary `Save selection` action and clear success/error feedback.
- HTML, CSS, and JavaScript are self-contained, with no CDN, external font, asset, service, framework, analytics, tracking, or internet dependency.
- The page displays `%APPDATA%\MiniServer\Config\start-sites.txt` for advanced users.
- Every `GET /` rereads Shared and lists only current valid Shared-approved applications in Shared order.
- `_shared`, unsafe entries, invalid entries, missing applications, and physical apps outside Shared are never offered.
- All available Shared applications are initially selected.
- The page never reads Private configuration for checkbox state or page choices.
- The page states clearly that saving creates a new selection and replaces existing personal selection.

### Safe Replacement Save

- `POST /__miniserver/start-sites` is the canonical save route.
- The route accepts only `application/json` with exactly one `sites` array of strings; malformed or structurally invalid payloads fail without a write.
- The server rereads and revalidates current Shared at save time and refuses saving if Shared is unavailable.
- Browser-submitted names are untrusted membership requests.
- Entries outside current valid Shared are discarded and cannot become stored paths, URLs, `_shared`, or invalid names.
- Accepted membership is deduplicated and written in Shared canonical order regardless of request order.
- Saving replaces the complete Private file and never merges with or derives the result from prior Private contents.
- An empty selection produces an existing empty UTF-8 Private file.
- The replacement write is safe and atomic, and failures are never presented as success.
- The endpoint writes only the canonical Private start-site file and cannot write Shared configuration, persistence, runtime state, client-selected targets, or arbitrary paths.
- The route remains on the loopback listener, is handled before application/static routing, adds no CORS, and introduces no general settings API, authentication, or accounts.
- Saving does not open applications and becomes effective on the next normal start action.

### Existing Selection and Manual Reselection

- A normal start with existing Private rereads Shared and Private and opens their current valid intersection in Shared order.
- Existing empty Private opens none; Private cannot elevate or reorder; stale entries are ineffective; new Shared entries are not automatically added.
- Missing, empty, or unreadable Shared with existing Private leaves the server active, opens no application, and does not automatically open `/`.
- Existing Private is not initialized or rewritten during normal start evaluation.
- Unreadable existing Private opens none and produces a concise diagnostic.
- A manual later `GET /` repeats the Shared-only, all-checked fresh replacement workflow.
- Both configuration files are reread on each normal start; changes apply on the next start action and no watcher is added.

### Verification and Compatibility

- Automated tests cover canonical path resolution, path separation, legacy precedence, successful migration, failed migration preservation, best-effort cleanup, and no dual write.
- Automated tests cover missing/empty/unreadable Shared initialization, normalized initial files, existing/unreadable Private behavior, first and repeated starts, and actual-port use.
- HTTP tests cover `GET /`, Shared-only population, Private non-use, empty/unavailable states, strict save payloads, filtering, Shared-order replacement, empty selection, save-time revalidation, failure preservation, and route/path boundaries.
- Manual verification covers the English welcome experience, responsive presentation, Windows-default-browser first-run opening, clear feedback, and packaged Windows behavior.
- The Maven build, MiniApi JavaScript tests, and distribution verification pass after implementation.
- Production code and tests remain Java 8 compatible.
- Distribution compatibility is preserved and no Private user file is packaged.
- Implementation updates the public, source, distribution, test, changelog, architecture, and release documentation where the behavior makes existing text stale.
- No unrelated runtime, port, stop-token, lock, static-serving, application-discovery, persistence API, MiniApi, shared-persistence, or server-lifetime behavior changes.

---

## T-017 — Verify v1.1 Release Scope

Status: Planned

Related requirements:

- REQ-009 — Default Browser Launch
- REQ-010 — Configurable Start Sites
- REQ-011 — Unified Current-User Storage

Related decisions:

- D-025 — Windows Default Browser Launch
- D-027 — Shared and Private Start-Site Selection
- D-028 — Unified Current-User Mini Server Storage
- D-029 — Interactive Start-Site Selection and First-Run Initialization

Description:

Perform final end-to-end verification of the complete v1.1 scope and required v1.0 regression behavior after T-018 has implemented revised REQ-010 and REQ-011.

Produce a dedicated v1.1 release-verification document analogous in purpose to the v1.0 verification report but scoped to v1.1 and its necessary regressions.

Any earlier T-017 Phase 1 evidence describes the pre-refinement implementation and is historical input only. It is not final v1.1 release evidence and must be rerun against the post-T-018 implementation.

Acceptance:

### Requirement Verification

- Every REQ-009 acceptance criterion is reviewed.
- Every REQ-010 acceptance criterion is reviewed.
- Every REQ-011 acceptance criterion is reviewed.
- Every criterion is verified or explicitly documented as incomplete.
- No incomplete requirement or criterion is presented as complete.

### Automated and Build Verification

- Java tests pass.
- MiniApi JavaScript tests pass.
- The Maven build passes.
- Distribution verification passes.
- Java 8 compatibility is verified.

### v1.1 Behavior

- The distribution contains the Shared `config/start-sites.txt` with active default entry `example`.
- The distribution does not contain a pre-created Private current-user configuration file.
- Microsoft Edge is not a required browser.
- Windows default-browser handling is used.
- Changing the Windows default browser is respected on a later start action.
- A first start uses its newly assigned dynamic port.
- A repeated start reuses the active server and port.
- Canonical start-site configuration uses `%APPDATA%\MiniServer\Config\start-sites.txt`.
- Canonical Private persistence uses `%APPDATA%\MiniServer\Data\<site>\data.json` without a redundant Private data-directory layer.
- Shared persistence and `%LOCALAPPDATA%\MiniServer\runtime\` remain at their established locations.
- A user with no Private file receives a normalized file derived from current valid Shared in Shared order after server readiness and state publication.
- That initialization action opens only the built-in root page and no application URL.
- Missing or unreadable Shared during initialization creates no Private file, leaves the server active, and exposes the unavailable state through `/` without permitting save.
- Readable Shared with no valid applications can initialize an empty Private file and shows an empty available list.
- `GET /` provides the English-only `Welcome to Mini Server` selection experience.
- The root UI is pleasant, responsive, self-contained, and has no external service, asset, tracking, or internet dependency.
- Every root-page load uses only current valid Shared choices in Shared order, initially checks all choices, and never reads Private for checkbox state.
- The page accurately explains that save replaces rather than displays or merges the personal selection.
- `POST /__miniserver/start-sites` enforces the canonical JSON payload and loopback infrastructure boundary.
- Save-time Shared rereading and revalidation prevent stale browser choices from elevating outside current Shared.
- Saving filters to current Shared membership, preserves Shared order, replaces the complete Private file, and permits an existing empty selection.
- Saving cannot target Shared configuration, persistence, runtime state, arbitrary paths, or client-selected files and adds no CORS.
- Saving does not open applications and applies on the next normal start.
- A later manual `/` visit repeats the same Shared-only, all-checked replacement workflow.
- A Private subset reduces the Shared selection.
- Private cannot enable an application outside the valid Shared-approved set.
- An existing empty or effectively empty Private file selects no applications.
- Shared order remains effective when Private entries use another order.
- Duplicate entries in either file retain one effective entry.
- Comments, empty lines, and surrounding whitespace behave as specified in both files.
- `_shared` is never opened as an application.
- Unsafe URL or path entries are never opened.
- Missing configured applications are ignored without being created.
- Missing, empty, and unreadable Shared behavior matches the distinct initialization and existing-Private rules in REQ-010.
- Unreadable Private configuration leaves the server running and opens none rather than falling back to all Shared applications.
- A repeated start rereads current configuration and applies missing-Private initialization or existing-Private intersection as required.
- Shared changes while the server runs affect the next repeated start.
- Private changes while the server runs affect the next repeated start.
- Removing a Shared application prevents a stale Private selection from opening it on the next start action.
- Re-adding a Shared application affects an existing Private selection only when that file contains it.
- A new Shared application does not enter an existing Private selection automatically.
- Existing canonical Private data takes precedence over legacy data.
- Legacy v1.0 Private data migrates safely only when canonical data is absent and is removed only after canonical establishment.
- Migration failure preserves legacy data and fails cleanly; directory cleanup failure after success is nonfatal.
- After migration, Private persistence uses only the canonical path with no permanent dual write or fallback.
- Application discovery and serving remain independent of Shared approval and Private selection.
- Browser-opening failure does not stop the server.
- Failure to open one URL does not prevent attempts for later valid URLs.
- Closing the selected browser does not intentionally stop Mini Server.

### v1.0 Regression Verification

- `stop.bat` still stops the active instance gracefully.
- Stopping an inactive server remains harmless.
- Restarting after stop requests port `0` and obtains the actual operating-system-assigned active port; the numeric port may coincide with a previous run.
- Static serving and multiple applications continue to work.
- Path-traversal protection remains effective.
- Persistence directories remain protected from static serving.
- Private and shared persistence continue to work through the unchanged API contract at their v1.1 canonical locations.
- Released v1.0 Private data is preserved through the specified migration transition.
- MiniApi continues to work.
- Persistence locking and atomic writes remain effective.
- Shared/network installations retain local per-user/computer runtime state.
- The authenticated stop route and token behavior remain unchanged.

### Release Readiness

- User-facing documentation is reviewed before release.
- `CHANGELOG.md` `[Unreleased]` is updated during actual implementation or release preparation, not during planning.
- Final v1.1 verification evidence is recorded in a dedicated document.
- The release is not ready while any required criterion is knowingly incomplete.

---

## Current Execution Order

The approved v1.1 task order is:

1. T-015 — Done
2. T-016 — Done
3. T-018 — Planned; implementation and local automation present, required Windows manual verification pending
4. T-017 — Planned, final verification after T-018

The completed v1.0 implementation history remains preserved by Git history, the `v1.0.0` tag, `releases/v1.0.md`, and `docs/notes/V1.0-RELEASE-VERIFICATION.md`.
