# REQ-004 — JavaScript Client API

## Requirement ID

REQ-004

## Title

JavaScript Client API

## Status

Released

## Purpose

Mini Server must provide a shared browser-side JavaScript library that exposes the central JSON persistence API through native JavaScript values.

Every operation must explicitly select private or shared persistence.

## Shared Library

The dependency-free library is:

    www/_shared/mini-api.js

Applications include it with:

    <script src="/_shared/mini-api.js"></script>

It exposes the global object:

    MiniApi

## Public API and Scope Selection

The v1 browser API is operation-first:

    MiniApi.read(section)
    MiniApi.readAll()
    MiniApi.write(data)
    MiniApi.remove(section)
    MiniApi.clear()

The caller must then select exactly one scope:

    .private()
    .shared()

Complete valid calls include:

    MiniApi.read("settings").private()
    MiniApi.read("settings").shared()

    MiniApi.readAll().private()
    MiniApi.readAll().shared()

    MiniApi.write(data).private()
    MiniApi.write(data).shared()

    MiniApi.remove("settings").private()
    MiniApi.remove("settings").shared()

    MiniApi.clear().private()
    MiniApi.clear().shared()

The chain order is mandatory. Scope-first calls such as MiniApi.private().read(...) and MiniApi.shared().read(...) are invalid.

There is no implicit scope. Calls without a terminal .private() or .shared() do not execute a persistence operation.

The obsolete names readSection and removeSection are not part of the v1 public API.

## Asynchronous Contract

The terminal .private() or .shared() call executes the selected operation and returns a native JavaScript Promise.

The Promise resolves only after the server operation succeeds. It rejects for client validation failures, network failures, non-successful HTTP statuses, serialization failures, or invalid expected JSON responses.

Application code may use normal Promise handling or async/await where supported.

## Site Detection

MiniApi derives the current application site from the browser URL.

For a page at:

    http://127.0.0.1:<port>/example/index.html

the site is example.

Together with the explicit scope selector, MiniApi targets:

    /example/api/<scope>/<operation>

Applications do not configure a site name or provide filesystem paths.

Automatic site detection and scope mapping are convenience and filesystem-scoping mechanisms, not authentication or authorization boundaries.

## read

    MiniApi.read(section).private()
    MiniApi.read(section).shared()

send:

    GET /<site>/api/<scope>/read?section=<encoded-name>

MiniApi validates and URL-encodes the Section name.

The Promise resolves with the stored native JavaScript value. A stored JSON null resolves successfully to null. A missing Section rejects with the server error.

## readAll

    MiniApi.readAll().private()
    MiniApi.readAll().shared()

send:

    GET /<site>/api/<scope>/readAll

The Promise resolves with the complete root object. A missing persistence file resolves to an empty object.

## write

    MiniApi.write(data).private()
    MiniApi.write(data).shared()

send:

    POST /<site>/api/<scope>/write

with an appropriate JSON content type.

data must be a non-null, non-array native JavaScript object with at least one own top-level property. Top-level property names are Sections and must satisfy the Section-name rules. Values must be representable as JSON.

MiniApi serializes the object internally. Callers do not call JSON.stringify().

A successful 204 No Content response resolves the Promise with undefined.

## remove

    MiniApi.remove(section).private()
    MiniApi.remove(section).shared()

send:

    DELETE /<site>/api/<scope>/remove?section=<encoded-name>

MiniApi validates and URL-encodes the Section name. Success resolves with undefined; a missing Section rejects.

## clear

    MiniApi.clear().private()
    MiniApi.clear().shared()

send:

    DELETE /<site>/api/<scope>/clear

Success resolves with undefined. Clearing an empty or not-yet-created selected store is successful.

## JSON Handling

MiniApi handles serialization and deserialization internally.

read and readAll parse successful JSON responses into native JavaScript values. write serializes the supplied object. write, remove, and clear do not parse a body after 204 No Content.

Application developers do not normally call JSON.stringify() or JSON.parse() for MiniApi operations.

## Client Validation

Section names supplied to read or remove, and top-level property names supplied to write:

- Contain between 1 and 128 characters
- Are not empty
- Have no leading or trailing whitespace
- Contain no control characters

Unicode and normal spaces, hyphens, underscores, and periods are permitted inside a Section name.

Validation remains generic and does not interpret application-specific business rules.

## Error Handling

MiniApi never converts a failed operation into success.

When the server returns:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

the rejected error makes the HTTP status, server error code, and human-readable message available where present.

The exact internal JavaScript error type may be selected during implementation. Browser errors must not expose unnecessary filesystem details.

Persistence write-lock failures remain normal rejected API operations with the server's simple Write failed error.

## HTTP Contract

MiniApi uses exactly:

    GET    /<site>/api/<scope>/read?section=<name>
    GET    /<site>/api/<scope>/readAll
    POST   /<site>/api/<scope>/write
    DELETE /<site>/api/<scope>/remove?section=<name>
    DELETE /<site>/api/<scope>/clear

It never uses unscoped routes or /<site>/<scope>/api/<operation>.

JSON-returning operations expect 200 OK. Modifying operations expect 204 No Content. Any non-successful status rejects.

## Application Independence

One shared mini-api.js works for every valid first-level application.

It contains no hard-coded application name or business logic and does not expose arbitrary filesystem paths. Deliberately written application code can still address another valid site's API or choose either scope; MiniApi is not a security boundary.

## Acceptance Criteria

REQ-004 is fulfilled when:

- www/_shared/mini-api.js exposes global MiniApi.
- Public operation names are read, readAll, write, remove, and clear.
- Every operation requires a terminal .private() or .shared() selector.
- No default persistence scope exists.
- The operation comes before the scope selector.
- Scope-first syntax is not supported.
- readSection and removeSection are not public v1 methods.
- The terminal scope selector returns a Promise.
- MiniApi derives the current site from the browser URL.
- MiniApi never accepts a persistence filesystem path.
- All generated routes use /<site>/api/<scope>/<operation>.
- read returns the native stored value, including null.
- readAll returns the complete root object.
- write accepts and serializes a non-empty native object.
- remove deletes one Section.
- clear clears the selected store.
- Section names are validated and URL-encoded.
- Successful modifying operations resolve with undefined after 204 No Content.
- JSON serialization and deserialization are internal.
- Validation, network, serialization, response, and HTTP failures reject.
- Server status, code, and message remain accessible when available.
- One library works for all hosted applications.
- The library remains dependency-free and application-independent.

## Constraints

The client library remains small, dependency-free, browser-native where practical, and compatible with the intended Microsoft Edge environment.

No legacy aliases, unscoped calls, or default scope are required.

## Related Decisions

- D-006 — Generic Server-Side Data Handling
- D-008 — Application Site and Persistence Scope Are Derived from the URL
- D-009 — Shared Central API Implementation
- D-010 — Shared JavaScript API Library
- D-011 — Native JavaScript Objects and Arrays
- D-016 — Application Separation Is Namespace Isolation, Not Authentication
- D-021 — Explicit Shared and Private Persistence Scopes
- D-022 — Explicitly Scoped Persistence API Contract

## Related Architecture

See docs/ARCHITECTURE.md, especially:

- Central Persistence API
- Shared JavaScript Client Library
- Application and Scope Isolation

## Related Tasks

See tasks/ACTIVE.md, especially T-007 through T-009 and T-013.

## Target Release

v1.0
