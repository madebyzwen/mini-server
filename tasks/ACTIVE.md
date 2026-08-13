# Active Tasks

This document contains the currently active implementation tasks for Mini Server.

Tasks describe concrete work that should be performed to fulfill the approved active requirements.

Tasks are not a replacement for requirements.

Each task should reference the requirement or requirements that define the expected behavior.

---

## Task Status

Use the following task states:

- Planned
- In Progress
- Blocked
- Done

Only tasks that are relevant to the current implementation scope should be listed here.

Completed tasks may be removed from this file after their result has been documented appropriately in the project history or release documentation.

---

## T-001 — Create Initial Java Project Structure

Status: Done

Related requirements:

- REQ-008

Description:

Create the initial Maven project and source structure for the Mini Server Java implementation according to D-019.

This task establishes the build and source foundation for the later implementation tasks.

It must not independently define application behavior that belongs to later tasks.

### Maven Build

Create the authoritative Maven build configuration at:

    pom.xml

in the repository root.

The Maven project must:

- Target the approved Java 8 runtime
- Use the conventional Maven source layout
- Support compilation of production Java source code
- Support compilation and execution of automated Java tests
- Produce build output below `target/`
- Provide the foundation for later runtime packaging

The concrete dependency versions, test framework versions, Maven plugin versions, executable-JAR strategy, and final packaging configuration must use the choices approved during implementation preparation.

Codex must not silently choose architectural alternatives that conflict with active requirements or approved decisions.

### Production Source Layout

Production Java source code belongs below:

    src/main/java/

The base Java package is:

    io.github.madebyzwen.miniserver

The corresponding source path is:

    src/main/java/io/github/madebyzwen/miniserver/

Subpackages may be introduced during later implementation tasks when they provide useful separation of responsibilities.

The package structure must remain small and understandable.

### Automated Test Layout

Automated Java test source code belongs below:

    src/test/java/

The base test package corresponds to:

    io.github.madebyzwen.miniserver

The top-level:

    tests/

directory remains test documentation and supporting project material.

Java test classes must not be placed directly in the top-level `tests/` directory.

### Web Content

The runtime web root remains:

    www/

It must remain outside the Java source tree.

The initial repository structure must support:

    www/_shared/
    www/example/

without embedding these directories into Java source packages.

### Reusable Template Source

Create or preserve the top-level development source location:

    template/

for the reusable starter-template contents.

This directory is packaging input and is not part of the runtime web root.

It must not create or require:

    www/template/

The later packaging process will produce:

    miniweb-template.zip

from the maintained reusable template source.

### Build and Runtime Separation

Generated build output belongs below:

    target/

Local process runtime state belongs outside the project and installation below:

    %LOCALAPPDATA%\MiniServer\runtime\

Neither generated build output nor local runtime state is project source content. The legacy `.runtime/` path remains ignored so that accidental local artifacts are not tracked, but it is not the target runtime location.

Both must remain excluded from normal Git tracking.

### Scripts

The existing:

    scripts/

directory remains available for optional convenience automation.

Maven remains authoritative for build and automated Java test behavior.

T-001 must not introduce a second independent build configuration through scripts.

### Scope

This task establishes project structure and build infrastructure only.

It does not implement the functional behavior assigned to later tasks, including:

- HTTP server startup
- Dynamic port allocation
- Instance locking
- Static file serving
- API routing
- JSON persistence
- MiniApi behavior
- Example application behavior
- Microsoft Edge launch behavior

Those responsibilities remain with their dedicated implementation tasks.

Acceptance:

- A root `pom.xml` exists.
- Maven is the authoritative Java build system.
- The Maven build enforces the approved Java 8 target.
- Production Java source uses `src/main/java/`.
- The base production package is `io.github.madebyzwen.miniserver`.
- Automated Java tests use `src/test/java/`.
- The top-level `tests/` directory is not used as the Maven Java test source directory.
- The runtime web root remains outside the Java source tree at `www/`.
- Shared browser-side content remains below `www/_shared/`.
- The maintained example application remains below `www/example/`.
- Reusable starter-template source has the top-level `template/` location.
- No permanent `www/template/` application is introduced.
- Generated Maven output is written below `target/`.
- `target/` remains excluded from Git tracking.
- Local runtime state is not created as project source, and `.runtime/` remains excluded from Git tracking as a safeguard.
- Convenience scripts do not duplicate the authoritative Maven build configuration.
- The initial Maven project can be compiled successfully once the minimum required source files for the implementation exist.
- No functional Mini Server behavior is implemented merely to satisfy this project-structure task.

---

## T-002 — Implement Dynamic Local Server Startup

Status: Done

Historical note: The previous T-002 implementation was deliberately rolled back because the deployment architecture changed. T-002 was subsequently redefined and reimplemented against the corrected local-runtime architecture defined by D-020.

Related requirements:

- REQ-002
- REQ-006
- REQ-008

Description:

Implement dynamic loopback server startup and the local per-user/computer single-instance mechanism defined by D-020.

A Mini Server installation may be used concurrently from different computers.

Each local user/computer context maintains its own runtime coordination state outside the shared installation.

The intended runtime location is:

    %LOCALAPPDATA%\MiniServer\runtime\

with:

    startup.lock
    instance.lock
    instance.json

The local runtime directory may be created when required.

Runtime lock and port state must not be located below the installation or `www/`, shared through a network drive, or exposed through static serving.

### Startup and Instance Locks

Before evaluating or changing local instance state, the process must acquire startup.lock using a bounded wait.

The active process-owned instance.lock is authoritative for determining whether another Mini Server process is already running in the local user/computer context.

A runtime state file by itself must not be treated as proof that an instance is active.

The active server process must retain ownership of the lock for its entire lifetime.

When the process terminates, the operating system must release the process-owned lock.

### New Server Instance

If no active process owns the local instance.lock:

1. Acquire startup.lock using a bounded wait.
2. Invalidate stale local runtime state.
3. Acquire and retain the exclusive instance.lock.
4. Bind the HTTP server exclusively to:

       127.0.0.1

5. Request TCP port:

       0

6. Allow the operating system to select an available local TCP port.
7. Retrieve the actual assigned port from the running server instance.
8. Confirm that the HTTP server is ready to accept requests.
9. Publish the assigned port in local instance.json.
10. Release startup.lock after startup state is stable.
11. Continue running while retaining instance.lock.

Mini Server must not:

- Use a permanently configured TCP port
- Scan a range of ports looking for an available port
- Bind the normal HTTP listener to external interfaces
- Publish a guessed or not-yet-active port

### Existing Server Instance

If another local Mini Server process already owns instance.lock, the new process must not start another HTTP server.

Instead it must:

1. Recognize the start as a repeated start.
2. Obtain valid runtime state from the active instance.
3. Retrieve the active server's assigned TCP port.
4. Make that existing port available to the browser-launch logic.
5. Terminate without becoming another server process.

The repeated-start path must therefore reuse the existing Mini Server instance.

### Startup Race

A second local process may encounter the active instance before valid runtime state is available.

In this situation the second process must not start another server.

It may wait and retry for valid runtime state for a short bounded period while coordinating through startup.lock.

If the lock remains owned but valid runtime state cannot be obtained within that period, startup must fail with a clear diagnostic result.

The exact retry interval and timeout may be chosen during implementation, but the wait must remain bounded and must preserve the single-instance guarantee.

### Stale Runtime State

A stale runtime state file may remain after abnormal process termination.

If no active process owns instance.lock, stale state must not prevent a new server from starting.

After acquiring the lock, the new process must invalidate stale state before starting and must publish fresh state only after its own server has successfully obtained its assigned port.

An old stored port must never be reused solely because it remains in `instance.json`.

### Persistence Concurrency Boundary

Local runtime locking does not protect shared persistence from Mini Server processes on other computers.

Shared and private persistence writes are protected separately by the short-lived file-locking and atomic-write model defined by D-023 and implemented in T-005.

Acceptance:

- The server binds exclusively to `127.0.0.1`.
- TCP port `0` is requested for every newly started server instance.
- The operating system assigns the runtime port.
- The actual assigned port can be retrieved from the running server.
- No fixed server port is required.
- No manual port scanning is used.
- Runtime state is stored under `%LOCALAPPDATA%\MiniServer\runtime\`.
- No runtime lock or port state is shared through the installation.
- startup.lock coordinates local startup attempts using a bounded wait.
- An exclusive local per-user/computer instance.lock is acquired before starting a new server.
- The active server retains the instance lock for its lifetime.
- Only one server process runs in one local user/computer context.
- Different computers may run the same shared installation simultaneously.
- Stale runtime state is invalidated before fresh runtime state is published.
- The assigned port is published only after successful server startup.
- A runtime state file alone is not treated as proof of an active instance.
- A repeated start does not create another HTTP server.
- A repeated start can obtain the active instance's runtime port.
- A startup race does not result in a competing server process.
- Failure to obtain valid runtime state during a repeated-start race produces a diagnostic error.
- Runtime lock and state waits are bounded and deterministic.
- Abnormal process termination does not permanently block future startup.
- The process-owned instance lock is released when the owning process terminates.
- Startup failures are reported clearly.
- Runtime instance locking is not used as persistence-file locking.

---

## T-003 — Implement Static File Serving

Status: Done

Related requirements:

- REQ-001
- REQ-007

Description:

Implement static file serving from the:

www/

directory.

Requests must resolve only to files inside the web root.

Directory requests should resolve to index.html when present.

Directory listings must not be exposed.

Path traversal outside the web root must be prevented.

Acceptance:

- HTML files can be served.
- CSS files can be served.
- JavaScript files can be served.
- JSON and common image files can be served.
- Appropriate content types are returned.
- Missing files return an error.
- Requests cannot escape the www directory.

---

## T-004 — Implement Site Detection and Persistence Scoping

Status: Done

Related requirements:

- REQ-003
- REQ-004
- REQ-007

Description:

Implement detection of the current site from the request path.

The first application path component must determine which site namespace owns the request.

Every persistence operation must explicitly select private or shared scope.

Shared operations map predictably to:

    <installation-root>\www\<site>\data\data.json

Private operations map predictably to:

    %APPDATA%\MiniServerData\<site>\data\data.json

based on the site namespace and scope addressed by the request URL.

The client must not be able to provide an arbitrary filesystem path or persistence location.

This task provides namespace and filesystem scoping. It does not introduce authentication or authorization between hosted applications.

Acceptance:

- Site names are derived from request paths.
- Requests are mapped to the private or shared persistence file belonging to the addressed site namespace.
- No default or unscoped persistence mapping exists.
- Clients cannot override the derived persistence location with an arbitrary filesystem path or storage location.
- The server-side mapping accepts the canonical application API namespace `/<site>/api/<scope>/...` that MiniApi will target automatically when T-007 is implemented.
- Invalid or unsafe filesystem paths are rejected.
- The implementation does not claim authentication or authorization isolation between hosted applications.

---

## T-005 — Implement JSON Persistence Layer

Status: Done

Related requirements:

- REQ-003
- REQ-007

Description:

Implement generic file-based JSON persistence for each valid application site and explicit persistence scope.

The shared location is:

    <installation-root>\www\<site>\data\data.json

The private location is:

    %APPDATA%\MiniServerData\<site>\data\data.json

Each persistence file must contain a JSON object at its root.

The top-level properties of that object represent named sections.

The empty persistence state is:

    {}

Section values may contain any valid JSON-compatible value, including objects, arrays, strings, numbers, booleans, and null.

The persistence layer must support:

- Reading one section
- Reading the complete root object
- Creating one or more sections
- Replacing one or more existing sections
- Preserving unrelated sections during partial writes
- Removing one section
- Clearing all sections

A missing persistence file must behave as an empty persistence store for read-all and clear operations.

Reading or removing a specific section from a missing persistence file must behave as though that section does not exist.

A valid write operation may create the required `data` directory and `data.json` file inside an already existing valid application directory.

The persistence layer must not create arbitrary application directories from unknown site names.

If an existing persistence file contains invalid JSON or does not contain a JSON object at its root, the operation must fail rather than silently resetting or reinterpreting the file.

write, remove, and clear must each obtain a short-lived exclusive file lock associated with the target persistence file using a bounded timeout.

The complete read-modify-write operation must occur while holding that lock. Writes must be atomic so readers observe the previous complete file or the new complete file. Reads do not acquire a separate read lock.

Failure to obtain the required lock must fail with the external error `Write failed`.

Persistence-file locks are separate from T-002 runtime locks.

Successful modifying operations must leave valid JSON persistence data.

Acceptance:

- Persistence files use a JSON object as their root value.
- Shared and private persistence locations are derived by the server.
- Every persistence operation has an explicit scope.
- Top-level properties represent sections.
- Section values preserve valid JSON-compatible data without application-specific interpretation.
- `read` can retrieve one existing section.
- A stored JSON null value remains distinguishable from a missing section.
- `readAll` retrieves the complete root object.
- A missing persistence file behaves as `{}` for `readAll`.
- `write` can create one or more sections.
- `write` can replace one or more existing sections.
- Sections omitted from a write remain unchanged.
- Missing persistence files and data directories can be created during a valid write.
- `remove` removes exactly one existing section.
- `clear` produces the logical empty state `{}`.
- Clearing an empty or not-yet-created persistence store succeeds.
- Invalid JSON persistence data is detected.
- A non-object persistence root is rejected.
- Failed modifications are not reported as successful.
- Successful modifications do not intentionally leave invalid or partially written JSON.
- Modifying operations use a bounded, short-lived exclusive target-file lock.
- Writes are atomic and reads remain lock-free.
- A write-lock timeout fails cleanly with `Write failed`.
- Persistence files are created only inside an approved location for an already existing valid application namespace.

---

## T-006 — Implement HTTP API Endpoints

Status: Done

Related requirements:

- REQ-003
- REQ-007

Description:

Implement the central server-side persistence API according to D-022.

The required endpoints and HTTP methods are:

    GET    /<site>/api/<scope>/read?section=<name>
    GET    /<site>/api/<scope>/readAll
    POST   /<site>/api/<scope>/write
    DELETE /<site>/api/<scope>/remove?section=<name>
    DELETE /<site>/api/<scope>/clear

scope must be private or shared. No unscoped endpoint exists.

All valid application sites must use the same central Java implementation.

The site namespace and persistence scope must be derived from the request URL.

The client must not be able to supply an arbitrary filesystem path or persistence location.

`read` returns the stored JSON value of one section directly.

`readAll` returns the complete persistence root object directly.

`write` accepts a non-empty JSON object whose top-level properties are the sections to create or replace.

`remove` removes exactly one section.

`clear` resets the site's persistence state to the logical equivalent of `{}`.

Successful JSON-returning operations use:

    200 OK

with:

    Content-Type: application/json; charset=utf-8

Successful modifying operations use:

    204 No Content

with an empty response body.

API errors use the structure:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

The API must use the following error categories where applicable:

    400 Bad Request
    404 Not Found
    405 Method Not Allowed
    415 Unsupported Media Type
    500 Internal Server Error

Section names must follow the validation rules preserved by D-022.

An unknown application namespace must not cause Mini Server to create a new application directory.

Reserved Mini Server areas such as `www/_shared/` must not be treated as normal application API namespaces.

Acceptance:

- `GET /<site>/api/<scope>/read` returns an existing section with `200 OK`.
- Reading a missing section returns `404 Not Found`.
- A stored JSON null section value returns successfully rather than being treated as missing.
- `GET /<site>/api/<scope>/readAll` returns the complete root object with `200 OK`.
- `readAll` returns `{}` when the persistence file does not yet exist.
- `POST /<site>/api/<scope>/write` accepts a valid non-empty JSON object.
- A successful write returns `204 No Content`.
- Invalid or empty write payloads return an appropriate error.
- Write requests with an unacceptable JSON content type return `415 Unsupported Media Type`.
- `DELETE /<site>/api/<scope>/remove` removes one existing section.
- Removing a missing section returns `404 Not Found`.
- A successful removal returns `204 No Content`.
- `DELETE /<site>/api/<scope>/clear` clears the selected persistence state.
- Clearing an empty or not-yet-created persistence store succeeds.
- A successful clear returns `204 No Content`.
- Known API operations reject unsupported HTTP methods with `405 Method Not Allowed`.
- Unknown API operations return `404 Not Found`.
- Unknown application namespaces return an appropriate not-found response.
- API errors use the defined JSON error structure.
- Browser-facing API errors do not expose unnecessary absolute filesystem paths.
- Requests are scoped to the site namespace addressed by the URL.
- Every request explicitly selects private or shared scope.
- Clients cannot override the derived persistence location with arbitrary filesystem paths.
- Failed server operations never return a successful HTTP status.

---

## T-007 — Implement Shared mini-api.js Library

Status: Planned

Related requirements:

- REQ-004

Description:

Implement the shared browser-side JavaScript client library:

    www/_shared/mini-api.js

The library must expose the global object:

    MiniApi

with the public v1.0 operation-first interface:

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

Every persistence operation requires an explicit terminal `.private()` or `.shared()` selector. No default scope exists, and scope-first syntax is invalid.

The terminal scope selector executes the asynchronous operation and returns a native JavaScript Promise.

The library must use browser-native functionality and remain dependency-free unless a later approved decision changes this requirement.

### Site Detection

MiniApi must automatically derive the current application site from:

    window.location.pathname

For a page below:

    /example/

MiniApi must automatically use:

    /example/api/<scope>/

as its persistence API namespace.

Application developers must not need to configure the site name manually.

MiniApi must not accept arbitrary filesystem paths or persistence locations.

Automatic site detection is a convenience and scoping mechanism, not an authentication boundary.

### read

The operation:

    MiniApi.read(section).private()
    MiniApi.read(section).shared()

must send:

    GET /<site>/api/<scope>/read?section=<encoded-name>

The section name must be validated before the request where practical.

The section name must be URL-encoded correctly.

A successful `200 OK` JSON response must be deserialized automatically.

The returned Promise must resolve with the native JavaScript value stored in the section.

A stored JSON null value must therefore resolve successfully to:

    null

A missing section or another unsuccessful HTTP response must reject the Promise.

### readAll

The method:

    MiniApi.readAll().private()
    MiniApi.readAll().shared()

must send:

    GET /<site>/api/<scope>/readAll

A successful `200 OK` JSON response must be deserialized automatically.

The returned Promise must resolve with the complete native JavaScript persistence object.

An empty persistence store therefore resolves to:

    {}

### write

The method:

    MiniApi.write(data).private()
    MiniApi.write(data).shared()

must accept a native JavaScript object whose own top-level properties represent the sections to create or replace.

Example:

    await MiniApi.write({
        settings: {
            theme: "dark"
        },
        favorites: [
            "Search A",
            "Search B"
        ]
    }).shared();

The caller must not need to use:

    JSON.stringify()

MiniApi must serialize the request body internally.

The request must use:

    POST /<site>/api/<scope>/write

with an appropriate JSON content type.

The supplied `data` value must be rejected when it is:

- null;
- an array;
- not an object;
- an object containing no own top-level properties.

Each top-level property name must satisfy the section-name rules defined by D-022.

The supplied section values must be representable as valid JSON.

Serialization or validation failures must reject the Promise and must not be reported as successful operations.

A successful server response is:

    204 No Content

After successful completion, the Promise must resolve with:

    undefined

MiniApi must not expect a JSON response body for a successful write.

### remove

The method:

    MiniApi.remove(section).private()
    MiniApi.remove(section).shared()

must send:

    DELETE /<site>/api/<scope>/remove?section=<encoded-name>

The section name must be validated and URL-encoded correctly.

A successful server response is:

    204 No Content

After successful completion, the Promise must resolve with:

    undefined

A missing section or another unsuccessful HTTP response must reject the Promise.

### clear

The method:

    MiniApi.clear().private()
    MiniApi.clear().shared()

must send:

    DELETE /<site>/api/<scope>/clear

A successful server response is:

    204 No Content

After successful completion, the Promise must resolve with:

    undefined

MiniApi must not attempt to parse a response body after a successful clear operation.

### Section Validation

Section names supplied to MiniApi must follow the API contract defined by D-022.

A valid section name:

- contains between 1 and 128 characters;
- is not empty;
- has no leading whitespace;
- has no trailing whitespace;
- contains no control characters.

Unicode characters and normal characters such as spaces, hyphens, underscores, and periods may be used inside a section name.

The same section-name validation must be applied to:

    MiniApi.read(section).private()
    MiniApi.remove(section).shared()

and to the top-level property names supplied to:

    MiniApi.write(data).private()

Client-side validation must remain generic and must not contain application-specific business rules.

### JSON Handling

MiniApi must handle JSON serialization and deserialization internally.

Application code must not normally need to call:

    JSON.stringify()

or:

    JSON.parse()

when using MiniApi.

Successful `read()` and `readAll()` responses must be parsed into native JavaScript values.

`write()` must serialize native JavaScript data into the request body.

Successful `write()`, `remove()`, and `clear()` operations return `204 No Content` and therefore must not trigger JSON response parsing.

### Error Handling

MiniApi must reject its Promise whenever an operation does not complete successfully.

This includes:

- Client-side validation failures
- JSON serialization failures
- Network failures
- Non-successful HTTP responses
- Invalid or unexpected JSON responses when JSON is expected

The server's standard error response has the form:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

When this information is available, the rejected error exposed to application code must make the following values accessible:

- HTTP status
- Server error code
- Human-readable message

The exact internal JavaScript error implementation may be chosen during implementation.

MiniApi must not silently transform a failed HTTP request into a successful JavaScript result.

Unexpected or malformed server responses must reject rather than being silently ignored.

### HTTP Contract

MiniApi must use exactly the server-side API contract defined by D-022:

    GET    /<site>/api/<scope>/read?section=<name>
    GET    /<site>/api/<scope>/readAll
    POST   /<site>/api/<scope>/write
    DELETE /<site>/api/<scope>/remove?section=<name>
    DELETE /<site>/api/<scope>/clear

MiniApi must not substitute different HTTP methods or endpoint names.

Operations returning data expect:

    200 OK

Successful modifying operations expect:

    204 No Content

Any non-successful HTTP status must reject the Promise.

### Generic Behavior

The shared library must remain application-independent.

It must not:

- Interpret application-specific section contents
- Contain application-specific business rules
- Contain hard-coded application names
- Expose arbitrary persistence filesystem paths
- Require separate copies for individual applications

One shared `mini-api.js` implementation must work for all normal hosted applications.

Acceptance:

- `www/_shared/mini-api.js` exists.
- The library exposes the global `MiniApi` object.
- Public operations are `read`, `readAll`, `write`, `remove`, and `clear`.
- Every operation requires a terminal `.private()` or `.shared()`.
- Operation-first chain order is enforced and no default scope exists.
- The terminal scope selector returns a Promise.
- MiniApi automatically derives the current site namespace.
- Application code does not need to configure its site name.
- Every generated endpoint uses `/<site>/api/<scope>/<operation>`.
- `read()` uses the correct GET endpoint.
- `read()` resolves with the native stored section value.
- A stored JSON null value resolves successfully to null.
- `readAll()` uses the correct GET endpoint.
- `readAll()` resolves with the complete native root object.
- `write()` uses the correct POST endpoint.
- `write()` accepts a non-empty native JavaScript object.
- `write()` can send one section.
- `write()` can send multiple sections.
- Invalid write roots are rejected.
- Invalid section names are rejected.
- MiniApi performs JSON serialization internally.
- MiniApi performs JSON deserialization internally.
- Application code does not normally require manual `JSON.stringify()` or `JSON.parse()`.
- A successful write resolves with `undefined`.
- `remove()` uses the correct DELETE endpoint.
- A successful removal resolves with `undefined`.
- `clear()` uses the correct DELETE endpoint.
- A successful clear resolves with `undefined`.
- Successful 204 responses are not parsed as JSON.
- Section names are URL-encoded correctly.
- Non-successful HTTP responses reject.
- Network failures reject.
- Server error status, code, and message remain accessible when available.
- The library contains no application-specific business logic.
- The same library works for multiple hosted applications.

---

## T-008 — Create Example Application

Status: Planned

Related requirements:

- REQ-005

Description:

Create the initial application:

www/example/

The application must demonstrate all public MiniApi operations.

Every demonstration must use operation-first chaining with an explicit private or shared selector. The example must make both scopes understandable.

The example should remain small and developer-focused.

Acceptance:

- The example application loads successfully.
- Read can be demonstrated.
- ReadAll can be demonstrated.
- Write can be demonstrated.
- Remove can be demonstrated.
- Clear can be demonstrated.
- The application uses the shared mini-api.js library.
- The application demonstrates both shared and private persistence locations.

---

## T-009 — Create Reusable Template Package

Status: Planned

Related requirements:

- REQ-005

Description:

Create the reusable starter template and distribute it as:

    miniweb-template.zip

The template archive must be stored outside the `www` web root.

The archive must contain application content that can be extracted or copied into a new first-level application directory below `www/`.

For example:

    www/my-app/

The extracted template must use the shared:

    /_shared/mini-api.js

library and demonstrate the public MiniApi interface.

The template must include a minimal visible:

    Hello Mini Webserver

demonstration.

The displayed value must be loaded from the application's bundled shared persistence data using:

    MiniApi.read("start").shared()

The template must remain application-neutral and suitable as a clean starting point for developers.

Acceptance:

- `miniweb-template.zip` is created.
- The archive is stored outside the `www` web root.
- The template can be extracted into a new first-level application directory below `www/`.
- The extracted application loads successfully.
- The extracted application uses the shared `mini-api.js` library.
- The extracted application can use its shared `data/data.json` and private user-profile persistence file.
- The template demonstrates the public MiniApi interface.
- `Hello Mini Webserver` is displayed using data returned by `MiniApi.read("start").shared()`.
- The template clearly indicates that its demonstration content may be replaced by the developer.
- Creating a new application from the template does not require application-specific Java server changes.

---

## T-010 — Implement Edge Browser Launch

Status: Planned

Related requirements:

- REQ-002
- REQ-006
- REQ-008

Description:

Implement Microsoft Edge launch behavior for both a newly started local Mini Server instance and a repeated start in the same local user/computer context.

The browser URL must always be constructed from the actual active Mini Server instance.

The URL format is:

    http://127.0.0.1:<active-port><start-target>

The configured start target identifies the application path that should be opened, for example:

    /example/

Mini Server must never construct the browser URL using a guessed, fixed, stale, or scanned TCP port.

### First Start

After a new Mini Server server instance has:

1. Acquired local startup.lock and instance.lock as defined by D-020
2. Bound successfully to `127.0.0.1`
3. Requested TCP port `0`
4. Obtained the actual operating-system-assigned port
5. Become ready to accept HTTP requests
6. Published valid runtime state

the browser-launch logic must construct the local application URL using that assigned port.

Example:

    http://127.0.0.1:51847/example/

Microsoft Edge must then be opened with that URL.

Edge must not be opened before the active server is ready.

### Repeated Start

When startup detects an active server in the same local user/computer context, no second HTTP server is started.

The repeated-start process obtains the active server port from valid runtime state.

It then constructs the browser URL using:

- `127.0.0.1`
- The port of the existing active Mini Server instance
- The configured start target

For example, if the active instance uses port:

    51847

the repeated start opens:

    http://127.0.0.1:51847/example/

The repeated-start process must not:

- Request another server port
- Start another HTTP server
- Replace the active instance's runtime state
- Assume that a stale state file represents a running server

After requesting Edge to open the existing Mini Server URL, the repeated-start process terminates.

### Microsoft Edge

Mini Server v1.0 uses the normally installed Microsoft Edge browser for the standard Windows startup experience.

Mini Server must not require an embedded or dedicated browser.

Launching Mini Server must not prevent the user from using Edge normally for unrelated websites, tabs, or windows.

Mini Server does not need to control whether Edge opens:

- A new tab
- A new window
- An existing Edge process
- A new Edge process

That behavior may be determined by Microsoft Edge and Windows.

The Mini Server responsibility is limited to requesting that the correct local URL be opened.

### Browser Launch Failure

Failure to launch Microsoft Edge must not corrupt or invalidate an otherwise successfully running Mini Server instance.

If the server has started successfully but Edge cannot be opened:

- The server may continue running
- Its runtime state remains valid
- Its active port remains available
- A clear diagnostic message must be produced
- The complete local URL must be made available so that the user can open it manually

A browser-launch failure must not cause Mini Server to report an incorrect server port or modify persistence data.

### Server Lifetime

The Edge process is not the owner of the Mini Server server lifetime.

Closing:

- The Mini Server tab
- An Edge window
- All Edge windows

must not intentionally terminate the active Mini Server Java process.

A later desktop start can therefore reopen the already running local Mini Server instance using the repeated-start behavior defined by D-020.

Acceptance:

- The generated URL uses `127.0.0.1`.
- The generated URL contains the actual active Mini Server port.
- The configured start target is included in the URL.
- A newly started server is ready before Edge is opened.
- A first start uses the newly assigned operating-system-selected port.
- A repeated start uses the existing active server's port.
- A repeated start does not start another HTTP server.
- A repeated start does not request another TCP port.
- A repeated start opens the existing Mini Server URL in Edge.
- The repeated-start process terminates after requesting the browser launch.
- A stale state file alone is not used to select a browser URL.
- No fixed, guessed, or scanned port is used for browser launch.
- Mini Server uses the normally installed Microsoft Edge browser.
- Mini Server does not require control over Edge tab or window reuse behavior.
- Failure to launch Edge does not corrupt or terminate an otherwise successfully running server.
- The complete local URL remains available for manual use if browser launch fails.
- Closing Edge does not intentionally terminate Mini Server.

---

## T-011 — Implement Error Handling and Diagnostics

Status: Planned

Related requirements:

- REQ-007

Description:

Implement consistent error handling for startup, static file access, API requests, JSON parsing, persistence, and filesystem permissions.

Diagnostics should be useful without exposing unnecessary application data or internal filesystem paths to browser clients.

Acceptance:

- Startup failures are visible.
- Missing files are handled correctly.
- Permission errors are handled correctly.
- Invalid JSON is detected.
- Invalid API calls are rejected.
- Browser-facing messages do not expose unnecessary absolute paths.
- Diagnostic output is sufficient for development and maintenance.

---

## T-012 — Verify Java 8 Runtime Compatibility

Status: Planned

Related requirements:

- REQ-008

Description:

Verify the completed implementation using a Java 8 compatible runtime.

Development with a newer JDK must not hide incompatibilities with the actual target runtime.

Acceptance:

- The project compiles for Java 8.
- The application starts using Java 8.
- Static file serving works using Java 8.
- JSON API operations work using Java 8.
- Browser startup behavior works with the Java 8 build.
- No dependency requires a newer Java runtime.

---

## T-013 — Add Automated Tests

Status: Planned

Related requirements:

- REQ-001
- REQ-002
- REQ-003
- REQ-004
- REQ-006
- REQ-007
- REQ-008

Description:

Add automated tests for behavior that can be verified reliably without manual browser interaction.

Priority should be given to:

- Path handling
- Path traversal prevention
- Site isolation
- Explicit shared/private persistence mapping
- JSON read and write behavior
- Bounded persistence write-lock behavior
- Atomic persistence writes
- Section replacement
- Section removal
- Clear behavior
- Invalid JSON handling
- Invalid API requests
- Dynamic port startup
- Local per-user/computer single-instance behavior
- Shared-installation use from separate computer contexts

Acceptance:

- Core server behavior has repeatable automated tests.
- Tests can be run through the project's normal test command.
- Failed tests result in a failing test process.
- Test results are not claimed unless the tests were actually executed.

---

## T-014 — Verify Initial Release Scope

Status: Planned

Related requirements:

- REQ-001
- REQ-002
- REQ-003
- REQ-004
- REQ-005
- REQ-006
- REQ-007
- REQ-008

Description:

Perform an end-to-end verification of the initial Mini Server implementation against all active requirements.

Acceptance:

- Every active requirement has been reviewed.
- Each acceptance criterion is either verified or explicitly documented as incomplete.
- No known incomplete requirement is presented as finished.
- The example application works end to end.
- The template package can be extracted into a new application that works end to end.
- The Java 8 target runtime has been verified.
- The project is ready for release preparation only when all required criteria are satisfied.

---

## Current Execution Order

The intended initial implementation order is:

T-001
T-002
T-003
T-004
T-005
T-006
T-007
T-008
T-009
T-010
T-011
T-012
T-013
T-014

The order may be adjusted when technically useful, but requirement dependencies must remain respected.
