# Architecture

## Overview

Mini Server is a lightweight web server intended for local or internal use.

The server hosts multiple independent small web applications below a shared `www` root directory.

Each web application is represented by its own first-level subdirectory below `www`.

The server provides:

- Static file serving
- A central JSON persistence API
- A shared JavaScript client library
- Per-application JSON data storage
- Isolation between individual web applications

The server itself does not interpret application-specific data.

## Runtime Structure

The basic runtime structure is:

    mini-server/
    ├── .runtime/
    │   ├── instance.lock
    │   └── instance.json
    ├── server/
    │   └── ...
    ├── miniweb-template.zip
    └── www/
        ├── _shared/
        │   └── mini-api.js
        ├── example/
        │   ├── index.html
        │   ├── assets/
        │   └── data/
        │       └── data.json
        └── another-app/
            ├── index.html
            ├── assets/
            └── data/
                └── data.json

The `.runtime/` directory is created and managed as local runtime state. It is outside the web root and must never be exposed through normal static file serving.

The runtime files represent the current installation instance state. `instance.lock` is used for exclusive instance ownership, while `instance.json` contains runtime information such as the currently assigned TCP port.

The Java source and build layout is defined by D-019. The simplified `server/` representation above describes the runtime distribution concept and does not represent the Maven source tree.

## Web Application Model

Each first-level directory below `www` represents an independent web application.

Examples:

```text
www/example/
www/dashboard/
www/notes/
www/my-app/
```

A web application owns its own:

- Static HTML files
- CSS
- JavaScript
- Assets
- JSON persistence file

The standard persistence location for an application is:

```text
www/<site>/data/data.json
```

For example:

```text
www/example/data/data.json
www/dashboard/data/data.json
```

## Static File Serving

Static resources are served from the `www` directory.

A request such as:

    /example/index.html

maps to:

    www/example/index.html

Normal application resources such as HTML, CSS, JavaScript, images, text files, and static JSON resources may be served from locations inside the application's directory.

The server must prevent path traversal outside the configured `www` root.

### Reserved Persistence Directory

The directory:

    www/<site>/data/

is reserved for Mini Server persistence.

It is not part of the application's publicly served static content.

A direct request such as:

    /example/data/data.json

must not return:

    www/example/data/data.json

Files below the reserved persistence directory must not be accessible through the normal static file handler.

Persistent application data is accessed exclusively through the site's JSON API.

This restriction applies specifically to the `data` directory directly below an application directory. It does not prohibit an application from serving ordinary static JSON files from other locations such as:

    www/example/assets/config.json

The persistence directory remains below the application directory so that an application and its data retain a predictable, portable structure, while the API remains the controlled access path for persistent data.

## Central API

The API is implemented once in the Java server.

Individual web applications do not contain their own server-side API implementations.

The site is derived from the first path component of the request.

For example:

```text
/example/api/read?section=settings
```

is interpreted as:

```text
site = example
operation = read
section = settings
```

The server automatically maps the request to:

```text
www/example/data/data.json
```

Likewise:

```text
/dashboard/api/read?section=settings
```

maps to:

```text
www/dashboard/data/data.json
```

The API implementation is therefore shared globally while each application receives a logically separate API namespace through its URL path.

## API Operations

The central persistence API uses the following HTTP contract:

    GET    /<site>/api/read?section=<name>
    GET    /<site>/api/readAll
    POST   /<site>/api/write
    DELETE /<site>/api/remove?section=<name>
    DELETE /<site>/api/clear

The HTTP method is part of the API contract.

### read

    GET /<site>/api/read?section=<name>

Reads one named section from the site's persistence data.

The stored JSON value of the section is returned directly with:

    200 OK

A missing section returns:

    404 Not Found

A stored JSON `null` value is valid and is therefore distinguishable from a missing section by the HTTP status.

### readAll

    GET /<site>/api/readAll

Returns the site's complete persistence root object with:

    200 OK

If the persistence file does not yet exist, the logical persistence state is empty and the operation returns:

    {}

### write

    POST /<site>/api/write

Creates or replaces one or more sections.

The request body is a non-empty JSON object whose top-level properties are the sections to create or replace.

Sections that are not included in the request remain unchanged.

A successful write returns:

    204 No Content

### remove

    DELETE /<site>/api/remove?section=<name>

Removes exactly one named section.

A successful removal returns:

    204 No Content

A missing section returns:

    404 Not Found

### clear

    DELETE /<site>/api/clear

Resets the site's persistence state to:

    {}

A successful clear returns:

    204 No Content

Clearing an already empty or not-yet-created persistence store is successful and is therefore idempotent.

### HTTP Responses

Operations returning JSON data use:

    Content-Type: application/json; charset=utf-8

API errors use a consistent JSON structure:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

The API uses the following error categories:

    400 Bad Request
    404 Not Found
    405 Method Not Allowed
    415 Unsupported Media Type
    500 Internal Server Error

Detailed request validation and error behavior are defined by D-017 and the active API requirements.

## Data Model

Each application's persistence file uses a JSON object as its root structure.

The standard persistence location is:

    www/<site>/data/data.json

The top-level properties of the root object are named sections.

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

The root value must always be a JSON object.

An array, string, number, boolean, or null value is not a valid persistence root.

### Sections

Each top-level property represents one section.

A section value may contain any valid JSON-compatible value, including:

- Objects
- Arrays
- Strings
- Numbers
- Booleans
- Null

The server does not interpret the application-specific meaning or internal structure of section values.

A stored JSON null value is valid and is distinct from a missing section.

### Section Names

Section names are logical JSON property names.

A valid section name:

- contains between 1 and 128 characters;
- is not empty;
- has no leading or trailing whitespace;
- contains no control characters.

Unicode characters and normal characters such as spaces, hyphens, underscores, and periods may appear inside a section name.

Section names must never be interpreted as filesystem paths.

### Missing Persistence File

A missing persistence file represents a persistence store that has not yet been created.

For read-all and clear operations, its logical state is equivalent to:

    {}

Reading or removing a specific section from a missing persistence file behaves as though that section does not exist.

A valid write operation may create the required `data` directory and `data.json` file inside an already existing application directory.

### Invalid Persistence Data

If an existing `data.json` file contains invalid JSON or does not contain a JSON object at its root, Mini Server must treat the persistence data as invalid.

The server must not silently reinterpret or destructively reset invalid persistence data.

The operation must fail and report an appropriate API error.

The detailed persistence contract is defined by D-017 and the active JSON API requirement.

## Application Isolation

Mini Server separates applications by URL namespace and persistence location.

Each application uses its own persistence file:

    www/<site>/data/data.json

and its own API namespace:

    /<site>/api/

For example:

    /example/api/read?section=settings

is mapped to:

    www/example/data/data.json

while:

    /dashboard/api/read?section=settings

is mapped to:

    www/dashboard/data/data.json

The server derives the target site from the request path.

The client must not be allowed to provide an arbitrary filesystem path or persistence location.

This prevents filesystem path manipulation and accidental mixing of persistence data between applications.

### Security Scope

Application separation is not an authentication or authorization boundary.

Applications hosted by one Mini Server instance normally share the same HTTP origin:

    http://127.0.0.1:<port>

Different path prefixes therefore do not make the applications mutually isolated security principals.

For example, JavaScript running from:

    /example/

could deliberately send a request to:

    /dashboard/api/readAll

The server would process that request within the `dashboard` namespace because the requested URL explicitly targets that namespace.

Mini Server v1.0 does not provide authentication or authorization between hosted applications.

Applications hosted together by one Mini Server instance must therefore be treated as part of the same trusted local environment.

The isolation model guarantees:

- predictable per-application persistence locations;
- URL-derived site scoping;
- prevention of arbitrary client-supplied persistence filesystem paths;
- separation of normal application data during intended MiniApi usage.

It does not guarantee protection against deliberately written code that explicitly calls another application's API namespace.

## Shared JavaScript Client Library

A shared browser-side JavaScript library named:

```text
mini-api.js
```

is maintained centrally.

It is intended to be served from:

```text
www/_shared/mini-api.js
```

Applications can include the shared library using a URL such as:

```html
<script src="/_shared/mini-api.js"></script>
```

The library provides the browser-facing API:

    MiniApi.readSection(section)
    MiniApi.readAll()
    MiniApi.write(data)
    MiniApi.removeSection(section)
    MiniApi.clear()

These names define the public MiniApi interface for the initial release.

Application code should normally use this shared interface rather than constructing persistence API requests directly.

The JavaScript library works with native JavaScript objects and arrays.

Application developers should not need to call:

    JSON.stringify()
    JSON.parse()

for normal API use.

Serialization and deserialization are handled internally by `mini-api.js`.

The library also performs basic validation before sending data to the server.

## Site Detection

The shared JavaScript library should determine the current site from the browser URL.

For example, when running from:

```text
http://127.0.0.1:<port>/example/index.html
```

the library determines:

```text
site = example
```

and sends API requests below:

```text
/example/api/
```

The site should not normally need to be configured manually by application code.

## Example Application and Template Package

The distributed web root contains a maintained example application:

    www/example/

The example application serves as the working reference implementation and demonstrates the public MiniApi interface.

It may evolve together with Mini Server as functionality is added or refined.

A reusable starter template is distributed separately as:

    miniweb-template.zip

The template archive is stored outside the `www` web root.

A permanent:

    www/template/

directory is not required in the normal Mini Server distribution.

Developers create a new application by extracting or copying the template into a new first-level directory below `www/`.

For example:

    www/my-app/

The extracted application then receives its own persistence location:

    www/my-app/data/data.json

and uses the shared browser-side API library from:

    www/_shared/mini-api.js

The template remains intentionally small and application-neutral.

It demonstrates the public MiniApi operations and includes a minimal visible:

    Hello Mini Webserver

example.

The template is intended to remain a clean starting point, while the `example` application is the maintained and potentially evolving demonstration.

## Network Boundary

Mini Server binds its HTTP server exclusively to:

    127.0.0.1

A newly started server requests TCP port:

    0

The operating system selects an available local port.

Mini Server does not scan for free ports and does not use a permanently configured server port.

The server is intended as a local per-user service and is not designed as a public internet-facing web server.

## Startup and Browser Launch

Only one Mini Server server process may run for one installation at a time.

Independent Mini Server installations may run simultaneously.

### Instance State

Each installation maintains runtime state outside the web root.

The intended location is:

    <installation-root>/.runtime/

For example:

    <installation-root>/.runtime/instance.lock
    <installation-root>/.runtime/instance.json

The exclusive instance lock determines whether the installation already has a running Mini Server process.

The runtime state contains at least the TCP port assigned to the active server after successful startup.

Runtime state must never be stored below `www/` or served as normal web content.

A state file by itself is not proof that a server is still running.

### First Start

When no server instance currently owns the installation lock:

1. Mini Server acquires the exclusive installation lock.
2. Stale runtime state is invalidated.
3. The HTTP server binds to `127.0.0.1` and requests port `0`.
4. The operating system assigns an available local TCP port.
5. Mini Server obtains the actual assigned port.
6. The current port is published in the runtime state.
7. The server is considered ready.
8. Microsoft Edge is opened with the assigned port and configured application start target.
9. The Java process remains running as the active Mini Server instance.

For example:

    http://127.0.0.1:51847/example/

Edge must not be opened using a guessed or predetermined port.

### Repeated Start

If the same installation is started again while its Mini Server process is already running, the second process does not start another HTTP server.

Instead it:

1. Detects that the installation lock is already owned.
2. Reads the runtime state of the active instance.
3. Obtains the existing server port.
4. Opens Microsoft Edge using the existing Mini Server URL.
5. Terminates.

A repeated start therefore reuses the active server instance instead of creating another process that could compete for the same persistence files.

### Startup Race

A repeated start may occur after the first process has acquired the installation lock but before it has published valid runtime state.

During this startup phase, the second process must not start another server.

It may wait for valid runtime state for a short bounded period.

If the active lock remains owned but valid state cannot be obtained, the repeated start fails with a diagnostic message instead of creating a competing server instance.

### Stale Runtime State

Runtime state may remain after an abnormal process termination.

If no process owns the installation lock, a new Mini Server process may start normally.

After acquiring the lock, the new process invalidates stale state and publishes its own state only after successful server startup.

An old stored port is never reused solely because it remains in a runtime state file.

### Server Lifetime

The Mini Server Java process runs independently of Microsoft Edge.

Closing a Mini Server tab, an Edge window, or all Edge windows does not intentionally stop the server.

The server continues running until its Java process terminates, for example because of:

- User logoff
- Operating-system shutdown
- Explicit process termination
- Fatal process failure

Mini Server v1.0 does not provide a browser-accessible HTTP shutdown endpoint.

### Persistence Concurrency

The per-installation single-instance rule prevents separate Mini Server processes from concurrently modifying the same installation's persistence files.

Concurrency between requests handled inside the active server process must still be managed by the persistence implementation itself.

Detailed startup and lifetime behavior is defined by D-018 and REQ-006.

## Architectural Principles

The implementation should preserve the following principles:

- Keep the server small and understandable.
- Keep application-specific logic out of the server.
- Centralize shared API behavior.
- Centralize the browser-side API library.
- Keep each application's persistent data isolated.
- Avoid unnecessary external frameworks and dependencies.
- Do not expose arbitrary filesystem access through the API.
- Prefer clear path-based scoping over client-supplied storage locations.
- Keep the architecture suitable for multiple independent applications below one `www` root.

## Related Documents

See:

- `docs/DECISIONS.md` for approved technical decisions and constraints
- `requirements/` for functional and non-functional requirements
- `docs/PROJECT_NOTES.md` for working knowledge and observations
- `docs/DEBUGGING.md` for known problems and verified fixes