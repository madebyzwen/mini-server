# Decisions

This document records approved technical and project decisions that should remain stable unless they are deliberately changed later.

Each decision should describe what was decided, why it was decided, and any important consequences.

---

## D-001 — Java 8 Compatibility

### Decision

The Java implementation must remain compatible with Java 8.

The target runtime environment is expected to provide a Java 8 compatible runtime.

### Rationale

The intended target systems may only provide Java 8.

Using a newer Java language level would reduce compatibility with those systems and would require additional runtime installation or deployment steps.

### Consequences

- Source code must use Java 8 compatible language features.
- Dependencies must support Java 8.
- Build configuration must target Java 8.
- APIs introduced only in later Java versions must not be required.

---

## D-002 — Local Loopback Binding

### Decision

The server should bind to the local loopback interface using:

```text
127.0.0.1
```

for the local desktop use case.

### Rationale

The primary purpose of the server is to provide local web applications for the current machine.

Binding to the loopback interface prevents the server from being exposed to the local network by default.

### Consequences

- The normal local URL uses `127.0.0.1`.
- The service is not reachable from other computers when bound only to the loopback interface.
- Public internet exposure is outside the project scope.

---

## D-003 — Dynamic Port Allocation

### Decision

The server must not use a permanently configured fixed port.

At startup, it should request port:

```text
0
```

from the operating system.

The operating system then selects an available local TCP port.

### Rationale

A fixed port may already be occupied or may conflict with software or policies on the target system.

Allowing the operating system to select an available port avoids unnecessary port conflicts and removes the need for port scanning.

### Consequences

- The actual port must be read from the server socket after startup.
- Any automatically opened browser URL must use the assigned port.
- Application code must not assume a fixed port number.

---

## D-004 — Browser Launch Uses the Assigned Port

Status: Superseded

Superseded by: D-025 — Windows Default Browser Launch

### Decision

After the server has started successfully and the operating system has assigned a port, the launcher should open Microsoft Edge with the corresponding local URL.

Example:

```text
http://127.0.0.1:<assigned-port>/<site>/
```

### Rationale

The user should be able to start the server and the web application with one action without manually determining the assigned port.

### Consequences

- Browser startup must happen only after the server port is known.
- The dynamically assigned port must be inserted into the launch URL.
- Edge is used as the intended browser on the Windows target environment.

---

## D-005 — No Database

### Decision

The project does not use a database.

Persistent application data is stored in JSON files.

### Rationale

The intended applications are small and do not require database functionality.

A database would introduce unnecessary complexity, dependencies, installation requirements, and maintenance.

### Consequences

- Each valid application and explicit persistence scope maps to its own JSON file as defined by D-021.
- The server provides file-based JSON persistence through the central API.
- Database drivers, servers, migrations, and schemas are outside the project scope.

---

## D-006 — Generic Server-Side Data Handling

### Decision

The server must not interpret application-specific data.

The server understands only the technical structure required for persistence, such as:

- Site
- Persistence scope
- Section
- JSON data
- API operation

### Rationale

The server should remain reusable for different small web applications without requiring server changes for application-specific business logic.

### Consequences

- Application-specific validation and semantics belong primarily to the web application.
- The Java server must not contain hard-coded knowledge about individual applications.
- New applications should normally be deployable without modifying the server implementation.

---

## D-007 — One JSON Data File per Application

Status: Superseded

Superseded by: D-021 — Explicit Shared and Private Persistence Scopes

This decision described the original single-location persistence model. It is preserved as historical context and no longer defines the v1 target architecture.

### Decision

Each web application has its own JSON persistence file.

The standard location is:

    www/<site>/data/data.json

### Rationale

Separate data files keep individual applications simple and separated while preserving a predictable directory structure.

### Consequences

Examples:

    www/example/data/data.json
    www/dashboard/data/data.json
    www/dashboard/data/data.json

Each API namespace maps predictably to the persistence file belonging to the site identified by that request URL.

For example:

    /example/api/

maps to:

    www/example/data/data.json

The client must not be able to redirect that namespace to another persistence file by supplying an arbitrary filesystem path or storage location.

This mapping provides namespace and filesystem separation but is not an authentication or authorization boundary between hosted applications.

A deliberately written client may explicitly address another valid application's API namespace as described in D-016.

---

## D-008 — Application Site and Persistence Scope Are Derived from the URL

### Decision

The server determines the application site from the first relevant path component of the request URL and the persistence scope from the explicit scope component of the API URL.

The client does not provide a filesystem path or arbitrary storage location.

### Rationale

The server must control which JSON file belongs to a request.

Allowing clients to submit filesystem paths would create unnecessary complexity and security risks.

### Consequences

For example:

```text
/example/api/private/read?section=settings
```

is scoped to the `example` site's private persistence file, while:

```text
/example/api/shared/read?section=settings
```

is scoped to the `example` site's shared persistence file.

The site name and persistence scope are determined by the request path. The server maps those values to the approved filesystem locations defined by D-021.

---

## D-009 — Shared Central API Implementation

### Decision

The Java server contains one central API implementation.

Individual applications below `www` do not implement separate server-side APIs.

### Rationale

Duplicating API implementations would increase maintenance effort and could cause inconsistent behavior between applications.

### Consequences

The same API operations are available within each application's URL namespace.

Examples:

```text
/example/api/private/read
/notes/api/shared/read
/dashboard/api/private/read
```

All are handled by the same server-side implementation.

---

## D-010 — Shared JavaScript API Library

### Decision

A common JavaScript library named:

```text
mini-api.js
```

provides the browser-side interface to the server API.

It is stored centrally and reused by all applications.

### Rationale

Application developers should not have to repeatedly implement HTTP requests, JSON serialization, JSON deserialization, and common API handling.

### Consequences

The intended location is:

    www/_shared/mini-api.js

Applications may include it with:

    <script src="/_shared/mini-api.js"></script>

The library exposes the operation-first, explicitly scoped public browser-side API defined by D-022. Examples include:

    MiniApi.read(section).private()
    MiniApi.readAll().shared()
    MiniApi.write(data).private()
    MiniApi.remove(section).shared()
    MiniApi.clear().private()

Application code should use this public interface rather than constructing persistence API requests directly during normal use.

---

## D-011 — Native JavaScript Objects and Arrays

### Decision

Application developers should work with native JavaScript objects and arrays when using `MiniApi`.

They should not normally need to call:

```javascript
JSON.stringify()
JSON.parse()
```

themselves.

### Rationale

JSON serialization and deserialization are handled internally by the shared API library and should not complicate application code.

### Consequences

- `mini-api.js` performs serialization before sending requests.
- `mini-api.js` performs deserialization after receiving responses.
- Basic input validation is performed by the library before sending data.

---

## D-012 — Example Application and Reusable Template Package

### Decision

The initial distribution contains a working example application at:

    www/example/

The example application is part of the normal Mini Server web root and serves as the maintained reference implementation and API demonstration.

A reusable starter template is distributed separately as:

    miniweb-template.zip

The template archive is stored outside the `www` web root and is not itself served as web content.

The distribution does not require a permanently installed:

    www/template/

application.

Developers may extract or copy the template into a new first-level application directory below `www/` when creating a new application.

For example:

    www/my-app/

### Rationale

The example application and the reusable template have different purposes.

The `example` application is a living demonstration and may evolve as Mini Server functionality changes.

The template should remain a clean and unchanged starting point that developers can copy when creating a new application.

Keeping the template as a separate archive prevents the distributed starter template from being modified accidentally during normal use or development of the example application.

Keeping the archive outside `www/` also prevents the template package itself from becoming normal static web content.

### Consequences

- `www/example/` is included as the maintained demonstration application.
- `example` may be updated as Mini Server evolves.
- `miniweb-template.zip` provides the reusable clean starting point for new applications.
- The template archive is distributed outside the `www` web root.
- A permanent `www/template/` directory is not required in the normal distribution.
- Developers create a new application by extracting or copying the template into a new first-level directory below `www/`.
- The extracted template must work without application-specific changes to the Java server.
- The template uses the shared `www/_shared/mini-api.js` library.
- The template demonstrates the public MiniApi interface with explicit shared or private persistence scope selection.
- The template contains a minimal visible `Hello Mini Webserver` example.
- The template remains application-neutral and contains no application-specific business logic.

---

## D-013 — English Repository Language

### Decision

English is the primary language of the repository.

This applies to:

- Source code
- Code comments
- Filenames
- Example applications
- Template content
- Primary project documentation

### Rationale

English keeps the repository accessible to a broader developer audience and provides a consistent technical language.

### Consequences

German documentation should additionally be provided where appropriate, especially within the main `README.md`.

Separate competing documentation structures should be avoided where the same README can reasonably contain both languages.

---

## D-014 — Not Intended for Public Internet Use

### Decision

Mini Server is not designed or supported as a public internet-facing web server.

### Rationale

The project intentionally focuses on a small, simple runtime for local or trusted internal applications.

Internet-facing operation would require a significantly different security model and operational scope.

### Consequences

Features required specifically for hardened public web hosting are outside the intended project scope.

This limitation should remain clearly documented for users and developers.

---

## D-015 — Persistence Data Is Not Served as Static Content

### Decision

Application persistence data stored below:

www/<site>/data/

must not be served directly by the static file handler.

In particular, a request such as:

/example/data/data.json

must not return the contents of:

www/example/data/data.json

Persistent application data may only be accessed through the site's JSON API.

This path is the shared persistence location defined by D-021. Private persistence is stored outside the web root and is therefore not eligible for normal static serving.

The `data` directory directly below an application directory is therefore reserved for Mini Server persistence and is not part of the application's publicly served static content.

### Rationale

The shared persistence file is located below the `www` directory to keep each web application and its shared data portable as a self-contained directory.

However, allowing the static file handler to expose that persistence file would bypass the JSON API completely.

API behavior such as section-based access, validation, error handling, and controlled persistence would become ineffective if clients could read the complete data file directly through a static URL.

Keeping the shared persistence directory below the application directory while excluding it from static file serving preserves both portability and the intended API boundary.

### Consequences

- `www/<site>/data/` is a reserved Mini Server directory.
- Files below that directory must not be returned by normal static file requests.
- `www/<site>/data/data.json` is accessed through the central JSON API only.
- Static JSON files may still be served from other application locations when they are normal web application resources.
- Static file path validation must detect and reject requests targeting the reserved persistence directory.
- This rule protects the persistence API boundary but does not by itself provide authentication or a security boundary between deliberately interacting local applications.

---

## D-016 — Application Separation Is Namespace Isolation, Not Authentication

### Decision

Mini Server separates application persistence by URL namespace, explicit persistence scope, and controlled filesystem location.

Each application has a shared persistence location:

    www/<site>/data/data.json

and a private persistence location:

    %APPDATA%\MiniServerData\<site>\data\data.json

and its own API namespace:

    /<site>/api/<scope>/

The server derives the target site from the request URL and does not allow the client to provide an arbitrary persistence filesystem path.

This separation is intended to prevent accidental cross-site or cross-scope persistence access and filesystem path manipulation.

It is not an authentication or authorization boundary between hosted applications.

The term `private` means user-profile persistence rather than shared-installation persistence. It does not add authentication, authorization, cryptographic protection, or isolation between mutually hostile applications.

### Rationale

All applications hosted by one Mini Server instance normally share the same HTTP origin:

    http://127.0.0.1:<port>

Browser same-origin rules therefore do not isolate applications merely because they use different URL path prefixes.

For example, JavaScript loaded from:

    /example/

could deliberately send a request to:

    /dashboard/api/private/readAll

The server would process that request within the `dashboard` namespace because the requested URL explicitly targets that namespace.

Preventing such deliberate interaction would require an authentication or authorization mechanism that is outside the intended scope of the lightweight initial implementation.

### Consequences

- Each API request may access only the persistence location derived from that request's site namespace and explicit scope.
- Clients cannot provide arbitrary filesystem paths to select another persistence file.
- `MiniApi` automatically uses the current application's site namespace and requires application code to select `private` or `shared` explicitly.
- Separate application/scope persistence files prevent accidental mixing of application data.
- Hosted applications must not be treated as mutually untrusted security principals.
- A deliberately written application can request another application's API namespace.
- Mini Server v1.0 does not provide authentication or authorization between hosted applications.
- Applications hosted together by one Mini Server instance should therefore be considered part of the same trusted local environment.
- Public internet exposure remains outside the supported project scope.

---

## D-017 — HTTP and JSON API Contract

Status: Superseded

Superseded by: D-022 — Explicitly Scoped Persistence API Contract

This decision records the original unscoped HTTP contract and browser-side method names. It is preserved as historical context and no longer defines the v1 target API.

### Decision

Mini Server uses a small and stable HTTP contract for its JSON persistence API.

The server-side API operations are:

    GET    /<site>/api/read?section=<name>
    GET    /<site>/api/readAll
    POST   /<site>/api/write
    DELETE /<site>/api/remove?section=<name>
    DELETE /<site>/api/clear

The browser-side MiniApi method names remain:

    MiniApi.readSection(section)
    MiniApi.readAll()
    MiniApi.write(data)
    MiniApi.removeSection(section)
    MiniApi.clear()

The browser-side method names and the server-side endpoint names do not need to be identical.

### Persistence Root Structure

Each site's persistence file contains a JSON object at its root.

The top-level properties of this object are the site's named sections.

Example:

    {
        "start": "Hello Mini Webserver",
        "settings": {
            "theme": "dark"
        },
        "favorites": [
            "Search A",
            "Search B"
        ]
    }

The empty persistence state is:

    {}

A persistence file must not use an array, string, number, boolean, or null value as its root value.

Individual section values may contain any valid JSON-compatible value, including:

- Objects
- Arrays
- Strings
- Numbers
- Booleans
- Null

### Section Names

Section names are logical JSON property names and are not filesystem paths.

A valid section name:

- must not be empty;
- must contain between 1 and 128 characters;
- must not contain leading or trailing whitespace;
- must not contain control characters.

Unicode characters and normal characters such as spaces, hyphens, underscores, and periods may be used inside a section name.

MiniApi is responsible for correctly encoding section names when constructing HTTP requests.

A section name must never be interpreted as a filesystem path.

### Read One Section

The operation:

    GET /<site>/api/read?section=<name>

returns the stored JSON value of the requested section directly.

Example stored data:

    {
        "settings": {
            "theme": "dark"
        }
    }

A request for:

    GET /example/api/read?section=settings

returns:

    {
        "theme": "dark"
    }

A stored JSON null value is a valid section value and therefore returns:

    null

with a successful HTTP status.

If the requested section does not exist, the operation returns:

    404 Not Found

with the normal JSON error response.

### Read All Sections

The operation:

    GET /<site>/api/readAll

returns the complete root JSON object for the site.

If the site's persistence file does not yet exist, `readAll` behaves as though the site contains an empty persistence object and returns:

    {}

### Write Sections

The operation:

    POST /<site>/api/write

accepts a JSON object as its request body.

The top-level properties of that object are the sections to create or replace.

Example:

    {
        "settings": {
            "theme": "dark",
            "language": "en"
        },
        "favorites": [
            "Search A",
            "Search B"
        ]
    }

A write request must contain at least one section.

The request body must therefore be a non-empty JSON object.

For every supplied top-level property:

- a missing section is created;
- an existing section is replaced;
- sections not included in the request remain unchanged.

A successful write returns:

    204 No Content

and no response body.

If the site's persistence file or `data` directory does not yet exist, Mini Server may create them within the valid site directory as required for the write operation.

### Remove One Section

The operation:

    DELETE /<site>/api/remove?section=<name>

removes exactly one named section.

Other sections remain unchanged.

A successful removal returns:

    204 No Content

and no response body.

If the requested section does not exist, the operation returns:

    404 Not Found

with the normal JSON error response.

A missing persistence file is equivalent to the requested section not existing.

### Clear All Sections

The operation:

    DELETE /<site>/api/clear

resets the site's persistence state to the logical equivalent of:

    {}

A successful clear returns:

    204 No Content

and no response body.

Clearing an already empty persistence store is successful.

Clearing a site whose persistence file does not yet exist is also successful.

The operation is therefore idempotent.

### Valid Site Requirement

An API namespace must refer to an existing valid application directory below:

    www/

For example:

    /example/api/

is valid when:

    www/example/

exists as an application directory.

The API must not create a new application directory merely because a request addresses an unknown site name.

An unknown or invalid site returns an appropriate not-found error.

Mini Server reserved areas such as:

    www/_shared/

must not be treated as normal application persistence namespaces.

### Successful Responses

Operations returning JSON data use:

    200 OK

with:

    Content-Type: application/json; charset=utf-8

Successful operations that do not return data use:

    204 No Content

with an empty response body.

For the initial API this applies to:

    POST   /<site>/api/write
    DELETE /<site>/api/remove
    DELETE /<site>/api/clear

### Error Response Format

API errors use a consistent JSON structure:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

The `code` value is a stable machine-readable error identifier.

The `message` value is a concise human-readable description.

Browser-facing error responses must not expose unnecessary absolute filesystem paths or sensitive internal details.

### HTTP Error Categories

The API uses the following HTTP status categories:

    400 Bad Request

for malformed requests, invalid section names, invalid JSON, invalid write payloads, or missing required request information.

    404 Not Found

for missing sections, unknown application sites, or unknown API operations.

    405 Method Not Allowed

when a known API operation is called using an unsupported HTTP method.

    415 Unsupported Media Type

when a write request does not provide an acceptable JSON request content type.

    500 Internal Server Error

for unexpected server-side or persistence failures that prevent the requested operation from completing.

A failed operation must never return a successful HTTP status.

### Rationale

The contract is intentionally small and uses normal HTTP semantics.

GET is used for non-modifying read operations.

POST is used for persistence updates that create or replace one or more sections.

DELETE is used for removal operations.

Successful modifying operations return `204 No Content` because callers do not require an additional success object when the HTTP status already communicates successful completion.

Returning section values directly keeps the browser-side API simple and avoids unnecessary wrapper structures.

Using a root JSON object provides a direct and predictable mapping between top-level JSON properties and Mini Server sections.

A consistent JSON error structure gives MiniApi and application developers a predictable way to handle failures.

### Consequences

- The HTTP methods and endpoint behavior are part of the v1.0 API contract.
- `data.json` always uses a JSON object as its root structure.
- Top-level JSON properties represent sections.
- `MiniApi.write(data)` sends a non-empty object whose top-level properties are the sections to create or replace.
- `read` returns the requested section value directly.
- `readAll` returns the complete root object directly.
- `write`, `remove`, and `clear` return `204 No Content` after successful completion.
- A missing persistence file behaves as an empty store for `readAll` and `clear`.
- A missing section returns `404 Not Found` for `read` and `remove`.
- Valid JSON null remains distinguishable from a missing section because null is returned with `200 OK` while a missing section returns `404 Not Found`.
- API errors use a consistent JSON error structure.
- Clients cannot use section names or request payloads to provide persistence filesystem paths.
- The API does not create arbitrary application directories from unknown site namespaces.

---

## D-018 — Single Running Instance and Server Lifetime

Status: Superseded

Superseded by: D-020 — Local Per-User/Computer Runtime Instance

This decision is preserved as historical context. It was superseded because the deployment model now permits one shared or network installation to be used concurrently from different computers, which makes installation-scoped runtime coordination invalid.

### Decision

Mini Server allows only one running server instance per installation at a time.

Different independent Mini Server installations may run simultaneously.

Each installation manages its own instance state and continues to use an operating-system-assigned dynamic TCP port.

### Dynamic Port

When a new Mini Server instance starts, it binds exclusively to:

    127.0.0.1

and requests TCP port:

    0

The operating system selects an available local port.

Mini Server must not scan for free ports and must not depend on a permanently configured port.

After the server has successfully started, the actually assigned port is stored in the installation's local runtime state.

### Instance Lock

Before starting the HTTP server, Mini Server must acquire an exclusive instance lock for the current installation.

The lock identifies whether another Mini Server process is already responsible for the same installation and persistence data.

The runtime lock and instance state must be stored outside:

    www/

They must never be exposed as normal static web content.

The intended runtime location is:

    <installation-root>/.runtime/

with separate lock and state information, for example:

    <installation-root>/.runtime/instance.lock
    <installation-root>/.runtime/instance.json

The exact internal representation of the state file may remain an implementation detail, but it must contain at least the currently assigned TCP port.

### First Start

When no other instance owns the installation lock:

1. The new process acquires the exclusive instance lock.
2. Any stale previous runtime state is invalidated.
3. The HTTP server binds to `127.0.0.1` using port `0`.
4. The operating system assigns an available port.
5. Mini Server reads the assigned port from the running server.
6. The current port is written to the runtime state.
7. The server is considered ready.
8. Microsoft Edge is opened with the configured Mini Server start URL using the actual assigned port.

For example:

    http://127.0.0.1:51847/example/

The concrete application path may depend on the configured distribution start target.

### Repeated Start

If the user starts the same Mini Server installation again while its server is already running, the second process must not start another HTTP server.

Instead, the second process:

1. Detects that the exclusive instance lock is already owned.
2. Reads the current runtime state.
3. Obtains the port of the already running Mini Server instance.
4. Opens Microsoft Edge using that existing server address.
5. Terminates without becoming another server process.

For example, when the existing instance is using:

    127.0.0.1:51847

a repeated start reuses that port instead of requesting another one.

### Startup Race Handling

A repeated start may occur while the first process has already acquired the instance lock but has not yet finished starting the HTTP server.

The second process must not interpret missing or incomplete runtime state during this short startup phase as permission to start another server.

It may wait and retry for a short bounded period for valid runtime state to become available.

If valid runtime state cannot be obtained while the instance lock remains owned, the second process must fail with a clear diagnostic message rather than starting a competing server instance.

### Stale Runtime State

A runtime state file alone is never proof that a Mini Server instance is still running.

The exclusive instance lock is authoritative.

If no process owns the instance lock, a new Mini Server instance may start normally even when stale runtime state remains from a previous execution.

After acquiring the lock, the new process must invalidate or replace stale runtime state before publishing its newly assigned port.

This prevents a repeated start from using an obsolete port value while a new instance is still starting.

### Server Lifetime

Closing Microsoft Edge does not stop Mini Server.

The server process continues running independently of individual browser windows or tabs.

The server remains active until its Java process ends, for example because:

- The user logs off
- The operating system shuts down
- The Java process is explicitly terminated
- The process exits because of a fatal server error

Mini Server v1.0 does not provide an HTTP shutdown endpoint.

A hosted web application must therefore not be able to terminate Mini Server through a normal browser API request.

### Process Termination

The operating system releases the exclusive instance lock when the owning Java process terminates.

This also applies when the process terminates unexpectedly.

A stale runtime state file may remain after abnormal termination.

Such stale state must not prevent a later Mini Server start because the runtime state file is not authoritative without the corresponding active instance lock.

### Persistence Safety

Preventing multiple server processes from operating on the same installation also prevents independent Mini Server processes from concurrently modifying the same application persistence files.

Within one running Mini Server process, normal synchronization and persistence integrity rules still apply.

The single-instance mechanism is therefore part of the protection against competing writes to:

    www/<site>/data/data.json

### Rationale

Mini Server is intended to behave like a local per-user service that can be started conveniently through a normal desktop action.

Users should be able to start Mini Server repeatedly without needing to know whether it is already running.

Reusing an existing instance gives repeated desktop starts predictable behavior while preserving the operating-system-assigned dynamic port model.

A per-installation instance lock prevents multiple server processes from competing for the same persistence files.

Keeping the server independent from the Edge process allows users to close or reopen browser windows without unintentionally stopping the local service.

Avoiding an HTTP shutdown endpoint prevents ordinary hosted pages from receiving an unnecessary server-control capability.

### Consequences

- Only one Mini Server server process may run for one installation at a time.
- Independent Mini Server installations may run simultaneously.
- The server continues to bind only to `127.0.0.1`.
- Every newly started server instance continues to request port `0`.
- Mini Server does not scan for free ports.
- The assigned port is published only after the server has successfully started.
- Runtime lock and state information are stored outside `www/`.
- A repeated start reuses the existing server instance instead of creating another one.
- A repeated start opens Edge using the port of the existing instance.
- A stale state file does not imply that a server is still running.
- The instance lock is authoritative for determining whether an instance exists.
- Closing Edge does not stop Mini Server.
- Mini Server v1.0 has no browser-accessible HTTP shutdown endpoint.
- Multiple independent server processes cannot concurrently modify the same installation's persistence files under normal operation.
- Abnormal process termination does not permanently block subsequent starts because the operating system releases the process-owned lock.

---

## D-019 — Maven Build and Project Source Structure

### Decision

Mini Server v1.0 uses Maven as its authoritative build system.

The project follows the conventional Maven source layout for Java production code and automated Java tests.

The intended development structure is:

    mini-server/
    ├── pom.xml
    ├── src/
    │   ├── main/
    │   │   └── java/
    │   │       └── io/github/madebyzwen/miniserver/
    │   └── test/
    │       └── java/
    │           └── io/github/madebyzwen/miniserver/
    ├── template/
    │   ├── index.html
    │   ├── assets/
    │   └── data/
    │       └── data.json
    ├── tests/
    │   └── README.md
    ├── scripts/
    │   └── README.md
    └── www/
        ├── _shared/
        │   └── mini-api.js
        └── example/
            ├── index.html
            ├── assets/
            └── data/
                └── data.json

Additional project documentation and release files remain at their existing repository locations.

### Maven Build

The repository root contains:

    pom.xml

This file is the authoritative build configuration for the Java implementation.

Build behavior must not be duplicated independently in shell scripts, IDE configuration, or other secondary build definitions.

Convenience scripts may invoke Maven but must not become an independent build system.

### Java Source Layout

Production Java source code belongs below:

    src/main/java/

The base Java package for Mini Server is:

    io.github.madebyzwen.miniserver

The corresponding base source directory is:

    src/main/java/io/github/madebyzwen/miniserver/

Subpackages may be introduced where they provide useful separation of responsibilities.

The package structure should remain small and understandable and must not introduce unnecessary architectural layers.

### Automated Test Layout

Automated Java tests belong below:

    src/test/java/

The normal test package hierarchy should correspond to the production package hierarchy where practical.

The existing top-level:

    tests/

directory is used for test documentation and other test-related project material that does not belong in the Maven Java test source tree.

Automated Java test classes must not be placed directly in the top-level `tests/` directory.

### Web Root

Hosted web content remains outside the Java source tree.

The runtime web root remains:

    www/

Shared browser-side code remains below:

    www/_shared/

Individual hosted applications remain first-level directories below:

    www/<site>/

Web application content must not be embedded into Java source packages merely to satisfy the Maven structure.

### Reusable Template Source

The maintained source contents used to produce the reusable Mini Server starter template are stored outside the runtime web root at:

    template/

This directory is development and packaging input.

It is not itself a hosted Mini Server application.

The packaged distribution artifact remains:

    miniweb-template.zip

The archive is produced from the reusable template source during the packaging or release process.

The template source must not require a permanent:

    www/template/

application.

A developer using the distributed template extracts or copies its contents into a new first-level application directory such as:

    www/my-app/

### Java Compatibility

The Maven build must enforce compatibility with the Java version approved for Mini Server v1.0.

The current target is Java 8.

Production code, automated tests, build plugins, and runtime dependencies used for v1.0 must therefore remain compatible with Java 8 where they participate in the Java 8 build or runtime.

Development may take place using a newer JDK as long as the build configuration and verification process continue to enforce the approved Java 8 target.

Final compatibility must be verified against the approved Java 8 runtime as required by the active requirements.

### Dependencies

External dependencies may be introduced through Maven when they provide a clear technical benefit and remain compatible with the approved Java target.

Mini Server should continue to prefer a small dependency set.

Large application frameworks must not be introduced merely for convenience when the required server functionality can remain small and understandable.

The exact runtime libraries, test framework versions, Maven plugin versions, and packaging plugin configuration are implementation decisions that may be finalized when the initial `pom.xml` is created.

Those implementation choices must not contradict the active requirements or approved architectural decisions.

### Scripts

The top-level:

    scripts/

directory remains available for small convenience and project automation scripts.

Scripts may support activities such as:

- Running the application
- Invoking tests
- Packaging
- Release preparation
- Development validation

Where Maven already provides the authoritative operation, scripts should delegate to Maven rather than duplicating build logic.

### Build Outputs

Generated Maven build output must not be committed as source content.

Normal generated output such as:

    target/

is considered disposable build output.

The repository's ignore rules must exclude normal generated build artifacts.

### Rationale

Using the conventional Maven structure reduces project-specific build conventions and makes the repository immediately understandable to Java developers and coding agents.

Keeping one authoritative `pom.xml` avoids duplicated build configuration.

Separating Java source code, automated Java tests, hosted web content, and reusable template source gives each project area a clear responsibility.

Keeping `www/` outside the Java source tree preserves Mini Server's model in which web applications remain directly editable files rather than application resources hidden inside Java packages.

Maintaining reusable template source outside `www/` preserves the distinction between the living example application and the clean starter template package.

### Consequences

- Maven is the authoritative build system for Mini Server v1.0.
- The authoritative build file is the root `pom.xml`.
- Production Java code belongs below `src/main/java/`.
- Automated Java tests belong below `src/test/java/`.
- The base Java package is `io.github.madebyzwen.miniserver`.
- The top-level `tests/` directory is not the Java test source directory.
- Hosted web applications remain below `www/`.
- Reusable template source is maintained below top-level `template/`.
- `template/` is not part of the runtime web root.
- The distributed reusable template remains `miniweb-template.zip`.
- No permanent `www/template/` application is required.
- Build output such as `target/` is not committed.
- The Maven build must enforce the approved Java 8 target.
- Exact dependency versions, Maven plugin versions, test framework versions, executable-JAR packaging details, and the concrete initial `pom.xml` configuration are deferred until implementation preparation.
- Codex may implement the build configuration later, but it must follow this decision and the active requirements.

---

## D-020 — Local Per-User/Computer Runtime Instance

### Decision

Mini Server supports distributions located on shared or network drives. Multiple users on different computers may start the same physical installation concurrently.

The running HTTP server remains local to the current computer. Single-instance coordination is therefore scoped to one local user/computer context, not to the installation directory.

Runtime coordination state is stored at:

    %LOCALAPPDATA%\MiniServer\runtime\

using:

    startup.lock
    instance.lock
    instance.json

startup.lock serializes concurrent startup attempts within the local user/computer context. instance.lock is held by the active server process for its lifetime and is authoritative for whether that local instance is active. instance.json contains repeated-start information, including the active dynamically assigned TCP port.

No machine- or process-specific runtime lock or port state may be stored in or coordinated through the shared installation directory.

### Startup and Repeated Starts

A startup attempt obtains startup.lock before it evaluates or changes local instance state.

If no active process owns instance.lock, the new process invalidates stale state, obtains and retains instance.lock, starts the server on 127.0.0.1 using port 0, obtains the assigned port, confirms readiness, and publishes that port in instance.json.

If another local process owns instance.lock, the startup attempt does not start another server. It obtains the active port from valid local runtime state, evaluates the current configured application URLs according to D-026, asks Windows to open them according to D-025, and exits.

Startup races and lock acquisition use bounded, deterministic waits. A state file alone is never proof that an instance is active. Failure to obtain valid state for an actively locked instance fails cleanly rather than starting a competing local process.

The Mini Server process remains independent of the Windows-selected browser. Graceful local
shutdown uses the authenticated internal route defined by D-024; no anonymous
application shutdown operation is provided.

### Rationale

An installation-scoped lock on a shared drive would cause unrelated computers to block each other and would publish a loopback port that is unusable from the other computers. Local user-specific runtime state aligns instance coordination with the local 127.0.0.1 server that it describes.

### Consequences

- User A on Computer A and User B on Computer B may run the same shared installation concurrently.
- One person may run Mini Server in separate computer or VDI contexts concurrently.
- Repeated starts in the same local user/computer context reuse or detect the active local instance.
- The installation contains no authoritative runtime lock or port state.
- The server continues to bind exclusively to 127.0.0.1 and request port 0.
- The assigned port is published only after successful local server startup.
- Process termination releases the process-owned instance.lock; stale state alone does not block a later start.

---

## D-021 — Explicit Shared and Private Persistence Scopes

### Decision

Every persistence operation explicitly selects exactly one of two scopes:

    shared
    private

There is no implicit or default persistence scope in v1.

Shared application persistence is stored at:

    <installation-root>\www\<site>\data\data.json

Private application persistence is stored at:

    %APPDATA%\MiniServerData\<site>\data\data.json

The private structure intentionally mirrors the shared application's data/data.json structure. Both scopes use the same section-based JSON model and operations.

The server derives and validates both locations from the URL-selected site and scope. Clients never provide arbitrary filesystem paths.

Private means data stored in the current Windows user's profile rather than in the shared installation. It does not provide authentication, authorization, encryption, or isolation between mutually hostile applications.

### Rationale

A shared/network installation needs a deliberate distinction between data shared by users of that installation and data belonging to the current user's profile. Mandatory selection prevents accidental reliance on an ambiguous default.

### Consequences

- Each valid site can have one shared and one private persistence file.
- Shared data follows the installation and may be accessed by users of that shared installation.
- Private data follows the current user's %APPDATA% profile independently of the installation location.
- Both files use a JSON object root whose top-level properties are Sections.
- Static-serving protection continues to apply to <installation-root>\www\<site>\data\.
- Private storage is outside the web root and is not normal static content.
- No migration, compatibility alias, or legacy unscoped storage behavior is required for the discarded pre-v1 implementation.

---

## D-022 — Explicitly Scoped Persistence API Contract

### Decision

The HTTP persistence API places the mandatory scope between api and the operation:

    /<site>/api/<scope>/<operation>

where <scope> is private or shared.

The operations are:

    GET    /<site>/api/<scope>/read?section=<name>
    GET    /<site>/api/<scope>/readAll
    POST   /<site>/api/<scope>/write
    DELETE /<site>/api/<scope>/remove?section=<name>
    DELETE /<site>/api/<scope>/clear

An absent, unknown, or otherwise invalid scope is an invalid API request. The alternative layout /<site>/<scope>/api/<operation> is not part of the contract.

The browser-side API uses the operation first and a mandatory terminal scope selector afterwards:

    MiniApi.read(section).private()
    MiniApi.read(section).shared()
    MiniApi.readAll().private()
    MiniApi.readAll().shared()
    MiniApi.write(data).private()
    MiniApi.write(data).shared()
    MiniApi.remove(section).private()
    MiniApi.remove(section).shared()
    MiniApi.clear().private()
    MiniApi.clear().shared()

The terminal .private() or .shared() call executes the asynchronous operation and returns its Promise. Scope-first forms such as MiniApi.private().read(...), and the old names readSection and removeSection, are not part of the v1 contract.

D-022 supersedes the unscoped HTTP contract and old browser-side method names recorded historically in D-017. D-017 does not define active v1 behavior.

### Persistence Root Structure

Each selected shared or private persistence file contains a JSON object at its root.

The top-level properties are named Sections. The empty persistence state is:

    {}

The root must not be an array, string, number, boolean, or null.

Individual Section values may contain any JSON-compatible object, array, string, number, boolean, or null. The server does not interpret their application-specific meaning.

Invalid JSON or a non-object root is an error and must not be silently reinterpreted or reset.

### Section Names

Section names are logical JSON property names, not filesystem paths.

A valid Section name:

- contains between 1 and 128 characters;
- is not empty;
- has no leading or trailing whitespace;
- contains no control characters.

Unicode characters and normal spaces, hyphens, underscores, and periods may appear inside a Section name.

MiniApi must URL-encode Section names when constructing HTTP requests. The server must never interpret a Section name as a filesystem path.

### read

    GET /<site>/api/<scope>/read?section=<name>

returns the requested Section's stored JSON value directly with 200 OK.

A stored JSON null is a valid value and returns successfully as:

    null

A missing Section returns 404 Not Found with the standard JSON error response. A missing selected persistence file is equivalent to the requested Section not existing.

### readAll

    GET /<site>/api/<scope>/readAll

returns the complete root JSON object with 200 OK.

If the selected persistence file does not yet exist, readAll returns:

    {}

### write

    POST /<site>/api/<scope>/write

accepts a non-empty JSON object. Its top-level properties are the Sections to create or replace.

For each supplied property:

- a missing Section is created;
- an existing Section is replaced;
- Sections omitted from the request remain unchanged.

A valid write may create the selected persistence file and its data directory inside the server-derived location for an existing valid application site.

A successful write returns 204 No Content with an empty response body.

### remove

    DELETE /<site>/api/<scope>/remove?section=<name>

removes exactly one named Section. Other Sections remain unchanged.

A successful removal returns 204 No Content with an empty response body.

A missing Section returns 404 Not Found with the standard JSON error response. A missing selected persistence file is equivalent to the requested Section not existing.

### clear

    DELETE /<site>/api/<scope>/clear

resets the selected persistence file to:

    {}

Clearing an empty or not-yet-created persistence store is successful and idempotent.

A successful clear returns 204 No Content with an empty response body.

### Valid Site and Persistence Location

The site namespace must identify an existing valid first-level application directory below www. Reserved areas such as www/_shared are not valid application persistence namespaces.

The API must not create a new application namespace or application directory merely because a request addresses an unknown site.

The server derives the physical persistence location from the validated site and explicit persistence scope according to D-021.

Clients cannot supply or override a persistence filesystem path through a query parameter, request body, header, Section name, or other client-controlled storage location.

### Successful Responses

Operations returning JSON data use 200 OK with:

    Content-Type: application/json; charset=utf-8

Successful write, remove, and clear operations use 204 No Content with an empty response body.

### Error Response Format

API errors use:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

The code value is a stable machine-readable error identifier.

The message value is a concise human-readable description.

Browser-facing errors must not expose unnecessary absolute filesystem paths or sensitive internal details.

### HTTP Error Categories

The API uses:

- 400 Bad Request for malformed requests, missing or invalid persistence scope, invalid Section names, invalid JSON, invalid write payloads, or missing required input;
- 404 Not Found for missing Sections, unknown application sites, or unknown API operations;
- 405 Method Not Allowed when a known operation is called with an unsupported HTTP method;
- 415 Unsupported Media Type when a write request lacks an acceptable JSON content type;
- 500 Internal Server Error for unexpected server-side or persistence failures.

Persistence write-lock failures follow D-023.

A failed operation must never return a successful HTTP status.

### Rationale

Putting scope in the HTTP URL makes the server mapping explicit. Operation-first fluent browser syntax keeps the familiar operation names while requiring the developer to make the persistence destination visible at every call site.

### Consequences

- No unscoped persistence endpoint or MiniApi call is valid.
- read reads one Section and readAll reads the complete root object.
- write creates or replaces supplied Sections, remove removes one Section, and clear resets the selected persistence file to {}.
- The site comes from the page/request namespace; the scope comes from the mandatory selector.
- Java server routes, mini-api.js, examples, templates, tests, and documentation must use the same scoped contract.

---

## D-023 — Concurrency-Safe Persistence Writes

### Decision

The persistence operations write, remove, and clear are write operations.

Each write operation obtains a short-lived exclusive file lock associated with its target shared or private persistence file. Lock acquisition uses a bounded timeout, and the lock is held only for the duration required to complete that write safely.

Writes are atomic. Readers do not acquire a separate read lock and must observe either the previous complete JSON file or the new complete JSON file, never a partially written file.

Failure to obtain the required write lock fails the operation cleanly. Its intentionally simple external error remains:

    Write failed

Persistence-file locking is separate from the runtime coordination locks defined by D-020. Runtime locking controls one local server instance; persistence locking protects one target data file across processes and computers.

### Rationale

Shared persistence may be written by server processes on different computers, and private persistence can also encounter concurrent access within a user's environment. Local single-instance coordination cannot protect these file operations.

### Consequences

- Read-modify-write behavior for a modifying operation occurs while holding the target file's exclusive write lock.
- Lock waits are bounded and do not hang indefinitely.
- A lock failure never reports success and does not intentionally damage existing data.
- Reads remain lock-free and rely on atomic replacement for complete-file visibility.
- The project does not introduce a database, transaction service, or large logging/recovery subsystem.

---

## D-024 — Detached Windows Start and Authenticated Local Stop

### Decision

`start.bat` launches the existing `MiniServer` entry point through `javaw.exe`
and exits without waiting for startup or browser-opening confirmation. `stop.bat` invokes
`MiniServer stop` through `java.exe`. Both use quoted absolute classpath paths
derived from `%~dp0` and do not change the command working directory.

Each new server stores an unpredictable stop token beside its active port in
local `instance.json`. The stop command sends that token in the
`X-MiniServer-Token` header to `POST /__miniserver/stop` on the existing
loopback HTTP listener, then waits boundedly for `instance.lock` to be released.
The response is completed before `RunningMiniServer.close()` runs asynchronously.
No second control listener, PID file, launcher handshake, or process supervisor
is used.

### Consequences

- The start CMD window does not remain attached to the server lifetime.
- Repeated starts continue to reuse the active dynamic port through D-020.
- Missing or incorrect stop tokens cannot trigger shutdown.
- Runtime control state remains local to the current user/computer context.
- Exact simultaneous start/stop transaction ordering is not guaranteed.

---

## D-025 — Windows Default Browser Launch

### Decision

For every valid local application URL to be opened, Mini Server asks Windows to open that URL using the configured operating-system HTTP URL handler.

Mini Server does not explicitly select a browser product and does not determine which browser executable handles the URL. Windows and the current user's operating-system configuration select the browser.

Mini Server therefore uses:

- No browser executable discovery
- No Microsoft Edge installation detection
- No supported-browser list
- No browser-specific executable paths
- No browser priority or fallback chain

Every submitted URL must still use `127.0.0.1` and the actual active Mini Server dynamic port.

D-025 supersedes D-004 — Browser Launch Uses the Assigned Port. Only D-004's browser-selection rule is replaced: the requirement to wait for a usable server and use its actual active port remains valid.

### Startup Behavior

On a first start, browser opening occurs only after the HTTP server is ready, the actual dynamic port is known, and valid local runtime state has been published.

On a repeated start, Mini Server reuses the existing local server and its active port without starting another server or requesting another port.

In both cases, D-026 determines which application URLs are selected and their order. Mini Server submits each selected URL to Windows in that order.

Failure to open one URL is isolated from the running server and from attempts to open later valid URLs. When practical, a concise diagnostic provides the affected local URL for manual use.

Browser lifetime remains independent from Mini Server lifetime. Closing the Windows-selected browser does not intentionally stop the server.

D-020 remains authoritative for runtime coordination. D-024 remains authoritative for detached startup and authenticated graceful shutdown.

### Rationale

Browser choice is an operating-system and user preference. It should not be a Mini Server responsibility.

Delegating HTTP URL handling to Windows removes browser-product discovery and lets the user's current Windows configuration determine the browser.

### Consequences

- Microsoft Edge is no longer required for v1.1 browser opening.
- Browser-launch logic is independent of individual browser products.
- Windows default-browser behavior determines which application handles local URLs.
- Changing the Windows default browser is respected by a later start action without restarting Mini Server.
- Every browser URL still uses the actual active local port.
- Multiple selected URLs are submitted in the configured order.
- Failure to open one URL does not invalidate the server, runtime state, active port, or later URL attempts.
- Browser lifetime does not own server lifetime.
- D-004 is superseded while remaining a historical v1.0-era decision record.

---

## D-026 — Shared Installation Start-Site Configuration

### Decision

Mini Server uses:

```text
<installation-root>/config/start-sites.txt
```

to select which existing applications should be opened automatically during a normal start action.

The configuration belongs to the installation and is shared when the physical installation is shared. It affects browser opening only and does not define application discovery or serving.

The file is simple UTF-8 text with one effective first-level application directory name per line. It is not JSON, XML, YAML, key/value configuration, or a general-purpose settings framework. It cannot contain arbitrary URLs or filesystem paths.

The v1.1 distribution provides this default active entry:

```text
example
```

The `example` application is not hard-coded in Java and opens only because the distributed configuration lists it.

### Configuration Behavior

Entries are normalized and validated according to REQ-010. Invalid, reserved, unsafe, duplicate, or missing-application entries are ignored without failing server startup. Duplicate handling retains the first occurrence and valid entries preserve file order.

If `start-sites.txt` is missing or has no effective valid entries, the server starts normally and opens no application automatically. Normal runtime startup does not recreate a missing configuration file.

If an existing configuration file cannot be read, an otherwise successful server remains active, no URLs are derived from unreadable content, and a concise diagnostic reports the problem.

### Configuration Lifetime

The configuration is evaluated on every normal start action, including repeated starts.

Changes therefore take effect on the next `start.bat` invocation without restarting the active server. No file watcher or active-server configuration reload service is required.

### Runtime Boundary

Installation configuration is not runtime coordination state. It must not be copied into `%LOCALAPPDATA%\MiniServer\runtime\` and creates no shared runtime locking or shared port state.

It must not alter stop tokens, runtime locks, shared persistence, private persistence, or persistence locking.

### Interaction

D-026 determines which valid application URLs are selected and their order. D-025 determines how Windows opens those URLs.

### Rationale

A small installation-level list makes automatic opening configurable for local and shared deployments without turning application discovery into configuration or introducing a general settings framework.

### Consequences

- Users of one physical shared installation share its start-site selection.
- Application serving remains based on actual valid first-level directories below `www/`.
- Applications do not need a start-site entry to remain available.
- Missing, empty, or partly invalid configuration does not prevent server startup.
- A repeated start uses the current configuration and existing active port.
- Runtime coordination and persistence boundaries remain unchanged.

---

## Changing Decisions

Existing decisions should not be silently rewritten when project requirements change.

When a previously approved decision is changed:

1. Record the new decision explicitly.
2. Reference the decision being superseded.
3. Explain the reason for the change.
4. Update affected requirements, architecture documentation, and implementation as necessary.

Historical decisions should remain understandable from the repository history.
