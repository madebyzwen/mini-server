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

The server must expose a shared set of API operations below each site's URL namespace.

The API is implemented once in the Java server.

Individual applications must not require separate server-side API implementations.

## Site Scope

The site is determined from the request URL.

Example:

/example/api/read?section=settings

must operate on:

www/example/data/data.json

Likewise:

/dashboard/api/read?section=settings

must operate on:

www/dashboard/data/data.json

The client must not provide an arbitrary filesystem path.

The server must derive and validate the storage location itself.

## Persistence Access Boundary

The directory:

www/<site>/data/

is reserved for Mini Server persistence.

Persistent application data stored below this directory must not be served through the normal static file handler.

In particular:

/example/data/data.json

must not provide direct HTTP access to:

www/example/data/data.json

The persistence file must be accessed through the site's JSON API.

This ensures that persistence access remains subject to the API's section handling, validation, error handling, and site scoping.

The restriction applies to the reserved persistence directory only.

Normal static JSON resources stored elsewhere in an application's directory may still be served as static content when permitted by the static file serving requirements.

## API Operations

The API must provide the following operations:

/<site>/api/read?section=<name>

/<site>/api/readAll

/<site>/api/write

/<site>/api/remove?section=<name>

/<site>/api/clear

## read

The `read` operation reads one named section from the site's JSON data.

Example:

/example/api/read?section=settings

If the requested section exists, its stored JSON value must be returned.

The server must not interpret the semantic meaning of the returned data.

## readAll

The `readAll` operation returns the complete stored JSON data for the current site.

Example:

/example/api/readAll

must return the contents represented by:

www/example/data/data.json

The complete persistence data is returned through the API and must not require or permit direct static access to the persistence file.

## write

The `write` operation creates or replaces one or more sections in the current site's JSON data.

Writing an existing section replaces the stored value of that section.

Writing a section that does not yet exist creates it.

The operation must support writing multiple sections in one request.

The exact transport payload format may be finalized during implementation, but it must support the logical equivalent of:

settings = {...}

and also:

settings = {...}
favorites = [...]
userData = {...}

within a single write operation.

Existing sections that are not included in the write request must remain unchanged.

## remove

The `remove` operation removes one named section from the current site's JSON data.

Example:

/example/api/remove?section=settings

must remove only the `settings` section from:

www/example/data/data.json

Other sections must remain unchanged.

## clear

The `clear` operation resets the current site's stored data to an empty state.

It must affect only the current site's JSON file.

It must not affect another site's data.

## Data Model

The persistence model is based on named sections.

Each section has a name and a JSON-compatible value.

Section values may contain:

- Strings
- Numbers
- Booleans
- Arrays
- Objects
- Null values

The server must preserve valid JSON structures without interpreting their application-specific meaning.

## Data File Creation

If the expected data file does not yet exist when a write operation requires it, the server should create the necessary data file automatically when possible.

The implementation may also create the required `data` directory when appropriate.

A missing data file must not cause data to be written outside the intended site directory.

Any automatically created persistence file remains subject to the reserved persistence directory rule and must not become directly available through static file serving.

## Data Integrity

Write operations must produce valid JSON.

The server must not intentionally leave a partially written or syntactically invalid JSON file after a successful API response.

If a write operation fails, the server must return an error instead of reporting success.

The implementation should minimize the risk of corrupting an existing valid data file during replacement.

## Application Isolation

Every API operation must remain scoped to the site identified by the request URL.

For example, requests below:

/example/api/

may only operate on:

www/example/data/data.json

They must not use arbitrary filesystem paths to read, modify, remove, clear, or otherwise access persistence files outside the site identified by the API request path.

Site scoping prevents an API request from supplying a different persistence location directly.

The exact security boundary between deliberately interacting local web applications is defined separately from this filesystem scoping rule.

## Invalid Requests

The server must reject malformed or invalid API requests appropriately.

Examples include:

- Missing required section names
- Invalid request data
- Invalid JSON
- Invalid site scope
- Attempts to escape the site's permitted data location
- Unsupported API operations

Error responses must not expose unnecessary internal filesystem information.

## HTTP Responses

Successful API operations must return an appropriate successful HTTP status.

Invalid requests must return an appropriate client error status.

Unexpected server-side failures must return an appropriate server error status.

The exact status codes and response body structure may be standardized during implementation.

The API must not report success when an operation was not completed successfully.

## Acceptance Criteria

REQ-003 is fulfilled when all of the following are true:

- One central Java implementation handles the API for all sites.
- Each site's API is available below /<site>/api/.
- The site scope is derived from the request path.
- Each site uses its own www/<site>/data/data.json file.
- The persistence directory www/<site>/data/ is not served by the normal static file handler.
- Direct requests for /<site>/data/data.json do not expose the site's persistence data.
- Persistent application data is accessed through the site's JSON API.
- `read` can retrieve one section.
- `readAll` can retrieve the complete site's stored data.
- `write` can create a new section.
- `write` can replace an existing section.
- `write` can update multiple sections in one request.
- Sections not included in a write request remain unchanged.
- `remove` can remove one section without removing unrelated sections.
- `clear` resets only the current site's stored data.
- JSON arrays and objects can be stored without application-specific interpretation by the server.
- Invalid JSON or malformed requests do not result in a successful response.
- API requests cannot supply arbitrary persistence filesystem locations.
- Clients cannot provide arbitrary filesystem storage paths.
- Successful write operations leave a valid JSON data file.
- Server-side failures are reported as errors rather than success.

## Constraints

The server-side implementation must remain generic.

Application-specific business rules must not be introduced into the central persistence API.

The implementation must remain compatible with the project's approved Java runtime requirements.

The persistence mechanism must remain file-based and must not require a database.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-005 — No Database
- D-006 — Generic Server-Side Data Handling
- D-007 — One JSON Data File per Application
- D-008 — Application Scope Is Derived from the URL
- D-009 — Shared Central API Implementation
- D-014 — Not Intended for Public Internet Use
- D-015 — Persistence Data Is Not Served as Static Content

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

No implementation tasks have been assigned yet.

## Target Release

Initial release