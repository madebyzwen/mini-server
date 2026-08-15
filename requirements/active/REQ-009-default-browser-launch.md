# REQ-009 — Default Browser Launch

## Requirement ID

REQ-009

## Title

Default Browser Launch

## Status

Active

## Purpose

Mini Server must open local application URLs through the Windows default browser instead of explicitly selecting Microsoft Edge.

Browser selection belongs to Windows and the current user's operating-system configuration.

This requirement changes the browser-selection behavior released with v1.0 while preserving the archived v1.0 contract.

## Historical Relationship

REQ-006 — Startup, Browser Launch, and Server Lifetime was released with v1.0.0 and remains unchanged under:

```text
requirements/archive/v1.0/
```

REQ-009 defines the changed browser-selection behavior for v1.1. The unchanged runtime-coordination, startup, server-lifetime, and shutdown behavior remains governed by the current architecture and decisions.

## Browser Selection

For every valid local application URL to be opened, Mini Server asks Windows to open the URL using the configured operating-system HTTP URL handler.

Mini Server must not:

- Explicitly select Microsoft Edge, Chrome, Firefox, or another browser product
- Search for `msedge.exe` or any other browser executable
- Inspect Microsoft Edge installation directories
- Require Microsoft Edge to be installed
- Maintain browser executable paths or a list of supported browsers
- Implement browser priority or browser fallback chains

Mini Server does not need to know which browser Windows selects.

Changing the Windows default browser must not require restarting the active Mini Server. A later start action asks Windows to open the selected URLs using the current operating-system configuration.

## Local URL Construction

Mini Server continues to construct local application URLs using:

```text
http://127.0.0.1:<active-port>/<site>/
```

Every URL must use `127.0.0.1` and the actual active dynamically assigned Mini Server port. No fixed, guessed, scanned, or stale port may be used.

The application URLs to open are selected according to REQ-010.

## First Start

Established local startup behavior remains authoritative.

On a first start:

1. The HTTP server completes normal startup.
2. The actual active dynamic port is known.
3. Server readiness is confirmed.
4. Valid runtime state is published.
5. Application URLs are selected according to REQ-010.
6. Each valid URL is handed to Windows in the selected order.

Browser opening must not occur before the server URL is usable.

## Repeated Start

On a repeated start:

1. The existing local Mini Server instance is detected.
2. No second HTTP server is started.
3. No second TCP port is requested.
4. The existing active local port is reused.
5. The current start-site configuration is evaluated according to REQ-010.
6. URLs are constructed using the existing active port.
7. Each valid URL is handed to Windows in the selected order.
8. The repeated-start process exits normally.

## Multiple URLs

When REQ-010 selects multiple application URLs, Mini Server submits each URL to Windows in the configured start-site order.

Mini Server does not control whether the Windows-selected browser opens URLs in tabs, windows, an existing process, or a new process.

## Browser-Launch Failure

A browser-launch failure is a convenience failure, not a server failure.

Failure to open one URL must not:

- Stop the active HTTP server
- Invalidate runtime state
- Change the active port
- Start another server
- Modify persistence
- Release the active instance merely because browser opening failed
- Prevent Mini Server from attempting the remaining valid URLs

When practical, a concise diagnostic should identify the affected local URL so the user can open it manually.

## Server Lifetime

Browser lifetime does not own Mini Server lifetime.

Closing a browser tab, one browser window, all browser windows, or the Windows-selected default browser application must not intentionally stop Mini Server.

The existing authenticated graceful-stop behavior remains unchanged.

## Compatibility Boundary

REQ-009 must not change:

- Loopback-only binding
- Port `0` allocation and dynamic port discovery
- Local per-user/computer single-instance coordination
- Startup locking and bounded waits
- Runtime-state storage
- Authenticated graceful shutdown
- Static routing or application discovery
- The persistence API
- Shared or private persistence
- Persistence locking and atomic writes
- Server lifetime

The implementation must remain compatible with Java 8.

## Acceptance Criteria

REQ-009 is fulfilled when all of the following are true:

- Production browser launch contains no Microsoft Edge-specific selection or invocation.
- Production browser launch does not explicitly select Chrome, Firefox, Edge, or another browser product.
- No browser executable discovery or Edge installation-directory inspection is used.
- Mini Server maintains no browser executable paths or supported-browser list.
- No browser priority or fallback chain is implemented.
- Every selected URL is handed to the configured Windows operating-system HTTP URL handler.
- Mini Server does not need to identify which browser Windows selects.
- Every browser URL uses `127.0.0.1` and the actual active dynamic port.
- No fixed, guessed, scanned, or stale port is used for browser opening.
- First-start browser opening occurs only after server readiness and valid runtime-state publication.
- First-start application URLs are selected according to REQ-010.
- A repeated start does not start another HTTP server or request another port.
- A repeated start reuses the existing active local port and reevaluates REQ-010.
- A repeated start hands the resulting URLs to Windows and exits normally.
- Changing the Windows default browser is respected on a later start action without restarting Mini Server.
- Multiple URLs are submitted in the caller-provided start-site order.
- Mini Server does not attempt to control browser tab, window, or process reuse.
- Failure to open one URL does not prevent attempts for later valid URLs.
- Browser-launch failure does not stop the server, invalidate runtime state, change the active port, release the instance lock, start another server, or modify persistence.
- A useful concise diagnostic includes the affected local URL when practical.
- Closing the selected browser does not intentionally stop Mini Server.
- Existing `stop.bat` and authenticated graceful-stop behavior remain unchanged.
- Existing runtime coordination, static routing, application discovery, persistence, and server-lifetime behavior remain unchanged.
- The implementation and browser-opening mechanism remain compatible with Java 8.

## Constraints

The browser-opening implementation must remain small, Windows-oriented, and browser-independent.

The exact Java 8-compatible mechanism used to ask Windows to open an HTTP URL may be selected during implementation preparation, provided it conforms to this requirement and D-025.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-020 — Local Per-User/Computer Runtime Instance
- D-024 — Detached Windows Start and Authenticated Local Stop
- D-025 — Windows Default Browser Launch

## Related Requirements

- REQ-006 — Startup, Browser Launch, and Server Lifetime (released with v1.0.0)
- REQ-010 — Configurable Start Sites

## Related Architecture

See `docs/ARCHITECTURE.md`, especially:

- Start-Site Configuration
- Startup and Browser Launch
- Network Boundary

## Related Tasks

- T-015 — Implement Default Browser Launch
- T-017 — Verify v1.1 Release Scope

## Target Release

v1.1
