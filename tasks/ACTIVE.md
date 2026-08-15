# Active Tasks

This document records the current approved implementation work for Mini Server.

## Current State

Mini Server v1.0.0 has been released. The approved v1.1 scope is defined by REQ-009 and REQ-010.

No v1.1 implementation work has started. The tasks below are Planned and must be implemented in order against the active requirements and approved decisions.

## T-015 — Implement Default Browser Launch

Status: Planned

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

Status: Planned

Related requirement:

- REQ-010 — Configurable Start Sites

Related decisions:

- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-014 — Not Intended for Public Internet Use
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch
- D-026 — Shared Installation Start-Site Configuration

Description:

Implement installation-level `config/start-sites.txt` for selecting which existing applications are automatically opened during a normal start action.

The configuration affects browser opening only. Application discovery and serving remain based on the actual valid first-level directories below `www/`.

Acceptance:

- The future source and distribution contain `config/start-sites.txt`.
- The distributed file contains `example` as its default active entry.
- The Java implementation contains no hard-coded `example` startup behavior.
- The file is read as UTF-8.
- Leading and trailing whitespace is trimmed.
- Empty lines are ignored.
- A line beginning with `#` after trimming is treated as a comment.
- Effective entries are limited to first-level application names below `www/`.
- Full URLs are rejected or ignored.
- Absolute, relative, and multi-level filesystem paths are rejected or ignored.
- Entries cannot provide a protocol, host, port, query, or fragment.
- `_shared` is invalid as a start site.
- Invalid or unsafe entries are ignored and never become arbitrary URLs or paths.
- Duplicate entries produce one opening request, retaining the first occurrence.
- Remaining valid entries preserve file order.
- A configured application is selected only when its valid directory currently exists below `www/`.
- A missing configured application is ignored without failing startup or creating a directory.
- Missing applications do not prevent other valid applications from opening.
- A missing configuration file leaves the server running and opens no application automatically.
- A missing configuration file is not recreated automatically during normal runtime startup.
- An empty or effectively empty configuration opens no application automatically.
- An unreadable configuration leaves the server active, derives no URLs from unreadable content, and produces a concise diagnostic.
- First-start evaluation occurs after readiness and valid runtime-state publication and uses the new actual active port.
- A repeated start reuses the existing active server and port.
- The configuration is reread on every normal start action, including repeated starts.
- A configuration change while the server runs affects the next repeated start without restarting the server.
- Application discovery and serving remain independent from `start-sites.txt`.
- Resulting URLs are passed in retained order to the REQ-009 browser-opening mechanism.
- No Microsoft Edge-specific launch logic is introduced.
- Runtime state, stop tokens, runtime locking, and persistence boundaries remain unchanged.
- Automated parser/configuration tests cover encoding, normalization, validation, deduplication, ordering, and failure cases.
- Automated first-start and repeated-start tests cover current configuration and active-port use.
- Distribution verification covers the default configuration file and entry.
- Production code, tests, and distribution remain compatible with Java 8.

---

## T-017 — Verify v1.1 Release Scope

Status: Planned

Related requirements:

- REQ-009 — Default Browser Launch
- REQ-010 — Configurable Start Sites

Related decisions:

- D-025 — Windows Default Browser Launch
- D-026 — Shared Installation Start-Site Configuration

Description:

Perform final end-to-end verification of the complete v1.1 scope and required v1.0 regression behavior.

Produce a dedicated v1.1 release-verification document analogous in purpose to the v1.0 verification report but scoped to v1.1 and its necessary regressions.

Acceptance:

### Requirement Verification

- Every REQ-009 acceptance criterion is reviewed.
- Every REQ-010 acceptance criterion is reviewed.
- Every criterion is verified or explicitly documented as incomplete.
- No incomplete requirement or criterion is presented as complete.

### Automated and Build Verification

- Java tests pass.
- MiniApi JavaScript tests pass.
- The Maven build passes.
- Distribution verification passes.
- Java 8 compatibility is verified.

### v1.1 Behavior

- The distribution contains `config/start-sites.txt` with default entry `example`.
- Microsoft Edge is not a required browser.
- Windows default-browser handling is used.
- Changing the Windows default browser is respected on a later start action.
- A first start uses its newly assigned dynamic port.
- A repeated start reuses the active server and port.
- A repeated start rereads the current `start-sites.txt`.
- Configuration changes while the server runs affect the next repeated start.
- Multiple selected applications preserve configuration order.
- Duplicate entries produce one opening request.
- Comments, empty lines, and surrounding whitespace behave as specified.
- `_shared` is never opened as an application.
- Unsafe URL or path entries are never opened.
- Missing configured applications are ignored.
- Missing configuration leaves the server running and opens no application automatically.
- Empty configuration leaves the server running and opens no application automatically.
- Unreadable configuration does not stop an otherwise successful server.
- Browser-opening failure does not stop the server.
- Failure to open one URL does not prevent attempts for later valid URLs.
- Closing the selected browser does not intentionally stop Mini Server.

### v1.0 Regression Verification

- `stop.bat` still stops the active instance gracefully.
- Stopping an inactive server remains harmless.
- Restarting after stop obtains a fresh dynamic port.
- Static serving and multiple applications continue to work.
- Path-traversal protection remains effective.
- Persistence directories remain protected from static serving.
- Private and shared persistence continue to work.
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

1. T-015 — Implement Default Browser Launch
2. T-016 — Implement Configurable Start Sites
3. T-017 — Verify v1.1 Release Scope

The completed v1.0 implementation history remains preserved by Git history, the `v1.0.0` tag, `releases/v1.0.md`, and `docs/notes/V1.0-RELEASE-VERIFICATION.md`.
