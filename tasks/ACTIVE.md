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

## T-004 — Implement Site Detection and Isolation

Status: Planned

Related requirements:

- REQ-003
- REQ-004
- REQ-007

Description:

Implement detection of the current site from the request path.

The first application path component must determine which site owns the request.

All persistence operations must remain scoped to:

www/<site>/data/data.json

The client must not be able to provide an arbitrary filesystem path.

Acceptance:

- Site names are derived from request paths.
- Requests are mapped to the correct site's data file.
- One site cannot access another site's persistence file through the API.
- Invalid or unsafe paths are rejected.

---

## T-005 — Implement JSON Persistence Layer

Status: Planned

Related requirements:

- REQ-003
- REQ-007

Description:

Implement generic file-based JSON persistence for each application.

The persistence layer must support:

- Reading one section
- Reading all sections
- Creating a section
- Replacing a section
- Writing multiple sections
- Removing one section
- Clearing all sections

The server must not interpret application-specific data.

Acceptance:

- Valid JSON data can be read and written.
- Existing unrelated sections remain unchanged during partial writes.
- Missing data files can be created when appropriate.
- Invalid JSON is detected.
- Failed writes are not reported as successful.
- A failed write does not intentionally leave invalid JSON behind.

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

Create:

www/_shared/mini-api.js

The library must expose:

MiniApi.read(section)

MiniApi.readAll()

MiniApi.write(data)

MiniApi.remove(section)

MiniApi.clear()

The library must determine the current site automatically from the browser URL.

JSON serialization and deserialization must be handled internally.

Acceptance:

- The same library works for multiple sites.
- Native JavaScript objects and arrays can be used directly.
- Application code does not require JSON.stringify() or JSON.parse() for normal API use.
- Basic input validation is performed.
- API failures remain visible to the caller.

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

## T-009 — Create Template Application

Status: Planned

Related requirements:

- REQ-005

Description:

Create:

www/template/

The initial template should provide the same minimal API demonstration as the example application while remaining suitable as a clean starting point for developers.

The page must include:

Hello Mini Webserver

and a clear indication that the demonstration content can be replaced with the developer's own application.

Acceptance:

- The template application loads successfully.
- It uses the shared mini-api.js library.
- It has its own data/data.json file.
- It demonstrates the public API.
- It remains independent from the example application's data.
- It can be copied as the basis for a new application.

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
- The template application works end to end.
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