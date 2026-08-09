# REQ-007 — Error Handling and File Permissions

## Requirement ID

REQ-007

## Title

Error Handling and File Permissions

## Status

Active

## Purpose

Mini Server must handle runtime, filesystem, and API errors in a predictable and understandable way.

Failures must not be reported as successful operations.

The implementation should provide enough information for users and developers to understand what went wrong without exposing unnecessary internal system details.

## Description

Mini Server operates with local files below the configured web root.

The server must therefore handle common failure conditions such as:

- Missing files
- Missing directories
- Invalid JSON
- Read failures
- Write failures
- Insufficient filesystem permissions
- Invalid API requests
- Invalid site paths
- Attempts to access resources outside the permitted web root
- Unexpected internal errors

The server must distinguish successful operations from failed operations clearly.

## Read Permissions

The server requires sufficient permissions to read static files and application data that should be served.

If a requested file exists but cannot be read because of filesystem permissions or another I/O problem, the request must fail cleanly.

The server must not report unreadable content as successfully returned.

## Write Permissions

Write operations require sufficient filesystem permissions for the corresponding application's data location.

For example:

www/example/data/data.json

must be writable when the application performs a persistence operation that modifies data.

If the required write access is unavailable, the API operation must fail.

The existing data file must not be intentionally replaced with incomplete or invalid content.

## Missing Data File

A missing data file is not necessarily an error.

When a write operation requires a site's data file and the file does not yet exist, Mini Server may create:

www/<site>/data/data.json

when the required directory is valid and writable.

If the required data directory does not exist, the implementation may create it when this can be done safely within the current site's directory.

The server must never create the data file outside the current site's permitted location.

## Invalid JSON Data

If an existing data file contains invalid JSON, the server must not silently overwrite or reinterpret the file as though it contained valid data.

Read operations must report the failure.

Write operations must not claim success unless the resulting stored data is valid JSON.

The exact recovery behavior for an already corrupted data file may be defined during implementation.

Automatic destructive recovery must not occur without an explicit and documented rule.

## API Errors

Invalid API requests must return a clear error response.

Examples include:

- Missing required parameters
- Invalid section names
- Invalid JSON request bodies
- Unsupported operations
- Invalid application scope
- Requests that violate application isolation
- Attempts to access arbitrary filesystem paths

A failed API request must not return a successful result.

## HTTP Status Handling

The server should use appropriate HTTP status codes.

Typical categories include:

- Successful request
- Invalid client request
- Resource not found
- Access or permission failure
- Unexpected server-side failure

The exact mapping of individual conditions to HTTP status codes may be standardized during implementation.

The API response body should provide a concise description of the failure where useful.

## Filesystem Information

Error responses sent to the browser must not expose unnecessary internal filesystem information.

For example, an API response should not need to reveal absolute paths such as:

C:\Users\...
/home/...
/volume2/...

Internal diagnostic logging may contain additional technical detail when appropriate, but browser-facing messages should remain limited to information needed to understand the failure.

## Data Integrity

A failed write operation must not intentionally leave the site's JSON data in a partially written state.

Where practical, the implementation should write updates in a way that minimizes the risk of corrupting a previously valid data file.

The server must only report a write operation as successful after the intended data has been stored successfully.

## Read-Only Operation

A hosted web application may still be usable for static content and read operations when its data file is not writable.

Write-related API operations must return a clear error when the required write permissions are unavailable.

The server must not require write access to unrelated application directories.

## Optional Read-Only Indicator

A site may optionally use a file named:

readonly.flag

as a convenient indicator that write operations should not be performed for that site.

This mechanism is a convenience feature and is not a security boundary.

Filesystem permissions remain authoritative.

The exact behavior of `readonly.flag` may be finalized during implementation.

## Logging

The server should provide useful diagnostic information for failures during development and operation.

Logging should be concise and should not expose application data unnecessarily.

Normal operation should not require detailed logging of application content.

## Startup Errors

Fatal initialization errors must prevent Mini Server from pretending to have started successfully.

Examples include:

- The local listening socket cannot be created.
- The configured web root cannot be accessed.
- Required server initialization fails.

When startup fails, the browser must not be opened with an unusable Mini Server URL.

## Acceptance Criteria

REQ-007 is fulfilled when all of the following are true:

- Missing static files return an appropriate error.
- Unreadable files do not produce a successful response.
- Write operations fail clearly when filesystem permissions are insufficient.
- Failed write operations are not reported as successful.
- A missing data file can be created when the correct site directory is writable.
- Data files are never created outside the permitted site directory.
- Invalid JSON in an existing data file is detected.
- Invalid JSON is not silently treated as valid application data.
- Malformed API requests return an error.
- Attempts to escape the permitted site or web root are rejected.
- Browser-facing errors do not expose unnecessary absolute filesystem paths.
- A failed write does not intentionally leave partially written JSON data.
- Read-only filesystem access does not prevent unrelated static or read-only functionality where technically possible.
- Startup failures prevent automatic browser launch.
- Diagnostic logging provides enough information to investigate failures.
- Normal operation does not require detailed logging of application content.

## Constraints

Error handling must remain lightweight and understandable.

The implementation must remain compatible with the project's approved Java runtime requirements.

Filesystem permissions provided by the operating system remain authoritative.

Application-level convenience features must not be described as security mechanisms unless they actually provide a security boundary.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-005 — No Database
- D-006 — Generic Server-Side Data Handling
- D-007 — One JSON Data File per Application
- D-008 — Application Scope Is Derived from the URL
- D-014 — Not Intended for Public Internet Use

## Related Requirements

- REQ-001 — Static File Serving
- REQ-003 — JSON Persistence API
- REQ-006 — Startup and Browser Launch

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Static File Serving
- Central API
- Data Model
- Application Isolation
- Network Boundary

## Related Tasks

No implementation tasks have been assigned yet.

## Target Release

Initial release