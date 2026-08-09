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

Status: Planned

Related requirements:

- REQ-001
- REQ-002
- REQ-006
- REQ-008

Description:

Create the initial Java project structure for Mini Server.

The project must be configured to remain compatible with Java 8.

The initial structure should provide a clean separation between:

- Server startup
- HTTP request handling
- Static file handling
- API handling
- File persistence
- Browser launch support

Avoid unnecessary dependencies.

The exact package structure may be chosen during implementation as long as it remains simple and understandable.

Acceptance:

- The project builds successfully.
- The produced code targets Java 8.
- A minimal server application can be started.
- The project structure is ready for the remaining implementation tasks.

---

## T-002 — Implement Dynamic Local Server Startup

Status: Planned

Related requirements:

- REQ-002
- REQ-006
- REQ-008

Description:

Implement startup of the HTTP server on:

127.0.0.1

using TCP port:

0

The operating system must assign an available port automatically.

After startup, the actual assigned port must be determined from the running server instance.

Acceptance:

- The server binds to 127.0.0.1.
- Port 0 is requested.
- The assigned runtime port can be retrieved.
- No manual port scanning is used.
- Startup failures are reported clearly.

---

## T-003 — Implement Static File Serving

Status: Planned

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

Status: Planned

Related requirements:

- REQ-003
- REQ-004
- REQ-007

Description:

Implement detection of the current site from the request path.

The first application path component must determine which site namespace owns the request.

Persistence operations must map predictably to:

www/<site>/data/data.json

based on the site namespace addressed by the request URL.

The client must not be able to provide an arbitrary filesystem path or persistence location.

This task provides namespace and filesystem scoping. It does not introduce authentication or authorization between hosted applications.

Acceptance:

- Site names are derived from request paths.
- Requests are mapped to the persistence file belonging to the addressed site namespace.
- Clients cannot override the derived persistence location with an arbitrary filesystem path or storage location.
- Normal MiniApi usage is automatically scoped to the current application's API namespace.
- Invalid or unsafe filesystem paths are rejected.
- The implementation does not claim authentication or authorization isolation between hosted applications.

---

## T-005 — Implement JSON Persistence Layer

Status: Planned

Related requirements:

- REQ-003
- REQ-007

Description:

Implement generic file-based JSON persistence for each valid application site.

The persistence location is:

    www/<site>/data/data.json

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

Successful modifying operations must leave valid JSON persistence data.

Acceptance:

- Persistence files use a JSON object as their root value.
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
- Persistence files are created only inside an already existing valid application directory.

---

## T-006 — Implement HTTP API Endpoints

Status: Planned

Related requirements:

- REQ-003
- REQ-007

Description:

Implement the central server-side persistence API according to D-017.

The required endpoints and HTTP methods are:

    GET    /<site>/api/read?section=<name>
    GET    /<site>/api/readAll
    POST   /<site>/api/write
    DELETE /<site>/api/remove?section=<name>
    DELETE /<site>/api/clear

All valid application sites must use the same central Java implementation.

The site namespace must be derived from the request URL.

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

Section names must follow the validation rules defined by D-017.

An unknown application namespace must not cause Mini Server to create a new application directory.

Reserved Mini Server areas such as `www/_shared/` must not be treated as normal application API namespaces.

Acceptance:

- `GET /<site>/api/read` returns an existing section with `200 OK`.
- Reading a missing section returns `404 Not Found`.
- A stored JSON null section value returns successfully rather than being treated as missing.
- `GET /<site>/api/readAll` returns the complete root object with `200 OK`.
- `readAll` returns `{}` when the persistence file does not yet exist.
- `POST /<site>/api/write` accepts a valid non-empty JSON object.
- A successful write returns `204 No Content`.
- Invalid or empty write payloads return an appropriate error.
- Write requests with an unacceptable JSON content type return `415 Unsupported Media Type`.
- `DELETE /<site>/api/remove` removes one existing section.
- Removing a missing section returns `404 Not Found`.
- A successful removal returns `204 No Content`.
- `DELETE /<site>/api/clear` clears the current site's persistence state.
- Clearing an empty or not-yet-created persistence store succeeds.
- A successful clear returns `204 No Content`.
- Known API operations reject unsupported HTTP methods with `405 Method Not Allowed`.
- Unknown API operations return `404 Not Found`.
- Unknown application namespaces return an appropriate not-found response.
- API errors use the defined JSON error structure.
- Browser-facing API errors do not expose unnecessary absolute filesystem paths.
- Requests are scoped to the site namespace addressed by the URL.
- Clients cannot override the derived persistence location with arbitrary filesystem paths.
- Failed server operations never return a successful HTTP status.

---

## T-006 — Implement HTTP API Endpoints

Status: Planned

Related requirements:

- REQ-003
- REQ-007

Description:

Implement the central server-side API endpoints:

/<site>/api/read?section=<name>

/<site>/api/readAll

/<site>/api/write

/<site>/api/remove?section=<name>

/<site>/api/clear

All sites must use the same central Java implementation.

Acceptance:

- All required API operations are available.
- Requests are scoped to the correct site.
- Invalid requests return appropriate errors.
- Server failures do not return successful responses.
- API responses do not expose unnecessary filesystem details.

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

with the public v1.0 methods:

    MiniApi.readSection(section)
    MiniApi.readAll()
    MiniApi.write(data)
    MiniApi.removeSection(section)
    MiniApi.clear()

All public MiniApi operations must be asynchronous and return native JavaScript Promises.

The library must use browser-native functionality and remain dependency-free unless a later approved decision changes this requirement.

### Site Detection

MiniApi must automatically derive the current application site from:

    window.location.pathname

For a page below:

    /example/

MiniApi must automatically use:

    /example/api/

as its persistence API namespace.

Application developers must not need to configure the site name manually.

MiniApi must not accept arbitrary filesystem paths or persistence locations.

Automatic site detection is a convenience and scoping mechanism, not an authentication boundary.

### readSection

The method:

    MiniApi.readSection(section)

must send:

    GET /<site>/api/read?section=<encoded-name>

The section name must be validated before the request where practical.

The section name must be URL-encoded correctly.

A successful `200 OK` JSON response must be deserialized automatically.

The returned Promise must resolve with the native JavaScript value stored in the section.

A stored JSON null value must therefore resolve successfully to:

    null

A missing section or another unsuccessful HTTP response must reject the Promise.

### readAll

The method:

    MiniApi.readAll()

must send:

    GET /<site>/api/readAll

A successful `200 OK` JSON response must be deserialized automatically.

The returned Promise must resolve with the complete native JavaScript persistence object.

An empty persistence store therefore resolves to:

    {}

### write

The method:

    MiniApi.write(data)

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
    });

The caller must not need to use:

    JSON.stringify()

MiniApi must serialize the request body internally.

The request must use:

    POST /<site>/api/write

with an appropriate JSON content type.

The supplied `data` value must be rejected when it is:

- null;
- an array;
- not an object;
- an object containing no own top-level properties.

Each top-level property name must satisfy the section-name rules defined by D-017.

The supplied section values must be representable as valid JSON.

Serialization or validation failures must reject the Promise and must not be reported as successful operations.

A successful server response is:

    204 No Content

After successful completion, the Promise must resolve with:

    undefined

MiniApi must not expect a JSON response body for a successful write.

### removeSection

The method:

    MiniApi.removeSection(section)

must send:

    DELETE /<site>/api/remove?section=<encoded-name>

The section name must be validated and URL-encoded correctly.

A successful server response is:

    204 No Content

After successful completion, the Promise must resolve with:

    undefined

A missing section or another unsuccessful HTTP response must reject the Promise.

### clear

The method:

    MiniApi.clear()

must send:

    DELETE /<site>/api/clear

A successful server response is:

    204 No Content

After successful completion, the Promise must resolve with:

    undefined

MiniApi must not attempt to parse a response body after a successful clear operation.

### Section Validation

Section names supplied to MiniApi must follow the API contract defined by D-017.

A valid section name:

- contains between 1 and 128 characters;
- is not empty;
- has no leading whitespace;
- has no trailing whitespace;
- contains no control characters.

Unicode characters and normal characters such as spaces, hyphens, underscores, and periods may be used inside a section name.

The same section-name validation must be applied to:

    MiniApi.readSection(section)
    MiniApi.removeSection(section)

and to the top-level property names supplied to:

    MiniApi.write(data)

Client-side validation must remain generic and must not contain application-specific business rules.

### JSON Handling

MiniApi must handle JSON serialization and deserialization internally.

Application code must not normally need to call:

    JSON.stringify()

or:

    JSON.parse()

when using MiniApi.

Successful `readSection()` and `readAll()` responses must be parsed into native JavaScript values.

`write()` must serialize native JavaScript data into the request body.

Successful `write()`, `removeSection()`, and `clear()` operations return `204 No Content` and therefore must not trigger JSON response parsing.

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

MiniApi must use exactly the server-side API contract defined by D-017:

    GET    /<site>/api/read?section=<name>
    GET    /<site>/api/readAll
    POST   /<site>/api/write
    DELETE /<site>/api/remove?section=<name>
    DELETE /<site>/api/clear

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
- `MiniApi.readSection(section)` exists.
- `MiniApi.readAll()` exists.
- `MiniApi.write(data)` exists.
- `MiniApi.removeSection(section)` exists.
- `MiniApi.clear()` exists.
- Every public MiniApi method returns a Promise.
- MiniApi automatically derives the current site namespace.
- Application code does not need to configure its site name.
- `readSection()` uses the correct GET endpoint.
- `readSection()` resolves with the native stored section value.
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
- `removeSection()` uses the correct DELETE endpoint.
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

The example should remain small and developer-focused.

Acceptance:

- The example application loads successfully.
- Read can be demonstrated.
- ReadAll can be demonstrated.
- Write can be demonstrated.
- Remove can be demonstrated.
- Clear can be demonstrated.
- The application uses the shared mini-api.js library.
- The application uses its own data/data.json file.

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

The displayed value must be loaded from the application's persistence data using:

    MiniApi.readSection("start")

The template must remain application-neutral and suitable as a clean starting point for developers.

Acceptance:

- `miniweb-template.zip` is created.
- The archive is stored outside the `www` web root.
- The template can be extracted into a new first-level application directory below `www/`.
- The extracted application loads successfully.
- The extracted application uses the shared `mini-api.js` library.
- The extracted application has its own `data/data.json` persistence file.
- The template demonstrates the public MiniApi interface.
- `Hello Mini Webserver` is displayed using data returned by `MiniApi.readSection("start")`.
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

After successful server startup, construct the local application URL using the assigned runtime port and open it in Microsoft Edge.

The browser must only be launched after the server is ready.

Acceptance:

- The generated URL contains 127.0.0.1.
- The generated URL contains the actual assigned port.
- The configured start application is included in the URL.
- Edge is opened only after successful server startup.
- Failure to launch Edge does not corrupt or crash the running server.
- The generated local URL remains available for manual use if browser launch fails.

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
- REQ-007
- REQ-008

Description:

Add automated tests for behavior that can be verified reliably without manual browser interaction.

Priority should be given to:

- Path handling
- Path traversal prevention
- Site isolation
- JSON read and write behavior
- Section replacement
- Section removal
- Clear behavior
- Invalid JSON handling
- Invalid API requests
- Dynamic port startup

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