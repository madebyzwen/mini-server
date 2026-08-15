# REQ-010 — Configurable Start Sites

## Requirement ID

REQ-010

## Title

Configurable Start Sites

## Status

Active

## Purpose

Mini Server must use Shared installation approval and Private current-user
selection to determine which hosted applications are opened during a normal
start action. Shared defines the upper bound and canonical order. Private may
reduce that set, but it must never elevate an application outside Shared or
reorder Shared-approved applications.

The built-in root page is the explicit setup and recovery interface. Displaying
that page does not create or modify Private configuration. A successful
`Save and open` action is the only first-run commit point, and every saved
selection contains at least one current valid Shared-approved application.

Start-site configuration controls automatic browser opening only. It does not
define application discovery, static serving, authentication, authorization,
or access control.

## Revision Context

This active, unreleased v1.1 requirement incorporates D-030 after manual Windows
verification of the earlier T-018 implementation exposed interaction defects.
D-030 refines the relevant parts of D-027 and D-029 while preserving their
historical text. Implementation must be corrected and Windows verification
rerun before this requirement is complete.

## Historical Relationship

REQ-006 — Startup, Browser Launch, and Server Lifetime was released with v1.0.0
and remains unchanged under `requirements/archive/v1.0/`.

REQ-010 defines the current v1.1 start-site behavior while preserving the
released loopback, dynamic-port, application-serving, and local-runtime model.

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
Private configuration is separate from Private application persistence,
local runtime coordination, and Shared installation configuration.

## Distributed Default

The v1.1 distribution must contain Shared `config\start-sites.txt` with this
initial active entry:

```text
example
```

The distribution must not contain a pre-created Private current-user file.
The `example` application has no special hard-coded Java behavior and is
eligible only because the valid Shared configuration lists it.

The v1.1 distribution must also contain `configure.bat` as the supported
Windows entry point for reopening the built-in configuration page.

## Configuration Format and Validation

Both files are simple UTF-8 text with one effective first-level application
name per line.

Parsing and normalization follow these rules independently for each file:

- Leading and trailing whitespace is ignored.
- Empty lines are ignored.
- A trimmed line beginning with `#` is a comment and is ignored.
- Entries refer only to first-level application directories below `www/`.
- Full URLs and absolute, relative, or multi-level filesystem paths are invalid.
- Entries cannot supply a protocol, host, port, query, or fragment.
- `_shared` is reserved and is never a valid application start site.
- Duplicate effective entries retain only their first occurrence within that file.

Invalid or unsafe entries are ignored and must never become arbitrary URLs or
filesystem paths. A Shared entry becomes currently approved only when the
corresponding safe first-level application directory currently exists below
`www/`. Shared approval defines the upper bound and canonical order for every
normal-start evaluation, root-page choice list, and save operation.

## Normal Start Decision

After a normal first or repeated start has a ready server, the actual active
port, and valid runtime state, Mini Server rereads the current configuration.

### Missing Private

When Private is absent, Mini Server does not create it. If Shared is readable
and has at least one current valid application, Mini Server opens only:

```text
http://127.0.0.1:<active-port>/
```

The root page initially proposes every current valid Shared application as
checked in Shared order. This proposal is not saved state. Setup is complete
only after a successful save. Closing the page without saving leaves Private
absent, commits nothing, and causes the next normal start to open setup again.

### Existing Private with Effective Applications

When Private exists and is readable, Mini Server computes:

```text
effective start sites
    = current valid Shared-approved applications
      ∩ current Private-selected applications
```

If this produces at least one application, normal start opens those applications
in Shared order and does not force the root page. Private cannot elevate or
reorder; stale entries are ineffective; newly added Shared entries remain
unchecked and ineffective unless already selected in Private.

### Zero or Broken Effective Selection

Normal start opens `/` as a recovery/configuration UI instead of silently
opening nothing when no effective application can be opened. This includes:

- an existing empty or effectively empty Private file;
- all Private entries being stale, removed, or no longer Shared-approved;
- unreadable Private configuration;
- readable but empty or effectively empty Shared configuration; and
- missing, unreadable, or otherwise unavailable Shared configuration.

The server remains active. When Shared is available and nonempty, the user can
repair the selection. When Shared is unavailable or contains no valid
applications, saving is unavailable. If at least one valid effective application
remains, normal start opens that selection normally and does not force `/`.

Both files are reread on every normal start action, including repeated starts.
A repeated start reuses the existing active server and actual port and never
starts a second server or requests a second port. No watcher is required.

## Shared Empty or Unavailable

When Shared is readable but produces zero current valid applications:

- no new Private file is created;
- saving is unavailable;
- `/` explains that nothing is currently available to select or save;
- no application opens automatically; and
- a later normal start retries and reevaluates the flow.

When Shared is missing, unreadable, or otherwise unavailable:

- no Private file is fabricated;
- saving is unavailable;
- `/` explains that Shared approval cannot currently be read;
- the server remains active; and
- a later normal start retries and reevaluates the flow.

## Built-In Root Configuration Page

`GET /` is reserved for a Mini Server-owned setup, editing, and recovery page.
It is not a directory listing, hosted application, persistence API page, normal
file below `www`, or authentication/authorization interface.

The runtime page is English only and has the main visible heading:

```text
Welcome to Mini Server
```

It is pleasant, responsive, self-contained, and free of external assets,
services, frameworks, analytics, tracking, and internet dependencies. It shows
the advanced-user path `%APPDATA%\MiniServer\Config\start-sites.txt`.

Every `GET /` rereads current Shared to derive the available applications and
their presentation order. It never offers `_shared`, unsafe entries, invalid
entries, missing applications, or physical applications absent from Shared.
Private can influence checked state but can never introduce a choice.

Checkbox state is context-sensitive:

- When Private is missing, every current valid Shared application is checked as
  an unsaved first-run/default proposal.
- When Private exists and is readable, checked state is its current selection
  intersected with current valid Shared. Shared order controls presentation;
  stale Private entries are not displayed, and newly added Shared applications
  are shown unchecked unless already selected in Private.
- When Private exists but cannot be read, `/` is a recovery UI. It may display
  current Shared-approved applications, clearly warns that saved state could
  not be read, does not claim to display it, does not guess it, and preselects
  no application merely as a fallback.

The page wording must make these states and actions clear:

- First run: choose at least one application; nothing is saved until
  `Save and open` succeeds.
- Existing selection: the page shows the personal selection among currently
  available Shared-approved applications.
- Save: the personal selection is replaced, opened immediately, and used on
  future normal starts.
- No available Shared applications: nothing can currently be selected or saved.
- Shared unavailable: approval cannot currently be read and selection cannot be saved.
- Unreadable Private: the existing personal selection could not be read; choose
  a replacement if saving is possible.

## Saving and Immediately Applying a Selection

The canonical internal save route is:

```text
POST /__miniserver/start-sites
```

It accepts `application/json` with exactly one `sites` member whose value is an
array of application-name strings. Malformed JSON, wrong content type, wrong
top-level structure, additional top-level members, or non-string entries are
rejected without changing Private.

For a structurally valid request, Mini Server:

1. Rereads and revalidates current Shared at save time.
2. Refuses the save without changing Private when Shared is unavailable or has
   no current valid applications.
3. Treats submitted names only as requested membership, never as paths or URLs.
4. Filters to current valid Shared membership, deduplicates, and restores Shared order.
5. Requires at least one application in the normalized result.
6. Safely replaces or creates the complete canonical UTF-8 Private file.
7. Only after the write succeeds, immediately applies the normalized saved selection.

`{"sites":[]}` is invalid and must not modify Private. A nonempty request that
save-time Shared revalidation filters to zero is also rejected without a write.
The internal route must distinguish malformed or empty user input from a
selection that became stale because current Shared changed. Existing Private
content is preserved after every rejected or failed save. Client validation is
only a convenience; server validation is authoritative.

Successful save returns a narrowly scoped internal JSON result containing the
server-normalized applications and server-generated local targets in canonical
Shared order. The page must use only this response—not raw checkbox/request
values—to perform post-save opening:

- the current root tab is replaced or navigated to the first normalized target;
- additional normalized targets are opened in Shared order, using the
  established Windows-default-browser mechanism when practical; and
- no raw, unapproved, stale, or client-constructed target is opened.

The primary action is labeled `Save and open` or equivalently. The root tab
must cease being the active configuration page after success; the design must
not rely on `window.close()`. Browser-opening failure after a successful write
does not roll back or corrupt Private, stop the server, invalidate runtime
state, or prevent isolated attempts to open later normalized applications.

During a save, the primary action is disabled and the page permits only one
logical in-flight submission, preventing double-save and duplicate-opening
behavior. On failure, current checkbox state is retained, a concise error is
shown, and saving is re-enabled when another valid attempt is possible.

The successful response contract is private to the built-in start-site UI and
does not create a general settings API.

## Supported Reconfiguration Action

The v1.1 distribution provides `configure.bat`, which conceptually invokes a
dedicated `MiniServer configure` mode so users need not discover the dynamic
port manually.

When no server is running, configure mode follows the normal loopback,
dynamic-port, readiness, runtime-state, single-instance, and detached portable
Windows launcher model, but opens only `/` and does not open configured
applications. The server remains running normally afterward.

When a server is already running, configure mode reuses the active instance and
actual port, starts no second server, opens only `/`, and exits normally.

`configure.bat` itself never modifies Private; only a successful save does. It
uses quoted portable launcher paths and the Windows default-browser mechanism
where applicable. Browser failure does not stop the server. Manual navigation
to the actual root URL remains valid, but `configure.bat` is the supported
normal workflow for later reconfiguration.

## Runtime, Persistence, and Trust Boundaries

Start-site evaluation, page display, configure mode, and saving must preserve:

- loopback-only listening and dynamic active-port use;
- no CORS, general settings API, authentication, or account system;
- Shared as the upper bound and Private as inclusion only;
- independent application discovery, static serving, and direct valid URLs;
- server-derived targets with no request-selected files, paths, hosts, or URLs;
- separate runtime state, stop token, locks, persistence API, MiniApi, and
  server-lifetime responsibilities; and
- Java 8 compatibility.

The persistent start-site sidecar lock file is implementation infrastructure.
Its presence does not mean configuration is actively locked and must not count
as completed setup. Immediate post-save opening must never turn client text
into arbitrary browser URLs.

## Acceptance Criteria

REQ-010 is fulfilled when all of the following are true:

- Shared and Private use their specified canonical locations and UTF-8 format.
- The distribution contains Shared `example`, no packaged Private file, and `configure.bat`.
- Shared parsing, safety, existence validation, deduplication, upper-bound, and
  canonical-order rules apply consistently to start, root GET, and save.
- Missing Private opens setup only after readiness and actual-port publication,
  creates no Private file, and checks all current valid Shared choices only as
  an unsaved proposal.
- Closing setup without saving leaves Private absent and retries setup on the
  next normal start.
- A saved selection contains at least one current valid Shared application;
  empty and normalized-to-empty saves preserve existing Private content.
- Empty or unavailable Shared creates no Private file, disables saving, opens
  no application, keeps the server active, and presents an explanatory root UI.
- `GET /` derives choices and order only from current valid Shared.
- Readable existing Private determines checked membership after intersection;
  stale entries are hidden and newly Shared applications are unchecked unless selected.
- Unreadable Private presents a warning, guesses no saved state, and preselects
  nothing merely as fallback.
- Existing readable nonempty effective Private opens applications in Shared
  order during normal start without forcing `/`.
- Every zero/broken effective-selection case opens `/` as recovery instead of
  silently opening nothing.
- Save strictly validates its JSON contract, rereads Shared, normalizes to
  current membership and Shared order, requires at least one result, and safely
  replaces or creates only canonical Private.
- Successful save returns the server-normalized ordered targets, navigates the
  root tab to the first, and opens later targets in order without using raw input.
- Browser-opening failures after save are isolated and never roll back the write
  or stop the server.
- One in-flight save cannot cause duplicate logical writes or opening; failure
  retains user choices and permits a later valid retry.
- `configure.bat` and configure mode start or reuse exactly one server, use its
  actual port, open only `/` through the Windows default browser, modify no
  configuration themselves, and isolate browser failure.
- Normal starts after configuration reread Shared and Private, preserve Shared
  order, do not auto-select newly Shared entries, and use recovery when the
  effective selection becomes empty or unreadable.
- Application discovery, direct serving, persistence, MiniApi, runtime control,
  and server lifetime remain independent and unchanged.
- Production code, launchers, distribution, and tests implement these behaviors
  with Java 8 compatibility before T-018 can be completed.

## Constraints

The configuration files remain simple UTF-8 line-oriented lists, not a general
settings framework. The built-in page, save result, configure mode, and launcher
are narrowly scoped Mini Server infrastructure.

## Related Decisions

- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-014 — Not Intended for Public Internet Use
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch
- D-027 — Shared and Private Start-Site Selection
- D-029 — Interactive Start-Site Selection and First-Run Initialization
- D-030 — Start-Site Setup, Recovery, and Immediate Apply

## Related Requirements

- REQ-006 — Startup, Browser Launch, and Server Lifetime (released with v1.0.0)
- REQ-009 — Default Browser Launch
- REQ-011 — Unified Current-User Storage

## Related Architecture

See `docs/ARCHITECTURE.md`, especially:

- Storage and Runtime Boundaries
- Start-Site Configuration
- Built-In Start-Site Setup, Editing, and Recovery
- Startup, Configure Action, and Browser Launch

## Related Tasks

- T-016 — Implement Configurable Start Sites
- T-018 — Implement Unified User Storage and Start-Site Selection UX
- T-017 — Verify v1.1 Release Scope

## Target Release

v1.1
