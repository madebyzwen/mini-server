# REQ-006 — Startup, Browser Launch, and Server Lifetime

## Requirement ID

REQ-006

## Title

Startup, Browser Launch, and Server Lifetime

## Status

Active

## Purpose

Mini Server must start a local loopback server, obtain its operating-system-assigned port, and open Microsoft Edge with the correct URL.

Repeated starts within the same local user/computer context must reuse or detect the active local instance. Users on different computers must be able to run the same shared installation concurrently.

## Shared Installation and Local Server

A Mini Server installation may reside on a local disk, network drive, or group drive.

Examples of supported concurrent use include:

- User A on Computer A and User B on Computer B running the same physical installation
- One person running the shared installation on a laptop and in a separate VDI environment
- Different Windows users running the same shared installation in their own user/computer contexts

The installation is shareable. Each HTTP server and its runtime coordination state remain local.

No machine- or process-specific runtime lock or port state may be stored in or coordinated through the installation directory.

## Network Binding

Every newly started server binds exclusively to:

    127.0.0.1

and requests:

    TCP port 0

The operating system selects an available local port. Mini Server obtains the actual port from the active server socket after successful startup.

Mini Server must not use a fixed port, scan a port range, or bind the normal server listener to an external interface.

## Single Local Instance

Only one Mini Server server process may run within one local user/computer context.

This scope is not the installation. Different computers do not block each other merely because they use the same shared distribution.

A repeated start in the same local context must reuse or detect the already running local server rather than start another.

## Local Runtime State

Runtime coordination state is stored at:

    %LOCALAPPDATA%\MiniServer\runtime\

The required concepts are:

    startup.lock
    instance.lock
    instance.json

Responsibilities:

- startup.lock coordinates concurrent startup attempts within the local user/computer context.
- instance.lock is held by the active server process for its lifetime and is authoritative for whether the local instance is active.
- instance.json stores repeated-start information, including the assigned local TCP port.

Runtime files must not be stored below www, served as static content, or shared through the installation.

A state file alone is never proof that an instance is active.

## First Local Start

When no active local instance exists:

1. Obtain startup.lock using a bounded wait.
2. Determine that no active process owns instance.lock.
3. Invalidate stale instance.json state.
4. Obtain instance.lock and retain it for the server lifetime.
5. Start the HTTP server on 127.0.0.1 using port 0.
6. Obtain the actual operating-system-assigned port.
7. Confirm that the server is ready to accept requests.
8. Publish the assigned port in instance.json.
9. Release startup.lock after local startup state is stable.
10. Construct the browser URL from 127.0.0.1, the assigned port, and configured start target.
11. Open Microsoft Edge with that URL.
12. Continue running while retaining instance.lock.

If startup fails after instance.lock was acquired, the process must cleanly release its resources and must not publish a usable state or open Edge with a known-invalid URL.

## Configured Start Target

The startup URL is:

    http://127.0.0.1:<assigned-port><start-target>

For a start target of /example/ and an assigned port of 51847:

    http://127.0.0.1:51847/example/

The exact configuration representation may be selected during implementation. It must identify content served by the current installation and must not alter the loopback or dynamic-port rules.

## Repeated Local Start

A repeated startup attempt first obtains startup.lock using a bounded wait.

If another local process owns instance.lock, the repeated start:

1. Does not start another HTTP server.
2. Obtains valid instance.json state for the active local process.
3. Reads the active local port.
4. Constructs the URL using that port and the configured start target.
5. Releases startup.lock.
6. Opens Microsoft Edge with the existing local URL.
7. Exits without becoming a server process.

The repeated start must not use a port published by a different computer or an installation-level state file.

## Startup Race and Timeouts

Concurrent local startup attempts and repeated-start state discovery must be bounded and deterministic.

A process may encounter an active instance.lock before valid instance.json state is available. It may wait and retry for a short bounded period while coordinating through startup.lock.

If a required runtime lock or valid state cannot be obtained within its bounded timeout, startup fails with a clear diagnostic. It must not hang indefinitely or start a competing local server.

Exact timeout durations may be selected during implementation.

## Stale Runtime State

Stale instance.json or lock files may remain after abnormal termination.

If no active process owns instance.lock, stale files must not block startup. A new process invalidates old state before publishing its own assigned port.

An old port is never reused solely because it remains in instance.json.

## Browser Launch

Mini Server v1 targets the normally installed Microsoft Edge browser on Windows.

Edge is opened only after a new local server is ready or a repeated start has obtained valid active-local-instance state.

Mini Server does not control whether Windows/Edge opens a tab, window, existing process, or new process.

If Edge launch fails after the server has started, the server may continue running. The valid local URL must be made available with a concise diagnostic so the user can open it manually.

## Server Lifetime

The Mini Server Java process is independent of Edge.

Closing a tab, one Edge window, or all Edge windows must not intentionally stop the server.

The server continues until its Java process ends because of logoff, shutdown, explicit termination, fatal failure, or another normal process-ending event.

Mini Server v1 has no browser-accessible HTTP shutdown endpoint.

## Process Termination

The active process owns instance.lock. The operating system releases the process-owned lock when that process terminates, including unexpected termination.

Stale runtime files may remain, but without an actively owned instance.lock they do not establish a running instance.

## Persistence Concurrency Boundary

Local runtime single-instance locking does not protect shared persistence from server processes on other computers.

Concurrency safety for shared and private data is provided by short-lived persistence-file locks and atomic writes as defined by D-023 and REQ-003.

Runtime locks and persistence locks are separate mechanisms and must not be conflated.

## Startup Errors

Startup fails clearly when required operations fail, including:

- Local startup or instance locks cannot be obtained within their bounded timeouts
- Valid repeated-start state cannot be obtained
- The HTTP server cannot bind to loopback
- The operating system does not provide a usable assigned port
- Required installation resources cannot be read
- Local runtime state cannot be handled
- Microsoft Edge cannot be launched

A failed operation must not be reported as successful startup. Edge must not be opened with a URL known to be invalid.

## Acceptance Criteria

REQ-006 is fulfilled when:

- Shared/network-drive installations are supported.
- Different computers can run the same physical installation concurrently.
- No runtime lock or port state is coordinated through the installation directory.
- Each server binds exclusively to 127.0.0.1 and requests port 0.
- No fixed port, port scanning, or external listener is used.
- The actual assigned port is read from the active server.
- Runtime state is stored under %LOCALAPPDATA%\MiniServer\runtime\.
- Runtime state includes startup.lock, instance.lock, and instance.json responsibilities.
- startup.lock coordinates local concurrent startup attempts.
- instance.lock is process-owned, lifetime-held, and authoritative.
- instance.json publishes the active assigned port.
- Only one server process runs per local user/computer context.
- A repeated local start does not start another server.
- A repeated local start obtains and reuses the active local port.
- Lock and state waits are bounded and deterministic.
- State alone is not treated as proof of an active process.
- Stale state does not permanently block startup.
- The assigned port is published only after readiness.
- Edge opens with the active local port and configured target.
- Browser-launch failure does not corrupt a successfully running server.
- Closing Edge does not intentionally stop Mini Server.
- Process termination releases the process-owned instance lock.
- Runtime locking is not claimed to protect cross-computer persistence writes.
- Persistence concurrency is delegated to the file-locking model.
- Startup failures are reported clearly and do not launch a known-invalid URL.

## Constraints

The startup implementation remains Java 8 compatible, lightweight, loopback-only, and usable without administrator privileges.

It follows D-020. It must not recreate installation-scoped runtime state or introduce migration behavior for the discarded implementation.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-004 — Browser Launch Uses the Assigned Port
- D-014 — Not Intended for Public Internet Use
- D-020 — Local Per-User/Computer Runtime Instance
- D-023 — Concurrency-Safe Persistence Writes

## Related Architecture

See docs/ARCHITECTURE.md, especially:

- Storage and Runtime Boundaries
- Network Boundary
- Startup and Browser Launch
- Persistence Concurrency

## Related Tasks

See tasks/ACTIVE.md, especially T-002, T-010, T-011, and T-013.

## Target Release

v1.0
