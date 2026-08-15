# REQ-008 — Runtime and Distribution

## Requirement ID

REQ-008

## Title

Runtime and Distribution

## Status

Released

## Purpose

Mini Server must be easy to run on the intended Windows target systems without requiring unnecessary installation steps, administrator privileges, or additional server software.

The initial implementation must remain compatible with the approved Java 8 runtime environment.

## Description

The first Mini Server implementation is a Java application.

It must run on systems that provide a compatible Java 8 runtime.

The distribution should remain small and self-contained apart from the required Java runtime and the web application files stored below the Mini Server web root.

The distribution may reside on a local disk, shared network drive, or group drive and may be used concurrently from different computers.

The user should not need to install or configure:

- A separate web server
- A database server
- An application server
- A container runtime
- A JavaScript runtime
- Additional background services

The server should be startable with a simple user action on Windows.

## Java Runtime Compatibility

The initial Mini Server implementation must remain compatible with Java 8.

Production source code, build configuration, runtime dependencies, and build components that participate in the Java 8 build or runtime must not require a newer Java version.

Development may use a newer JDK.

A successful build or test run on a newer JDK does not replace final verification against the approved Java 8 runtime.

The Maven build must enforce the approved Java target.

## Build System

Mini Server v1.0 uses Maven as its authoritative Java build system.

The authoritative build configuration is located at:

    pom.xml

in the repository root.

The Maven build is responsible for:

- Compiling production Java source code
- Compiling automated Java tests
- Managing approved Java dependencies
- Running automated Java tests
- Producing Java build artifacts
- Supporting the distribution packaging process

Scripts and IDE configuration must not introduce an independent competing build definition.

Convenience scripts may invoke Maven.

## Java Dependencies

External Java dependencies may be introduced when they provide a clear technical benefit.

Any runtime dependency used by Mini Server v1.0 must:

- Support Java 8
- Have a clear project purpose
- Be suitable for redistribution
- Not require a separately installed application server or service
- Not introduce unnecessary runtime complexity
- Be declared through the authoritative Maven build configuration

The project should maintain a small and justified dependency footprint.

Large application frameworks should not be introduced merely for convenience when the required functionality can remain small and understandable.

Exact dependency versions are selected during implementation preparation and must remain consistent with the approved Java target and active requirements.

## Source and Build Layout

Production Java source code belongs below:

    src/main/java/

The base Java package is:

    io.github.madebyzwen.miniserver

Automated Java test source code belongs below:

    src/test/java/

The top-level:

    tests/

directory is used for test documentation and other test-related project material and is not the Maven Java test source directory.

Generated Maven output such as:

    target/

is disposable build output and must not be committed as project source.

## Reusable Template Source

The maintained source used to produce the reusable starter template is stored in the repository at:

    template/

This directory is development and packaging input.

It is not part of the runtime web root.

The reusable template source is packaged as:

    miniweb-template.zip

for distribution.

The normal distribution must not require a permanent:

    www/template/

application.

A developer may extract or copy the packaged template into a new first-level application directory such as:

    www/my-app/

## Packaging

The Java implementation must be distributable in a form that can be copied to the target system and started without requiring a traditional system-wide installer.

The distribution must contain everything required by Mini Server except the Java 8 compatible runtime already expected on the target system and Microsoft Edge already expected on the Windows target system.

The distribution must include:

- The Mini Server Java runtime artifact or artifacts
- All required runtime dependencies
- The `www/` web root
- The maintained example application
- The shared browser-side MiniApi library
- `miniweb-template.zip`
- The required Windows startup mechanism or launcher files

A representative distribution structure is:

    mini-server/
    ├── mini-server.jar
    ├── miniweb-template.zip
    ├── www/
    │   ├── _shared/
    │   │   └── mini-api.js
    │   └── example/
    │       ├── index.html
    │       ├── assets/
    │       └── data/
    │           └── data.json
    └── startup files

If runtime dependencies are not packaged inside the primary Java artifact, the distribution may contain an additional dependency directory such as:

    lib/

The exact executable-JAR strategy, dependency bundling mechanism, launcher filenames, and Maven packaging plugin configuration may be selected during implementation preparation.

Those implementation choices must not change the distribution behavior defined by the active requirements.

The repository's development-only:

    template/

directory does not need to be copied into the normal runtime distribution when `miniweb-template.zip` has been produced from it.

Machine- and process-specific runtime coordination state is not part of the distribution and must not be created below the installation root.

Mini Server creates local per-user/computer runtime state below:

    %LOCALAPPDATA%\MiniServer\runtime\

## Web Root

The distributed package must contain:

    www/

as the runtime web root.

The web root remains directly accessible as normal files in the distribution and is not hidden inside the Java source tree.

The server must locate its web root predictably relative to the Mini Server installation unless an explicitly supported configuration mechanism is introduced.

Normal use must not require the user to configure an absolute filesystem path manually.

The web root contains:

    www/_shared/

for shared browser-side functionality and first-level application directories such as:

    www/example/

Additional valid application directories may be added without application-specific Java server code changes.

Shared persistent data below:

    www/<site>/data/

must remain protected from normal static file serving as defined by the persistence requirements.

Private persistence remains outside the installation below:

    %APPDATA%\MiniServerData\<site>\data\

## Windows Startup

The intended initial runtime platform is Windows.

The distribution must provide a simple user-facing method to start Mini Server.

Normal use should require one action, such as a desktop shortcut or launcher.

The Java implementation is responsible for the startup behavior defined by D-020 and REQ-006, including:

- Per-user/computer single-instance handling
- Local runtime-state handling
- Loopback-only server startup
- Requesting TCP port `0`
- Obtaining the operating-system-assigned port
- Repeated-start detection
- Reusing the active server instance
- Opening Microsoft Edge with the correct active local URL

The launcher must not implement an independent port-selection or server-instance mechanism that competes with the Java implementation.

The user must not need to open a command prompt or manually construct the Mini Server URL during normal operation.

## User Privileges

Normal operation must not require administrator privileges.

The server runs with the permissions of the current user.

Shared and private files written through the JSON persistence API are therefore subject to the filesystem permissions of that user at their respective locations.

If required files or directories are not writable, Mini Server must report the failure rather than attempting to elevate privileges automatically.

## Installation

A traditional system-wide installation is not required for Mini Server v1.0.

The application must be suitable for deployment by copying the distribution directory to a user-accessible local or shared/network location.

Multiple users on different computers may use one copied distribution concurrently. Each computer/user context runs its own loopback server and uses its own local runtime coordination state.

Independent copied Mini Server installations may also operate separately.

Mini Server must not require changes to system-wide web server configuration.

Normal deployment must not require registration of a system-wide web service.

## Configuration

The initial implementation should require as little configuration as possible.

Normal startup must not require configuration of a fixed TCP port.

The server always obtains a new operating-system-assigned port when a new server instance is started.

Application-specific server configuration must not be required merely because another valid first-level application directory is added below:

    www/

Mini Server supports a configured browser start target as defined by REQ-006.

The exact representation of that start-target configuration may be selected during implementation preparation.

Configuration options must not allow normal operation to bypass the loopback-only binding, dynamic-port behavior, application scoping, or persistence-path protections defined by the active requirements.

## Browser Dependency

Microsoft Edge is the intended browser for the initial Windows runtime.

Mini Server uses the normally installed Microsoft Edge application rather than bundling its own browser engine.

The server remains independent from Edge.

Closing Edge does not intentionally terminate the Mini Server Java process.

A repeated start within the same local user/computer context may reopen the already running local Mini Server instance in Edge using its published local runtime port.

## Network Requirements

The normal local deployment must not require external network access.

Mini Server serves its applications through:

    127.0.0.1

and does not require an internet connection for its own server functionality.

A newly started server requests:

    TCP port 0

and lets the operating system select an available local port.

Mini Server must not depend on:

- A fixed server port
- Manual port scanning
- Public internet reachability
- A separately installed web server

Hosted applications may independently require network access if their own functionality uses external resources.

Such application-specific network requirements are outside the Mini Server runtime requirement.

## Application Data

Every persistence operation explicitly selects shared or private scope.

Shared application persistence is stored at:

    <installation-root>\www\<site>\data\data.json

Private application persistence is stored at:

    %APPDATA%\MiniServerData\<site>\data\data.json

Both use the same section-based JSON structure and API semantics. There is no default persistence scope.

Private means user-profile storage rather than a security boundary.

The distribution model must not require:

- A database
- A database server
- A separate persistence service

Application persistence remains ordinary file-based data.

The persistence files may be backed up, copied, or inspected directly through the filesystem when user permissions allow it.

Persistence data must not be exposed through Mini Server's normal static HTTP file serving.

## Runtime State

Per-user/computer runtime coordination state is stored locally at:

    %LOCALAPPDATA%\MiniServer\runtime\

The runtime state uses:

    startup.lock
    instance.lock
    instance.json

Runtime state is local process state and is not distribution or installation content.

The local runtime directory may be created automatically when Mini Server starts.

Runtime state must not be packaged as authoritative state from a development or build environment.

A stale runtime-state file alone must never be interpreted as proof of a running Mini Server instance.

No runtime lock or port state may be shared through the installation directory.

## Portability of Web Applications

A Mini Server web application is represented by a first-level directory below:

    www/

A valid application directory should normally be movable or copyable between compatible Mini Server installations without requiring application-specific Java server changes.

Shared Mini Server functionality remains provided by:

- The central Java server implementation
- The shared browser-side `mini-api.js` library
- The standard persistence API contract

Shared persistence may travel with the application's directory when that is intentionally desired. Private persistence remains in the current user's profile.

The reusable starter template provides a clean basis for creating additional portable applications.

## Future Runtime Implementations

Equivalent implementations using other runtime environments, such as Python or .NET, may be developed in the future.

They are not part of the initial Java implementation requirement.

Where practical, future implementations should preserve:

- The Mini Server web application directory model
- The external HTTP persistence API contract
- The public MiniApi behavior
- The file-based application persistence model

This allows existing Mini Server web applications to remain reusable across compatible runtime implementations.

## Acceptance Criteria

REQ-008 is fulfilled when all of the following are true:

- The initial implementation runs on the approved Java 8 compatible runtime.
- The produced application does not require a newer Java runtime.
- Maven is the authoritative Java build system.
- The authoritative build configuration is the root `pom.xml`.
- Production Java source code uses the Maven `src/main/java/` source tree.
- Automated Java tests use the Maven `src/test/java/` test source tree.
- The Maven build enforces the approved Java target.
- Required runtime dependencies are declared through Maven.
- Runtime dependencies remain compatible with Java 8.
- External dependencies are kept to a justified minimum.
- Generated Maven output is not treated as project source.
- Normal operation does not require administrator privileges.
- No separate web server must be installed.
- No database server must be installed.
- No application server must be installed.
- No container runtime is required.
- The application can be deployed without a traditional system-wide installer.
- The distribution includes the required Mini Server Java runtime artifact or artifacts.
- The distribution includes all required runtime dependencies.
- The distribution includes the `www/` web root.
- The distribution includes the maintained example application.
- The distribution includes the shared `mini-api.js` library.
- The distribution includes `miniweb-template.zip`.
- The normal distribution does not require a permanent `www/template/` application.
- The reusable template package is produced from the maintained top-level `template/` source.
- The top-level `template/` source is not treated as a hosted runtime application.
- The distribution contains no authoritative runtime lock or port state.
- Local runtime state is created under `%LOCALAPPDATA%\MiniServer\runtime\`.
- Normal startup can be performed through a simple Windows user action.
- The user does not need to configure a fixed TCP port.
- The startup mechanism does not perform manual port scanning.
- Microsoft Edge can be opened with the actual active local server URL.
- A shared/network installation can be used concurrently from different computers.
- A repeated start reuses or detects the existing server instance in the same local user/computer context.
- Closing Edge does not intentionally stop Mini Server.
- Mini Server can operate locally without internet access.
- Application persistence remains file-based with mandatory shared or private scope.
- Shared persistence uses `<installation-root>\www\<site>\data\data.json`.
- Private persistence uses `%APPDATA%\MiniServerData\<site>\data\data.json`.
- Persistent application data is not exposed through normal static file serving.
- New valid application directories can be added below `www/` without application-specific Java server changes.
- Independent copied Mini Server installations may operate separately.

## Constraints

The initial implementation targets Windows systems with an available Java 8 compatible runtime.

The project must remain lightweight and understandable.

Runtime packaging must not introduce unnecessary infrastructure requirements.

Maven remains the authoritative Java build system.

The implementation should use Java standard functionality where it provides a reasonable and maintainable solution.

External dependencies must have a clear technical purpose.

Public internet deployment is outside the supported runtime and distribution model.

The normal runtime package must preserve the loopback-only, dynamic-port, local per-user/computer single-instance, and explicitly scoped file-based persistence architecture.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-002 — Local Loopback Binding
- D-003 — Dynamic Port Allocation
- D-004 — Browser Launch Uses the Assigned Port
- D-005 — No Database
- D-012 — Example Application and Reusable Template Package
- D-013 — English Repository Language
- D-014 — Not Intended for Public Internet Use
- D-015 — Persistence Data Is Not Served as Static Content
- D-019 — Maven Build and Project Source Structure
- D-020 — Local Per-User/Computer Runtime Instance
- D-021 — Explicit Shared and Private Persistence Scopes
- D-022 — Explicitly Scoped Persistence API Contract
- D-023 — Concurrency-Safe Persistence Writes

## Related Requirements

- REQ-001 — Static File Serving
- REQ-002 — Dynamic Port Allocation
- REQ-003 — JSON Persistence API
- REQ-004 — JavaScript Client API
- REQ-005 — Example Application and Template Package
- REQ-006 — Startup, Browser Launch, and Server Lifetime
- REQ-007 — Error Handling and File Permissions

## Related Architecture

See:

    docs/ARCHITECTURE.md

Relevant sections include:

- Storage and Runtime Boundaries
- Web Application Model
- Example Application and Template
- Network Boundary
- Startup and Browser Launch
- Architectural Principles

## Related Tasks

See:

    tasks/ACTIVE.md

Relevant implementation tasks include:

- T-001 — Create Initial Java Project Structure
- T-002 — Implement Dynamic Local Server Startup
- T-008 — Create Example Application
- T-009 — Create Reusable Template Package
- T-010 — Implement Edge Browser Launch
- T-012 — Verify Java 8 Runtime Compatibility
- T-014 — Verify Initial Release Scope

## Target Release

v1.0
