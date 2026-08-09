# REQ-006 — Startup and Browser Launch

## Requirement ID

REQ-006

## Title

Startup and Browser Launch

## Status

Active

## Purpose

Mini Server should provide a simple startup flow in which the local server is started first and the browser is then opened automatically with the correct local application URL.

The user should not need to determine the assigned TCP port manually.

## Description

The normal startup sequence must be:

1. Start Mini Server.
2. Bind the server to the local loopback interface.
3. Request an automatically assigned TCP port.
4. Determine the actual port assigned by the operating system.
5. Confirm that the server is ready to accept requests.
6. Construct the local application URL.
7. Open Microsoft Edge with that URL.

The browser must not be opened before the server is ready.

## Local Server Address

For the normal Windows desktop use case, the server URL must use:

http://127.0.0.1:<assigned-port>/

The port is determined dynamically during startup.

No fixed port number may be assumed by the launcher.

## Start Application

The launcher must be able to open a configured start application below the web root.

Example:

www/example/

would result in a URL such as:

http://127.0.0.1:<assigned-port>/example/

The exact default start application should be configurable or defined by the distribution without requiring changes to the server implementation.

The launcher must construct the URL from the actual runtime port rather than storing a complete fixed URL.

## Browser

Microsoft Edge is the intended browser for the Windows target environment.

The launcher should start the normal installed Edge browser.

It must not require a special browser profile, kiosk mode, embedded browser engine, or custom browser runtime.

Opening the Mini Server application must not prevent the user from using the same Edge instance for normal browser tabs.

## Browser Launch Timing

The launcher must only attempt to open the browser after Mini Server has successfully started and the assigned port is known.

The startup sequence must avoid a race condition where the browser attempts to access the application before the server is ready.

## Existing Browser Instance

If Microsoft Edge is already running, opening the Mini Server URL may reuse the existing browser instance according to normal Edge behavior.

Mini Server does not need to manage browser processes beyond requesting that the target URL be opened.

## Startup Failure

If Mini Server cannot start successfully, the launcher must not open the application URL.

The user should receive a meaningful error indication.

Possible startup failures include:

- The local server cannot be created.
- The loopback interface cannot be bound.
- The web root cannot be accessed.
- Required startup resources are missing.
- Another unrecoverable initialization error occurs.

A failed startup must not be presented as successful.

## Browser Launch Failure

Failure to launch Microsoft Edge must not cause the running Mini Server process to corrupt data or terminate unexpectedly.

If the server starts successfully but Edge cannot be launched, the user should receive enough information to open the application manually.

The manually usable URL should contain the actual assigned runtime port.

## Server Lifetime

The initial implementation may keep the server running independently after Edge has been opened.

Closing the browser or the Mini Server tab must not automatically be interpreted as a request to modify or delete application data.

The exact shutdown mechanism may be finalized during implementation.

## Multiple Starts

Starting another Mini Server instance while one instance is already running must not rely on the previous instance's port.

Each new instance must request its own available port from the operating system.

Because the server uses dynamically allocated ports, different instances should not normally fail solely because another Mini Server process is already listening.

## User Experience

The intended user experience is simple:

The user starts Mini Server with one action.

The server starts in the background.

Microsoft Edge opens automatically with the selected local web application.

The user should not need to:

- Select a TCP port
- Check whether a predefined port is free
- Start a separate web server manually
- Copy a generated URL manually
- Configure Edge for normal use

## Acceptance Criteria

REQ-006 is fulfilled when all of the following are true:

- Mini Server can be started through a single normal user action.
- The server starts before the browser is opened.
- The server binds to 127.0.0.1 for the normal local use case.
- The server requests an automatically assigned TCP port.
- The actual assigned port is determined after startup.
- The launcher constructs the browser URL using the assigned port.
- A configured start application can be included in the URL.
- Microsoft Edge is opened with the resulting local URL.
- No fixed TCP port is required.
- The browser is not opened with the application URL when server startup fails.
- The browser is not opened before the server is ready to accept requests.
- An already running Edge installation can continue to operate normally.
- Other Edge tabs remain usable.
- Browser launch failure does not corrupt server data.
- If browser launch fails, the actual local server URL can still be determined for manual use.
- Multiple Mini Server processes do not depend on sharing the same fixed port.

## Constraints

The normal target environment is Windows with Microsoft Edge available.

Startup must not require administrator privileges under normal conditions.

The launcher should remain small and should avoid unnecessary external dependencies.

The server itself must remain compatible with the project's approved Java runtime requirements.

The implementation must not rely on port scanning.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-004 — Browser Launch Uses the Assigned Port
- D-014 — Not Intended for Public Internet Use

## Related Requirements

- REQ-001 — Static File Serving
- REQ-002 — Dynamic Port Allocation

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Runtime Structure
- Network Boundary
- Architectural Principles

## Related Tasks

No implementation tasks have been assigned yet.

## Target Release

Initial release