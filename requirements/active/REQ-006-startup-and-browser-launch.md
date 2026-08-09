# REQ-006 — Startup, Browser Launch, and Server Lifetime

## Requirement ID

REQ-006

## Title

Startup, Browser Launch, and Server Lifetime

## Status

Active

## Purpose

Mini Server must provide a simple startup experience for the local user.

A normal start must launch the local server, determine its operating-system-assigned TCP port, and open Microsoft Edge with the correct local Mini Server URL.

Repeated starts of the same installation must reuse an already running server instance instead of creating additional competing server processes.

## Description

Mini Server is intended to be started through a simple user action such as a desktop shortcut or equivalent launcher.

The user must not need to:

- Select a TCP port
- Check whether Mini Server is already running
- Start Microsoft Edge manually
- Enter the local server address manually
- Stop an existing Mini Server instance before opening it again

Mini Server manages these details automatically.

## Network Binding

A newly started Mini Server instance must bind only to:

    127.0.0.1

The server must request TCP port:

    0

The operating system selects an available local TCP port.

Mini Server must not:

- Use a permanently configured server port
- Scan a range of ports looking for a free port
- Bind the normal server listener to external network interfaces

After successful startup, Mini Server must obtain the actual port assigned by the operating system.

## Single Instance per Installation

Only one Mini Server server process may run for one installation at a time.

The single-instance scope is the Mini Server installation, not the entire computer.

Therefore two independent Mini Server installations may run simultaneously, each with:

- Its own installation directory
- Its own runtime state
- Its own operating-system-assigned TCP port
- Its own hosted applications and persistence data

Two processes belonging to the same installation must not operate as independent server instances at the same time.

## Runtime State

Mini Server must maintain local runtime information outside the web root.

The intended location is:

    <installation-root>/.runtime/

Runtime information includes an exclusive instance lock and the state required to reopen an already running Mini Server instance.

For example:

    <installation-root>/.runtime/instance.lock
    <installation-root>/.runtime/instance.json

The runtime state must contain at least the currently assigned server port once startup has completed successfully.

Runtime files must not be stored below:

    www/

and must not be available through normal static HTTP requests.

The exact internal serialization of runtime state may remain an implementation detail.

## Instance Lock

Mini Server must use an exclusive process-owned lock to determine whether the current installation already has a running server instance.

The active lock is authoritative.

The existence of a runtime state file by itself must not be treated as proof that a Mini Server process is still running.

If the lock can be acquired, no other running Mini Server process currently owns that installation.

If the lock cannot be acquired because another process owns it, the current process must behave as a repeated start.

## First Start

When no Mini Server instance is already running for the installation, startup must proceed in this order:

1. Acquire the exclusive installation instance lock.
2. Invalidate stale runtime state from a previous execution.
3. Start the HTTP server bound to `127.0.0.1`.
4. Request TCP port `0`.
5. Obtain the actual port assigned by the operating system.
6. Publish the assigned port in the current runtime state.
7. Treat the server as ready.
8. Construct the browser URL using `127.0.0.1`, the assigned port, and the configured Mini Server start target.
9. Open Microsoft Edge with that URL.
10. Continue running as the active Mini Server process.

For example, if the operating system assigns port:

    51847

and the configured start target is:

    /example/

the resulting URL is:

    http://127.0.0.1:51847/example/

Microsoft Edge must not be opened with a guessed or predetermined port.

The browser should be launched only after the server is ready to accept requests.

## Configured Start Target

Mini Server must support a defined application path that is opened after startup.

The startup URL consists of:

    http://127.0.0.1:<assigned-port><start-target>

The exact configuration mechanism for selecting the start target may be defined by the distribution and build structure.

The configured target must resolve to content served by the current Mini Server installation.

The start target must not alter the server's loopback-only network binding or dynamic-port behavior.

## Repeated Start

If the same Mini Server installation is started again while its server process is already running, the second process must not start another HTTP server.

Instead, the repeated start must:

1. Detect that another process owns the installation instance lock.
2. Obtain the runtime state published by the active instance.
3. Read the currently assigned server port.
4. Construct the browser URL using the existing server instance.
5. Open Microsoft Edge with that URL.
6. Exit without becoming another server process.

For example, if the existing instance is listening on:

    127.0.0.1:51847

the repeated start must reuse:

    http://127.0.0.1:51847/<start-target>

It must not request another operating-system-assigned port for a second server instance.

## Startup Race

A repeated start may occur while the first process:

- Already owns the instance lock
- Is still starting the HTTP server
- Has not yet published valid runtime state

The repeated start must not treat missing or incomplete runtime state during this phase as permission to create another server instance.

It may wait and retry for valid runtime state for a short bounded period.

If the active lock remains owned but valid runtime state cannot be obtained within that bounded period, the repeated start must fail with a clear diagnostic message.

It must not start a competing Mini Server process.

The exact retry timing may be chosen during implementation as long as the wait remains bounded and the single-instance guarantee is preserved.

## Stale Runtime State

Runtime state may remain after abnormal process termination.

A stale state file must not permanently block Mini Server startup.

When no process owns the instance lock:

- A new instance may acquire the lock
- Old runtime state must be invalidated
- A new dynamic port must be requested
- Fresh runtime state must be published only after successful server startup

A stale port value must never be reused merely because it remains in a state file.

## Browser Launch

Mini Server v1.0 targets Microsoft Edge on Windows for the normal distribution startup experience.

The browser launch must use the normal installed Microsoft Edge application.

Mini Server does not require a dedicated embedded browser.

Opening Mini Server must not prevent the user from using the same Edge instance for unrelated normal browser tabs or windows.

Repeated starts may open the Mini Server start URL again in Edge.

The exact Edge process behavior, such as whether the operating system reuses an existing Edge process or creates another one, does not need to be controlled by Mini Server.

## Server Lifetime

The lifetime of Mini Server is independent of individual Edge windows and tabs.

Closing:

- The Mini Server browser tab
- An Edge window
- All visible Edge windows

must not intentionally stop the Mini Server Java process.

The server continues running until its Java process ends.

Examples include:

- User logoff
- Operating-system shutdown
- Explicit process termination
- Fatal Mini Server process failure

## No HTTP Shutdown Endpoint

Mini Server v1.0 must not expose a normal browser-accessible HTTP operation for shutting down the server.

In particular, hosted applications must not receive a persistence-style API endpoint capable of terminating the Mini Server process.

Closing the browser is not a server shutdown command.

A future controlled shutdown mechanism may be considered separately if a later requirement introduces one.

## Process Termination and Lock Release

The exclusive instance lock must be owned by the running Mini Server process.

When that process terminates, the operating system must release the process-owned lock.

This allows a later Mini Server process to start normally.

Unexpected termination may leave stale state files behind, but stale state without an actively owned lock must not block subsequent startup.

## Persistence Safety

The single-instance mechanism also protects persistence data from competing independent Mini Server processes belonging to the same installation.

Under normal operation, two server processes must not simultaneously modify:

    www/<site>/data/data.json

for the same installation.

Concurrency that occurs inside one running Mini Server process remains subject to the persistence integrity and synchronization requirements defined elsewhere.

## Startup Errors

If Mini Server cannot complete startup, it must not open Edge with a URL that is known to be unusable.

Startup must fail with a clear diagnostic message when required operations fail, including failures such as:

- The instance lock cannot be handled correctly
- The HTTP server cannot bind to loopback
- The operating system does not provide a usable port
- Required installation resources cannot be accessed
- Valid runtime state cannot be obtained during a repeated-start race
- Microsoft Edge cannot be launched

A browser launch failure does not change the server's network binding or dynamic port.

The exact user-facing diagnostic presentation may be defined by the error-handling requirement and implementation.

## Acceptance Criteria

REQ-006 is fulfilled when all of the following are true:

- Mini Server binds only to `127.0.0.1`.
- A newly started server requests TCP port `0`.
- No fixed TCP port is required.
- Mini Server does not scan for free ports.
- The actual operating-system-assigned port is obtained after server startup.
- Runtime lock and state data are stored outside `www/`.
- Runtime state is not served as static web content.
- Only one server process may run per Mini Server installation.
- Independent Mini Server installations can run simultaneously.
- The first process acquires the installation instance lock before starting the server.
- Stale runtime state is invalidated before a new instance publishes its current port.
- The assigned port is published only after successful server startup.
- Edge is opened using the actual assigned port.
- The configured application start target is included in the browser URL.
- A repeated start does not create a second server instance.
- A repeated start reuses the currently running instance's port.
- A repeated start opens Edge with the existing server URL.
- A repeated-start race does not result in a competing server process.
- A stale runtime state file alone does not prevent a new startup.
- The active instance lock is authoritative for determining whether the installation is already running.
- Closing Edge does not intentionally stop Mini Server.
- Mini Server continues running independently of browser windows and tabs.
- Mini Server v1.0 exposes no normal HTTP shutdown endpoint.
- Process termination releases the process-owned instance lock.
- Abnormal termination does not permanently block future startup.
- Two independent Mini Server processes do not normally write to the same installation's persistence files concurrently.
- Startup failures are not reported as successful startup.
- Edge is not intentionally opened with a known invalid server URL.

## Constraints

The startup implementation must remain compatible with the project's approved Java runtime requirements.

Normal operation must not require administrator privileges.

The dynamic-port behavior defined by the project architecture must be preserved.

The single-instance mechanism must be scoped to the current Mini Server installation.

Runtime state must remain outside the web root.

The implementation must not introduce a public or externally reachable server listener.

The implementation must follow D-018.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-014 — Not Intended for Public Internet Use
- D-018 — Single Running Instance and Server Lifetime

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant areas include:

- Network Boundary
- Runtime Structure
- Startup and Browser Launch
- Persistence Safety

## Related Tasks

See:

tasks/ACTIVE.md

Relevant implementation tasks must cover:

- Dynamic loopback startup
- Operating-system-assigned port handling
- Per-installation instance locking
- Runtime state publication
- Repeated-start behavior
- Microsoft Edge launch
- Startup diagnostics

## Target Release

v1.0