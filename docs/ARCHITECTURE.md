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

```text
mini-server/
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
```

The final Java source layout may differ from the simplified `server/` representation above and will be defined by the implementation structure.

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

The central API provides the following logical operations:

```text
/<site>/api/read?section=<name>
/<site>/api/readAll
/<site>/api/write
/<site>/api/remove?section=<name>
/<site>/api/clear
```

The intended behavior is:

### read

Reads one named section from the site's JSON data file.

### readAll

Reads the complete JSON data file for the site.

### write

Creates or replaces one or more sections in the site's JSON data file.

### remove

Removes one named section from the site's JSON data file.

### clear

Resets the site's JSON data to an empty state.

Detailed request and response formats are defined by the requirements and API documentation rather than this architecture document.

## Data Model

The JSON persistence layer is intentionally generic.

The server does not interpret the semantic meaning of stored application data.

At the server level, data is organized into named sections.

A section may contain normal JSON-compatible values, including:

- Strings
- Numbers
- Booleans
- Arrays
- Objects
- Null values

The web application defines the meaning and internal structure of the stored data.

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

    /template/api/readAll

The server would process that request within the `template` namespace because the requested URL explicitly targets that namespace.

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

The server is intended for trusted local or internal environments.

It is not designed as a public internet-facing web server.

The exact network binding, port allocation, launch behavior, and related runtime constraints are defined in the project decisions and requirements rather than duplicated here.

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