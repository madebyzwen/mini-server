# REQ-003 — JSON Persistence API

## Requirement ID

REQ-003

## Title

JSON Persistence API

## Status

Active

## Purpose

Mini Server must provide a central JSON-based persistence API that can be used by all hosted web applications.

The API must remain generic and must not interpret application-specific business data.

## Description

Each first-level web application below `www/` has its own JSON persistence file.

The standard location is:

    www/<site>/data/data.json

The API is implemented once in the Java server.

Individual applications must not require separate server-side API implementations.

The server exposes the same persistence operations within each valid application's URL namespace.

## Site Scope

The site is determined from the request URL.

For example:

    GET /example/api/read?section=settings

operates on:

    www/example/data/data.json

Likewise:

    GET /dashboard/api/read?section=settings

operates on:

    www/dashboard/data/data.json

The server must derive and validate the persistence location itself.

The client must not provide an arbitrary filesystem path or persistence location.

An API namespace is valid only when the corresponding first-level application directory exists below `www/`.

For example:

    /example/api/

is a valid namespace when:

    www/example/

exists.

An API request must not create a new application directory merely because an unknown site name was supplied in the URL.

Reserved Mini Server areas such as:

    www/_shared/

must not be treated as normal application persistence namespaces.

## Persistence Access Boundary

The directory:

    www/<site>/data/

is reserved for Mini Server persistence.

Persistent application data stored below this directory must not be served through the normal static file handler.

In particular:

    /example/data/data.json

must not provide direct HTTP access to:

    www/example/data/data.json

Persistent data must be accessed through the site's JSON API.

Normal static JSON resources stored elsewhere in an application's directory may still be served as static content when permitted by the static file serving requirements.

## Persistence Data Model

Each site's `data.json` file must contain a JSON object at its root.

The top-level properties of this object are named sections.

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

The root of `data.json` must not be:

- An array
- A string
- A number
- A boolean
- Null

Each individual section value may contain any valid JSON-compatible value, including:

- Objects
- Arrays
- Strings
- Numbers
- Booleans
- Null

The server must preserve valid JSON section values without interpreting their application-specific meaning.

## Section Names

Section names are logical JSON property names.

They must never be interpreted as filesystem paths.

A valid section name:

- Must contain between 1 and 128 characters
- Must not be empty
- Must not contain leading whitespace
- Must not contain trailing whitespace
- Must not contain control characters

Unicode characters and normal characters such as spaces, hyphens, underscores, and periods may be used inside a section name.

Invalid section names must be rejected.

## API Operations

The API must provide the following operations:

    GET    /<site>/api/read?section=<name>
    GET    /<site>/api/readAll
    POST   /<site>/api/write
    DELETE /<site>/api/remove?section=<name>
    DELETE /<site>/api/clear

The HTTP method is part of the API contract.

Calling a known operation with an unsupported method must return:

    405 Method Not Allowed

## Read One Section

The operation:

    GET /<site>/api/read?section=<name>

reads one named section from the current site's persistence data.

If the section exists, its stored JSON value must be returned directly.

For example, if the persistence file contains:

    {
        "settings": {
            "theme": "dark"
        }
    }

then:

    GET /example/api/read?section=settings

returns:

    {
        "theme": "dark"
    }

A stored JSON null value is valid and must be returned successfully as:

    null

If the requested section does not exist, the API must return:

    404 Not Found

with the standard JSON error response.

A missing persistence file is equivalent to the requested section not existing for this operation.

## Read All Sections

The operation:

    GET /<site>/api/readAll

returns the complete root JSON object for the current site.

If the stored persistence data is:

    {
        "start": "Hello Mini Webserver",
        "settings": {
            "theme": "dark"
        }
    }

the complete object is returned directly.

If the site's persistence file does not yet exist, `readAll` must behave as though the site contains an empty persistence store and return:

    {}

## Write Sections

The operation:

    POST /<site>/api/write

creates or replaces one or more sections in the current site's persistence data.

The request body must contain a non-empty JSON object.

The top-level properties of the request object are the sections to create or replace.

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

For each supplied top-level property:

- A missing section is created
- An existing section is replaced
- Sections not included in the request remain unchanged

A write request containing zero sections is invalid.

A write request whose root value is not a JSON object is invalid.

The request must use an acceptable JSON request content type.

A successful write must return:

    204 No Content

with an empty response body.

## Data File Creation

If the site's persistence file does not yet exist when a valid write operation requires it, the server may create:

    www/<site>/data/data.json

The server may also create the site's `data` directory when required.

Any created directory or file must remain inside the already existing valid application directory.

The API must never create an arbitrary application directory from an unknown site namespace.

## Remove One Section

The operation:

    DELETE /<site>/api/remove?section=<name>

removes exactly one named section from the current site's persistence data.

Other sections must remain unchanged.

A successful removal must return:

    204 No Content

with an empty response body.

If the requested section does not exist, the operation must return:

    404 Not Found

with the standard JSON error response.

A missing persistence file is equivalent to the requested section not existing.

## Clear All Sections

The operation:

    DELETE /<site>/api/clear

resets the current site's persistence state to the logical equivalent of:

    {}

A successful clear must return:

    204 No Content

with an empty response body.

Clearing an already empty persistence store is successful.

Clearing a site whose persistence file does not yet exist is also successful.

The operation is therefore idempotent.

The operation must affect only the persistence file derived from the site namespace addressed by the request.

## Data Integrity

Successful write, remove, and clear operations must leave valid JSON persistence data.

The server must not intentionally leave a partially written or syntactically invalid JSON file after reporting success.

If a persistence modification fails, the server must return an error instead of reporting success.

The implementation should minimize the risk of corrupting an existing valid persistence file during replacement.

If an existing persistence file contains invalid JSON or does not contain a JSON object at its root, the server must report an error rather than silently reinterpret or destructively reset the file.

## Application Isolation

Every API operation must be scoped to the site namespace identified by the request URL.

For example:

    /example/api/

maps to:

    www/example/data/data.json

while:

    /dashboard/api/

maps to:

    www/dashboard/data/data.json

The client must not be able to override this mapping using:

- Query parameters
- Request bodies
- Headers
- Section names
- Arbitrary filesystem paths
- Other client-controlled persistence locations

This is namespace and filesystem scoping.

It is not authentication or authorization between hosted applications.

Applications hosted by one Mini Server instance normally share the same HTTP origin.

A deliberately written application may explicitly request another valid application's API namespace.

For example, code loaded from:

    /example/

may deliberately request:

    /dashboard/api/readAll

Such a request is processed within the `dashboard` namespace because that namespace was explicitly addressed in the URL.

Mini Server v1.0 does not authenticate hosted applications against each other.

Hosted applications are therefore considered part of the same trusted local environment.

## Successful HTTP Responses

Operations returning JSON data must use:

    200 OK

with:

    Content-Type: application/json; charset=utf-8

This applies to:

    GET /<site>/api/read
    GET /<site>/api/readAll

Successful modifying operations must use:

    204 No Content

with an empty response body.

This applies to:

    POST   /<site>/api/write
    DELETE /<site>/api/remove
    DELETE /<site>/api/clear

## Error Response Format

API errors must use a consistent JSON structure.

Example:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

The `code` value must be a stable machine-readable error identifier.

The `message` value must be a concise human-readable description.

Browser-facing errors must not expose unnecessary absolute filesystem paths or sensitive internal implementation details.

## HTTP Error Categories

The API must use:

    400 Bad Request

for malformed requests, invalid section names, invalid JSON, invalid write payloads, or missing required request information.

It must use:

    404 Not Found

for missing sections, unknown application sites, or unknown API operations.

It must use:

    405 Method Not Allowed

when a known API operation is called using an unsupported HTTP method.

It must use:

    415 Unsupported Media Type

when a write request does not provide an acceptable JSON request content type.

It must use:

    500 Internal Server Error

for unexpected server-side or persistence failures that prevent the requested operation from completing.

A failed operation must never return a successful HTTP status.

## Acceptance Criteria

REQ-003 is fulfilled when all of the following are true:

- One central Java implementation handles the persistence API for all valid application sites.
- Each site's API is available below `/<site>/api/`.
- The site namespace is derived from the request path.
- Unknown site namespaces do not cause new application directories to be created.
- Reserved Mini Server directories are not treated as normal application persistence namespaces.
- Each site uses its own `www/<site>/data/data.json` persistence file.
- The persistence directory `www/<site>/data/` is not served by the normal static file handler.
- Direct requests for `/<site>/data/data.json` do not expose persistence data.
- `data.json` always uses a JSON object as its root value.
- Top-level properties in `data.json` represent sections.
- Section values may contain any valid JSON-compatible value.
- Section names are validated and are never interpreted as filesystem paths.
- `GET /<site>/api/read` retrieves one section value directly.
- A stored JSON null section value is returned successfully.
- Reading a missing section returns `404 Not Found`.
- `GET /<site>/api/readAll` returns the complete root object.
- `readAll` returns `{}` when the persistence file does not yet exist.
- `POST /<site>/api/write` accepts a non-empty JSON object.
- `write` can create one or more sections.
- `write` can replace one or more existing sections.
- Sections not included in a write request remain unchanged.
- A successful write returns `204 No Content`.
- `DELETE /<site>/api/remove` removes exactly one section.
- Removing a missing section returns `404 Not Found`.
- A successful removal returns `204 No Content`.
- `DELETE /<site>/api/clear` resets the site persistence state.
- Clearing an empty or not-yet-created persistence store succeeds.
- A successful clear returns `204 No Content`.
- Known operations reject unsupported HTTP methods with `405 Method Not Allowed`.
- Invalid JSON and malformed requests do not result in successful responses.
- Write requests with an unacceptable JSON content type return `415 Unsupported Media Type`.
- API errors use the defined JSON error structure.
- API responses do not expose unnecessary absolute filesystem paths.
- Clients cannot supply arbitrary persistence filesystem locations.
- Successful modifying operations leave valid JSON persistence data.
- Server-side failures are reported as errors rather than success.

## Constraints

The server-side implementation must remain generic.

Application-specific business rules must not be introduced into the central persistence API.

The implementation must remain compatible with the project's approved Java runtime requirements.

The persistence mechanism must remain file-based and must not require a database.

The API contract defined by D-017 must remain consistent between the Java server, `mini-api.js`, example application, template package, tests, and documentation.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-005 — No Database
- D-006 — Generic Server-Side Data Handling
- D-007 — One JSON Data File per Application
- D-008 — Application Scope Is Derived from the URL
- D-009 — Shared Central API Implementation
- D-014 — Not Intended for Public Internet Use
- D-015 — Persistence Data Is Not Served as Static Content
- D-016 — Application Separation Is Namespace Isolation, Not Authentication
- D-017 — HTTP and JSON API Contract

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Central API
- API Operations
- Data Model
- Application Isolation
- Static File Serving

## Related Tasks

See:

    tasks/ACTIVE.md

Relevant implementation tasks include:

- T-004 — Implement Site Detection and Persistence Scoping
- T-005 — Implement JSON Persistence Layer
- T-006 — Implement HTTP API Endpoints
- T-013 — Add Automated Tests

## Target Release

v1.0