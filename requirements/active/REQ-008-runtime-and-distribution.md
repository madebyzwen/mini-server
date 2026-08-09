# REQ-008 — Runtime and Distribution

## Requirement ID

REQ-008

## Title

Runtime and Distribution

## Status

Active

## Purpose

Mini Server must be easy to run on the intended Windows target systems without requiring unnecessary installation steps, administrator privileges, or additional server software.

The initial implementation must remain compatible with the approved Java 8 runtime environment.

## Description

The first Mini Server implementation is a Java application.

It must run on systems that provide a compatible Java 8 runtime.

The distribution should remain small and self-contained apart from the required Java runtime and the web application files stored below the Mini Server web root.

The user should not need to install or configure:

- A separate web server
- A database server
- An application server
- A container runtime
- A JavaScript runtime
- Additional background services

The server should be startable with a simple user action on Windows.

## Java Runtime Compatibility

The implementation must remain compatible with Java 8.

Source code, build configuration, and dependencies must not require a newer Java runtime.

The target environment currently includes Java 8 compatible runtimes.

The project may be developed using a newer Java installation, but the produced application must still be executable with the supported Java 8 runtime.

## Java Dependencies

External Java dependencies should be avoided where the Java standard library provides sufficient functionality.

If an external dependency is required, it must:

- Support Java 8
- Have a clear purpose
- Be suitable for redistribution
- Not require a separate server installation
- Not introduce unnecessary runtime complexity

The project should prefer a small dependency footprint.

## Packaging

The Java implementation should be distributable in a form that can be copied to the target system and started without a traditional installer where practical.

The final package should contain everything required by Mini Server except the Java runtime already expected on the target system.

The exact packaging format may be finalized during implementation.

A typical distribution may contain:

mini-server/
├── mini-server.jar
├── www/
│   ├── _shared/
│   ├── example/
│   └── template/
└── startup files

The exact filenames may differ in the final implementation.

## Web Root

The distributed package must contain the `www/` directory used by the server.

The server should locate its web root predictably relative to the distributed application unless an explicitly supported configuration mechanism is introduced.

Normal use should not require the user to configure an absolute filesystem path manually.

## Windows Startup

The intended initial target platform is Windows.

The distribution should provide a simple method to start Mini Server.

The normal user experience should allow the user to start the application with one action, such as a desktop shortcut or launcher.

The startup mechanism should:

- Start the Java server
- Wait until the server is ready
- Use the dynamically assigned port
- Open the configured start application in Microsoft Edge

The user should not need to open a command prompt or manually construct the local URL during normal operation.

## User Privileges

Normal operation must not require administrator privileges.

The server should run with the permissions of the current user.

Files written through the JSON persistence API are therefore subject to the filesystem permissions of that user.

If required files or directories are not writable, the server must report the failure rather than attempting to elevate privileges automatically.

## Installation

A traditional system-wide installation should not be required for the initial version.

The application should be suitable for deployment by copying the distribution directory to an appropriate user-accessible location.

The exact deployment method may depend on the target environment.

Mini Server must not require changes to system-wide web server configuration.

## Configuration

The initial implementation should require as little configuration as possible.

Normal startup must not require configuration of a fixed TCP port.

Application-specific server configuration should not be required when another valid application directory is added below `www/`.

Configuration options introduced later should have sensible defaults where possible.

## Browser Dependency

Microsoft Edge is the intended browser on the initial Windows target environment.

Mini Server should use the normally installed Edge browser rather than bundling its own browser engine.

The server itself must remain separate from the browser and must continue to function as a normal local HTTP server.

## Network Requirements

The normal local deployment must not require external network access.

Mini Server must be able to serve its local applications through the loopback interface without an internet connection.

Hosted applications may independently require network access if their own functionality depends on external resources, but that is outside the Mini Server runtime requirement.

## Application Data

Application data remains inside the corresponding site's data directory.

For example:

www/example/data/data.json

The distribution model must not require a database or separate persistence service.

Application data should remain accessible as normal files for backup, copying, or inspection when filesystem permissions allow it.

## Portability of Web Applications

A Mini Server web application should be portable as a directory below `www/`.

A valid application directory should normally be movable or copyable into another Mini Server installation without requiring Java code changes.

Shared Mini Server functionality remains provided by the central server and shared JavaScript library.

## Future Runtime Implementations

Equivalent implementations using other runtime environments, such as Python or .NET, may be developed in the future.

These are not part of the initial Java implementation requirement.

Where practical, future implementations should preserve the same web application structure and external API behavior so that existing Mini Server web applications can be reused.

## Acceptance Criteria

REQ-008 is fulfilled when all of the following are true:

- The initial implementation runs on a compatible Java 8 runtime.
- The produced application does not require a newer Java runtime.
- Normal operation does not require administrator privileges.
- No separate web server must be installed.
- No database server must be installed.
- No application server must be installed.
- No container runtime is required.
- The distribution includes the required Mini Server web root content.
- The application can be deployed without requiring a traditional system-wide installer.
- Normal startup can be performed through a simple Windows user action.
- The user does not need to configure a fixed TCP port.
- Microsoft Edge can be launched with the dynamically generated local URL.
- Mini Server can operate locally without internet access.
- Application persistence remains file-based.
- New valid application directories can be added below `www/` without application-specific Java server changes.
- External dependencies, if any, remain compatible with Java 8 and are kept to a justified minimum.

## Constraints

The initial implementation targets Windows systems with an available Java 8 compatible runtime.

The project should remain lightweight.

Runtime packaging must not introduce unnecessary infrastructure requirements.

The Java implementation should rely on standard Java functionality wherever this provides a reasonable solution.

Public internet deployment is outside the supported runtime and distribution model.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-004 — Browser Launch Uses the Assigned Port
- D-005 — No Database
- D-013 — English Repository Language
- D-014 — Not Intended for Public Internet Use

## Related Requirements

- REQ-001 — Static File Serving
- REQ-002 — Dynamic Port Allocation
- REQ-003 — JSON Persistence API
- REQ-004 — JavaScript Client API
- REQ-005 — Example and Template Applications
- REQ-006 — Startup and Browser Launch
- REQ-007 — Error Handling and File Permissions

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Runtime Structure
- Web Application Model
- Network Boundary
- Architectural Principles

## Related Tasks

No implementation tasks have been assigned yet.

## Target Release

Initial release