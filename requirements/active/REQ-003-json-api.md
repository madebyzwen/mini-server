# REQ-003 — JSON Persistence API

## Requirement ID

REQ-003

## Title

JSON Persistence API

## Status

Active

## Purpose

Mini Server must provide one generic JSON persistence API for all hosted web applications.

Every persistence operation must explicitly select shared or private storage. The server must not interpret application-specific business data.

## Persistence Scopes

Each valid first-level application below www has two persistence scopes:

    shared
    private

There is no implicit or default persistence scope.

Shared persistence is stored at:

    <installation-root>\www\<site>\data\data.json

Private persistence is stored at:

    %APPDATA%\MiniServerData\<site>\data\data.json

Both scopes use the same JSON model and operations.

Private means user-profile storage rather than shared-installation storage. It does not provide authentication, authorization, encryption, or isolation between mutually hostile applications.

## Site and Scope Mapping

The site and scope are determined from the request URL.

For example:

    GET /dashboard/api/shared/read?section=settings

operates on:

    <installation-root>\www\dashboard\data\data.json

while:

    GET /dashboard/api/private/read?section=settings

operates on:

    %APPDATA%\MiniServerData\dashboard\data\data.json

The server derives and validates both physical locations. Clients must never provide arbitrary filesystem paths or persistence locations.

An API site is valid only when the corresponding first-level application directory exists below www. Unknown site names must not create application directories. Reserved areas such as www/_shared are not application persistence namespaces.

## Static Access Boundary

The shared persistence directory:

    <installation-root>\www\<site>\data\

must not be served by the normal static file handler.

A request for:

    /<site>/data/data.json

must not expose shared persistence data. Private persistence is outside the web root and must likewise never be available through normal static serving.

## Persistence Data Model

Each shared or private data.json file contains a JSON object at its root. Top-level properties are named Sections.

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

The root must not be an array, string, number, boolean, or null.

Each Section value may contain any JSON-compatible object, array, string, number, boolean, or null. The server preserves valid values without interpreting their application-specific meaning.

## Section Names

A Section name:

- Must contain between 1 and 128 characters
- Must not be empty
- Must not contain leading or trailing whitespace
- Must not contain control characters

Unicode characters and normal spaces, hyphens, underscores, and periods may appear inside a Section name.

Section names are logical JSON property names and must never be interpreted as filesystem paths.

## HTTP API

The canonical route shape is:

    /<site>/api/<scope>/<operation>

where scope is private or shared.

The operations are:

    GET    /<site>/api/<scope>/read?section=<name>
    GET    /<site>/api/<scope>/readAll
    POST   /<site>/api/<scope>/write
    DELETE /<site>/api/<scope>/remove?section=<name>
    DELETE /<site>/api/<scope>/clear

Every request must contain a valid explicit scope. Unscoped routes and /<site>/<scope>/api/<operation> are not part of the v1 API.

The HTTP method is part of the contract. A known operation called with an unsupported method returns 405 Method Not Allowed.

## Read One Section

    GET /<site>/api/<scope>/read?section=<name>

returns one Section's stored JSON value directly.

A stored JSON null value is valid and returns successfully as null.

A missing Section returns 404 Not Found with the standard JSON error response. A missing persistence file is equivalent to the requested Section not existing.

## Read All Sections

    GET /<site>/api/<scope>/readAll

returns the selected persistence file's complete root object.

If that file does not yet exist, readAll returns:

    {}

## Write Sections

    POST /<site>/api/<scope>/write

creates or replaces one or more Sections in the selected persistence file.

The request body must be a non-empty JSON object. Its top-level properties are the Sections to create or replace.

For every supplied property:

- A missing Section is created.
- An existing Section is replaced.
- Sections omitted from the request remain unchanged.

The request must use an acceptable JSON content type. Success returns 204 No Content with an empty body.

## Data File Creation

A valid modifying operation may create the selected persistence file and its data directory when needed.

For shared scope, creation is limited to:

    <installation-root>\www\<existing-site>\data\data.json

For private scope, creation is limited to:

    %APPDATA%\MiniServerData\<existing-site>\data\data.json

The API must never create a new application namespace from an unknown site or accept a client-provided filesystem path.

## Remove One Section

    DELETE /<site>/api/<scope>/remove?section=<name>

removes exactly one Section. Other Sections remain unchanged.

Success returns 204 No Content. A missing Section or missing selected persistence file returns 404 Not Found.

## Clear All Sections

    DELETE /<site>/api/<scope>/clear

resets the selected persistence file to the logical empty state:

    {}

Clearing an empty or not-yet-created store is successful and idempotent. Success returns 204 No Content.

## Persistence Concurrency and Integrity

write, remove, and clear are write operations.

Each write operation must:

1. Obtain a short-lived exclusive file lock associated with the selected target persistence file.
2. Use a bounded lock-acquisition timeout.
3. Hold the lock for the complete read-modify-write operation.
4. Write atomically so readers never observe partial JSON.
5. Release the lock when the operation completes or fails.

Failure to obtain the lock must fail cleanly. The intentionally simple external error remains:

    Write failed

Reads do not acquire a separate read lock. A reader must see either the previous complete file or the new complete file.

Persistence-file locking is separate from Mini Server runtime startup and instance locking.

Successful modifications must leave valid JSON. If existing persistence data contains invalid JSON or a non-object root, the operation fails rather than silently resetting or reinterpreting the file.

## Application Isolation

Every operation is scoped to the site and explicit persistence scope in its URL.

Clients cannot override the mapping through query parameters, request bodies, headers, Section names, or arbitrary paths.

This is namespace and filesystem scoping, not authentication or authorization. Applications served by one Mini Server instance share an HTTP origin and are considered part of the same trusted local environment.

Deliberately written code may address another valid site's API namespace or select either scope. The private scope remains user-specific storage rather than a hostile-application security boundary.

## Successful Responses

JSON-returning operations use 200 OK with:

    Content-Type: application/json; charset=utf-8

Modifying operations use 204 No Content with an empty body.

## Error Responses

API errors use:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

The API uses:

- 400 Bad Request for malformed requests, missing/invalid scope, invalid Section names, invalid JSON, invalid payloads, or missing required input
- 404 Not Found for missing Sections, unknown sites, or unknown operations
- 405 Method Not Allowed for an unsupported method on a known operation
- 415 Unsupported Media Type for an unacceptable write content type
- 500 Internal Server Error for server-side, persistence, permission, atomic-write, or write-lock failures

A failed operation never returns success. Browser-facing errors must not expose unnecessary absolute filesystem paths.

## Acceptance Criteria

REQ-003 is fulfilled when:

- One central Java implementation handles persistence for every valid site.
- Every operation explicitly selects private or shared scope.
- No default or unscoped persistence API exists.
- All HTTP routes use /<site>/api/<scope>/<operation>.
- Shared scope maps to <installation-root>\www\<site>\data\data.json.
- Private scope maps to %APPDATA%\MiniServerData\<site>\data\data.json.
- Clients cannot provide arbitrary persistence paths.
- Unknown sites do not create application directories.
- Shared persistence is protected from static serving.
- Private persistence remains outside the web root.
- Every persistence file uses a JSON object root with top-level Sections.
- Section values preserve all JSON-compatible value types.
- Section names are validated and never treated as paths.
- read returns one Section value, including stored null.
- Missing Sections return 404 Not Found.
- readAll returns the complete root object and returns {} for a missing file.
- write accepts a non-empty object and creates or replaces supplied Sections while preserving others.
- remove removes one Section without changing others.
- clear resets the selected store to {} and is idempotent.
- Successful modifications return 204 No Content.
- Each write operation uses a bounded, short-lived exclusive file lock for its target.
- Writes are atomic and readers never observe partial JSON.
- Reads do not acquire a separate read lock.
- Write-lock failure fails with the external message Write failed.
- Invalid stored JSON or a non-object root is not silently overwritten.
- API errors use the standard JSON structure and appropriate HTTP status.
- Private storage is not described as an authentication or authorization boundary.

## Constraints

The server-side implementation remains generic, file-based, lightweight, and Java 8 compatible.

No database, migration layer, legacy unscoped API, transaction service, or persistent operational logging architecture is required.

The contract must remain consistent across the Java server, mini-api.js, examples, templates, tests, and documentation.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-005 — No Database
- D-006 — Generic Server-Side Data Handling
- D-008 — Application Scope Is Derived from the URL
- D-009 — Shared Central API Implementation
- D-014 — Not Intended for Public Internet Use
- D-015 — Persistence Data Is Not Served as Static Content
- D-016 — Application Separation Is Namespace Isolation, Not Authentication
- D-021 — Explicit Shared and Private Persistence Scopes
- D-022 — Explicitly Scoped Persistence API Contract
- D-023 — Concurrency-Safe Persistence Writes

## Related Architecture

See docs/ARCHITECTURE.md, especially:

- Storage and Runtime Boundaries
- Central Persistence API
- API Operations
- Persistence Data Model
- Persistence Concurrency
- Application and Scope Isolation

## Related Tasks

See tasks/ACTIVE.md, especially T-004, T-005, T-006, and T-013.

## Target Release

v1.0
