# REQ-002 — Dynamic Port Allocation

## Requirement ID

REQ-002

## Title

Dynamic Port Allocation

## Status

Active

## Purpose

Mini Server must start without relying on a permanently configured TCP port.

The operating system must automatically select an available local port when the server starts.

## Description

The server must request TCP port:

0

when creating its listening socket.

Port 0 instructs the operating system to assign an available local TCP port automatically.

After the server has started successfully, the implementation must determine the actual port assigned by the operating system.

The assigned port must then be used for all startup-related URLs and browser launch operations.

The implementation must not perform manual port scanning in order to find a free port.

## Binding Address

For the normal local desktop use case, the server must bind to:

127.0.0.1

The dynamically assigned port therefore results in a local address such as:

http://127.0.0.1:49152/

The actual port number may be different on every server start.

## Port Discovery

The server must obtain the assigned port from the active server socket after binding.

The port must not be guessed, calculated, or assumed.

The application must only continue with operations that require the server URL after the actual port has been determined successfully.

## Browser Integration

When the launcher opens the browser automatically, it must use the dynamically assigned port.

Example:

http://127.0.0.1:<assigned-port>/<site>/

The browser must not be started with a fixed or previously remembered port.

## Port Conflicts

Because the operating system selects an available port, normal startup must not fail merely because a previously used port is occupied by another application.

The implementation must not terminate simply because one particular predefined application port is unavailable.

## Failure Handling

If the server cannot bind to the local interface or no listening socket can be created, startup must fail cleanly.

The error should provide enough information to indicate that the local server could not be started.

The implementation must not open the browser when the server itself failed to start.

## Acceptance Criteria

REQ-002 is fulfilled when all of the following are true:

- The server requests TCP port 0 during startup.
- The operating system assigns an available local TCP port.
- The actual assigned port is read from the active server socket.
- No manual port scanning is used.
- No fixed application port is required.
- The server can start even when a port used during a previous run is occupied.
- The assigned port is used when constructing the local server URL.
- Automatic browser launch uses the actual assigned port.
- The browser is not opened if server startup fails.
- The normal local server address uses 127.0.0.1.

## Constraints

The implementation must remain compatible with Java 8.

The solution should use the networking capabilities provided by the Java runtime and operating system without introducing an unnecessary external dependency for port discovery.

The assigned port is runtime state and must not be treated as a persistent configuration value.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-004 — Browser Launch Uses the Assigned Port
- D-014 — Not Intended for Public Internet Use

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Network Boundary
- Architectural Principles

## Related Tasks

See:

    tasks/ACTIVE.md

Relevant implementation tasks include:

- T-002 — Implement Dynamic Local Server Startup
- T-010 — Implement Edge Browser Launch
- T-013 — Add Automated Tests

## Target Release

v1.0