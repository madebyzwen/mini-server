# Architecture

## Overview

Mini Server is a lightweight web server for local or trusted internal web applications.

A Mini Server distribution may be located on a local disk, shared network drive, or group drive. Multiple users on different computers may use the same physical installation concurrently. The installation files may therefore be shared, while every running HTTP server and its coordination state remain local to one user/computer context.

The server provides:

- Static file serving from a shared `www` root
- A central, explicitly scoped JSON persistence API
- Shared and private per-application persistence
- A shared JavaScript client library
- Dynamic loopback-only HTTP startup
- Local per-user/computer instance coordination
- Shared and private start-site selection and Windows-default browser opening
- Concurrency-safe file persistence

The server remains generic and does not interpret application-specific data.

## Storage and Runtime Boundaries

Mini Server distinguishes five physical areas.

### Shared Installation

A representative distribution is:

    <installation-root>\
    ├── mini-server.jar
    ├── miniweb-template.zip
    ├── start.bat
    ├── stop.bat
    ├── config\
    │   └── start-sites.txt
    └── www\
        ├── _shared\
        │   └── mini-api.js
        ├── example\
        │   ├── index.html
        │   ├── assets\
        │   └── data\
        │       └── data.json
        └── another-app\
            └── ...

The installation may be shared by multiple users and computers. It contains the server distribution, installation-level `config`, web applications, and shared persistence data. Users of one physical installation share the approval list and canonical order in `config\start-sites.txt`, but their effective automatic-opening selections may differ through Private configuration.

Installation configuration is distinct from Private Mini Server configuration, runtime coordination state, and private application persistence. The installation must not contain authoritative machine- or process-specific runtime coordination state. In particular, an installation-level `.runtime` directory is not part of the target runtime architecture.

### Local Runtime State

Runtime coordination state is local to the current Windows user/computer context:

    %LOCALAPPDATA%\MiniServer\runtime\
    ├── startup.lock
    ├── instance.lock
    └── instance.json

This state describes only the local loopback server:

- `startup.lock` serializes concurrent local startup attempts.
- `instance.lock` is held by the active local server process for its lifetime and is authoritative for whether that instance is active.
- `instance.json` publishes the dynamically assigned local TCP port and the
  per-instance stop token.

These files are never served as web content and are never coordinated through a shared installation.

### Private Mini Server Configuration

Current-user Mini Server configuration is stored at:

    %APPDATA%\MiniServer\config\start-sites.txt

This file allows the current Windows user to select a subset of the Shared installation approval list. It is configuration for Mini Server itself, not application persistence, runtime coordination state, or a packaged installation default.

Private Mini Server configuration is distinct from:

- Local runtime coordination at `%LOCALAPPDATA%\MiniServer\runtime\`
- Private application persistence at `%APPDATA%\MiniServerData\<site>\data\data.json`
- Shared installation configuration at `<installation-root>\config\start-sites.txt`

### Shared Persistence

Shared application persistence is stored with the application:

    <installation-root>\www\<site>\data\data.json

When the installation is on a network or group drive, this file is shared by users of that installation.

### Private Persistence

Private application persistence is stored in the current Windows user's profile:

    %APPDATA%\MiniServerData\<site>\data\data.json

This structure intentionally mirrors the shared application's `data\data.json` structure.

`private` means user-profile storage rather than shared-installation storage. It is not authentication, authorization, encryption, or a security boundary between mutually hostile applications.

The Java source and build layout is defined by D-019 and remains separate from these runtime locations.

## Web Application Model

Each valid first-level directory below `www` represents a web application:

    www\example\
    www\dashboard\
    www\notes\

An application owns its static HTML, CSS, JavaScript, and assets. It can use both persistence scopes:

    shared:  <installation-root>\www\<site>\data\data.json
    private: %APPDATA%\MiniServerData\<site>\data\data.json

Both scopes use the same JSON structure and persistence operations. Every operation must select one scope explicitly; there is no default scope.

## Start-Site Configuration

Automatic application opening uses two configuration levels:

```text
Shared:  <installation-root>\config\start-sites.txt
Private: %APPDATA%\MiniServer\config\start-sites.txt
```

Shared is the upper bound: it defines which currently valid applications are centrally approved for automatic opening and their canonical opening order. Private belongs to the current Windows user and can reduce the Shared selection but cannot elevate an application outside it or reorder it.

When Private exists, the effective selection is the intersection of valid existing applications, Shared-approved applications, and Private-selected applications. When Private is missing, the complete valid Shared selection is effective. An existing empty or effectively empty Private file selects none.

Both files use the same simple UTF-8 line-oriented application-name syntax defined by REQ-010. Invalid, unsafe, reserved, and duplicate entries are ignored. Shared entries must correspond to valid first-level applications below `www/`; Private entries are matched only against the resulting valid Shared set.

Application discovery and serving remain based on valid first-level directories below `www/`. Shared approval and Private selection control browser opening only; they do not create, enable, disable, authorize, or deny access to applications.

The v1.1 distribution contains `example` as the default active entry in the Shared file. It does not package a pre-created Private file. The Java implementation has no special hard-coded startup behavior for `example`; it opens only when it is in the effective selection.

Both files are evaluated on every normal start action, including repeated starts. A change to either file therefore takes effect on the next `start.bat` invocation without restarting the active server. No watcher or active reload service is used, and neither file is automatically rewritten during normal startup.

A missing, empty, effectively empty, or unreadable Shared file means that no application is opened automatically. An unreadable Private file also opens none rather than falling back to all Shared applications. Configuration-reading failures do not invalidate an already successful server and produce concise diagnostics.

Removing an application from Shared removes it from every user's effective selection on the next start action, even if stale Private entries remain. Re-adding it selects it for users without a Private file and for users with a Private file only when their file still contains it.

## Static File Serving

Static resources are served from `www`.

For example:

    /example/index.html

maps to:

    <installation-root>\www\example\index.html

The server prevents path traversal outside the configured web root.

### Reserved Shared Persistence Directory

The directory:

    <installation-root>\www\<site>\data\

is reserved for shared Mini Server persistence and is not public static content.

A direct request such as:

    /example/data/data.json

must not expose the shared persistence file.

Private persistence is outside `www` and is likewise never available through normal static file serving.

Normal static JSON resources may still be served from non-reserved locations such as:

    www\example\assets\config.json

## Central Persistence API

The persistence API is implemented once in the Java server. Individual applications do not implement separate server-side APIs.

The request URL identifies:

- The site from the first application path component
- The mandatory persistence scope, `private` or `shared`
- The persistence operation

The canonical route shape is:

    /<site>/api/<scope>/<operation>

For example:

    /dashboard/api/private/read?section=settings

maps to:

    %APPDATA%\MiniServerData\dashboard\data\data.json

while:

    /dashboard/api/shared/read?section=settings

maps to:

    <installation-root>\www\dashboard\data\data.json

The server derives and validates these locations. Clients never provide arbitrary filesystem paths.

An API namespace is valid only when the corresponding first-level application directory exists below `www`. Reserved areas such as `www\_shared\` are not application namespaces.

## API Operations

The scoped HTTP contract is:

    GET    /<site>/api/<scope>/read?section=<name>
    GET    /<site>/api/<scope>/readAll
    POST   /<site>/api/<scope>/write
    DELETE /<site>/api/<scope>/remove?section=<name>
    DELETE /<site>/api/<scope>/clear

`<scope>` must be `private` or `shared`. Unscoped routes and the alternative layout `/<site>/<scope>/api/<operation>` are invalid.

### read

`read` returns one named Section's stored JSON value directly with `200 OK`. A stored JSON `null` is valid. A missing Section returns `404 Not Found`.

### readAll

`readAll` returns the complete root JSON object with `200 OK`. A not-yet-created persistence file behaves as an empty store and returns:

    {}

### write

`write` accepts a non-empty JSON object whose top-level properties are Sections to create or replace. Sections omitted from the request remain unchanged. Success returns `204 No Content`.

### remove

`remove` removes exactly one named Section. Other Sections remain unchanged. Success returns `204 No Content`; a missing Section returns `404 Not Found`.

### clear

`clear` resets the selected persistence file to the logical empty state `{}`. Clearing an empty or not-yet-created store succeeds with `204 No Content`.

### HTTP Responses

JSON responses use:

    Content-Type: application/json; charset=utf-8

API errors use:

    {
        "error": {
            "code": "SECTION_NOT_FOUND",
            "message": "Section not found."
        }
    }

The API uses the established `400`, `404`, `405`, `415`, and `500` error categories. A persistence write-lock failure uses the intentionally simple external message:

    Write failed

Detailed behavior is defined by D-022 and the active API requirements.

## Persistence Data Model

Every shared or private persistence file contains a JSON object at its root. Top-level properties are named Sections.

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

The empty state is `{}`. An array, string, number, boolean, or null is not a valid root value.

A Section value may be any JSON-compatible object, array, string, number, boolean, or null. The server does not interpret its application-specific meaning.

A valid Section name:

- Contains between 1 and 128 characters
- Is not empty
- Has no leading or trailing whitespace
- Contains no control characters

Section names are JSON property names and are never filesystem paths.

If a selected persistence file is missing, a valid modifying operation may create the required `data` directory and `data.json` inside the server-derived shared or private site location.

Invalid JSON or a non-object root is an error. Mini Server does not silently reinterpret or destructively reset invalid persistence data.

## Persistence Concurrency

Shared persistence can be accessed by Mini Server processes on different computers. Private persistence may also encounter concurrent access within the user's environment.

`write`, `remove`, and `clear` therefore:

1. Obtain a short-lived exclusive file lock associated with the target persistence file.
2. Use a bounded lock-acquisition timeout.
3. Perform the complete read-modify-write operation while holding the lock.
4. Replace the persistence data atomically.
5. Release the lock immediately after the write completes or fails.

Failure to obtain the lock fails the write cleanly.

Reads do not acquire a separate read lock. Atomic replacement ensures that a reader sees either the previous complete file or the new complete file, never a partial write.

Persistence-file locks are distinct from runtime locks:

- Runtime locks coordinate one local server instance.
- Persistence locks protect one target data file across local processes and computers.

The architecture does not introduce a database, transaction service, or persistent operational logging system.

## Application and Scope Isolation

Mini Server derives the target site and persistence scope from the request URL.

This provides predictable namespace and filesystem mapping and prevents arbitrary client-supplied persistence paths. It does not provide authentication or authorization between hosted applications.

Applications served by one Mini Server instance share the same HTTP origin:

    http://127.0.0.1:<port>

Deliberately written code from one application can address another valid application's API namespace. It can also choose either documented persistence scope. Applications hosted together must therefore be treated as part of the same trusted local environment.

The private scope remains user-specific storage, not a hostile-application security boundary.

## Shared JavaScript Client Library

The shared browser library is:

    www\_shared\mini-api.js

Applications include it with:

    <script src="/_shared/mini-api.js"></script>

The public v1 API is operation-first and requires a terminal scope selector:

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

The terminal `.private()` or `.shared()` executes the asynchronous operation and returns a Promise.

Scope-first calls such as `MiniApi.private().read(...)` are invalid. The obsolete names `readSection` and `removeSection` are not part of the v1 API.

`mini-api.js` derives the current site from the browser URL, adds the explicitly selected scope, and targets:

    /<site>/api/<scope>/<operation>

It handles JSON serialization/deserialization and generic validation. Applications normally work with native JavaScript values without calling `JSON.stringify()` or `JSON.parse()`.

## Example Application and Template

The distribution contains the maintained example application:

    www\example\

and the reusable template archive:

    miniweb-template.zip

The template archive is outside the web root. Developers extract it into a new first-level application directory.

The example and template demonstrate the public MiniApi interface and make their selected persistence scope explicit. The template remains application-neutral and includes the visible `Hello Mini Webserver` demonstration loaded from a selected persistence scope.

## Network Boundary

Each running server binds exclusively to:

    127.0.0.1

and requests TCP port:

    0

The operating system selects an available local port. Mini Server reads the actual assigned port from the active server socket. It does not use a fixed port, scan a port range, or bind to external interfaces.

The loopback server is local even when its installation and shared persistence are on a network drive.

## Startup and Browser Launch

Mini Server permits one running server instance per local user/computer context. Different computers do not block each other merely because they use the same installation.

`start.bat` launches the existing `MiniServer` entry point through `javaw.exe`
using quoted absolute paths based on the batch-file directory. It exits without
waiting for startup or browser confirmation. The detached process continues only
when it owns the active server; a repeated-start process opens the currently
effective URLs and exits naturally.

### First Local Start

A startup attempt:

1. Performs normal local locking and state startup according to D-020.
2. Binds the HTTP server to `127.0.0.1` using port `0`.
3. Reads the actual operating-system-assigned port.
4. Confirms that the HTTP server is ready.
5. Publishes the port and an unpredictable per-instance stop token in local `instance.json`.
6. Releases `startup.lock` while retaining `instance.lock` for the server lifetime.
7. Reads and evaluates Shared `config\start-sites.txt`.
8. Reads and evaluates Private `%APPDATA%\MiniServer\config\start-sites.txt` if it exists.
9. Computes the effective Private subset of the valid Shared selection.
10. Preserves Shared order.
11. Constructs application URLs using `127.0.0.1` and the actual active port.
12. Asks Windows to open each URL in effective order using its configured HTTP URL handler.
13. Continues the server lifetime independently of browser lifetime.

### Repeated Local Start

If `instance.lock` is owned by the active local server, the repeated start:

1. Does not start another HTTP server or request another port.
2. Obtains valid local `instance.json` state and reuses the active local port.
3. Rereads and evaluates current Shared configuration.
4. Rereads and evaluates current Private configuration if it exists.
5. Recomputes the effective Private subset of the valid Shared selection.
6. Preserves Shared order.
7. Constructs application URLs using the existing active port.
8. Asks Windows to open each URL using its configured HTTP URL handler.
9. Exits normally.

If an active lock exists but valid state cannot be obtained within the bounded startup procedure, the repeated start fails clearly instead of starting a competing process.

A state file alone is never proof that an instance is active. Stale state does not prevent a later start when no process owns `instance.lock`.

### Failure Boundaries

The startup/browser flow has three ordered boundaries:

```text
server startup
    -> start-site evaluation
    -> browser opening
```

A start-site evaluation or browser-opening failure must not retroactively invalidate an already successfully running server.

- Missing, empty, effectively empty, or unreadable Shared configuration leaves the server running and opens no application automatically; Private cannot bypass this boundary.
- Missing Private configuration uses the complete valid Shared selection.
- Empty or effectively empty Private configuration leaves the server running and opens no application automatically.
- Unreadable Private configuration leaves the server running and opens none rather than falling back to the complete Shared selection.
- Unreadable configuration produces a concise diagnostic without altering runtime coordination or persistence.
- Failure to open one URL leaves the server running and does not prevent attempts for remaining valid URLs.
- If no browser is available, runtime state remains valid and authenticated `stop.bat` shutdown continues to work.

### Shared Installation Concurrency

User A on Computer A and User B on Computer B may run the same shared installation at the same time. Each process has its own local loopback port and local runtime locks/state.

Cross-computer persistence safety is provided by the short-lived persistence file locks, not by runtime single-instance locking.

### Server Lifetime

The Java server runs independently of the Windows-selected browser. Closing browser tabs, windows, or the browser application does not intentionally stop it.

`stop.bat` invokes `MiniServer stop` through normal `java.exe`. The command reads
the local port and token, then sends `POST /__miniserver/stop` with the token in
the `X-MiniServer-Token` header to the existing loopback HTTP listener. The
internal route has no CORS support, rejects missing or incorrect tokens, and is
handled before application API/static routing. After completing the successful
HTTP response, it asynchronously calls `RunningMiniServer.close()`, which stops
the listener, invalidates `instance.json`, releases `instance.lock`, and ends the
server lifetime. No second control listener exists.

The normal startup and repeated-start locks remain authoritative. Transactionally
perfect behavior for start and stop commands launched at the exact same instant
is intentionally outside this small launcher design.

Current startup and browser authority is divided between D-020 for runtime coordination, D-024 for detached start and graceful stop, D-025 and REQ-009 for Windows browser opening, and D-027 and REQ-010 for Shared and Private start-site selection. D-026 is the superseded installation-only start-site decision. REQ-006 remains the archived historical v1.0 startup/browser contract rather than the active v1.1 browser requirement.

## Architectural Principles

- Keep the server small and understandable.
- Keep application-specific logic out of the server.
- Support shared/network installations without shared machine/process runtime state.
- Keep every HTTP listener loopback-only and dynamically allocated.
- Require explicit shared or private persistence scope for every operation.
- Derive controlled persistence locations on the server.
- Keep private user storage distinct from a security boundary.
- Separate runtime coordination locks from persistence write locks.
- Use bounded locking and atomic persistence writes.
- Avoid unnecessary frameworks, services, and persistent logging infrastructure.

## Related Documents

- `docs/DECISIONS.md` records approved decisions and superseded historical decisions.
- `requirements/` contains binding functional and non-functional requirements.
- `docs/PROJECT_NOTES.md` contains working knowledge and observations.
- `docs/DEBUGGING.md` contains known problems and verified fixes.
