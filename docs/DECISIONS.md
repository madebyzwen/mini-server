# Decisions

This document records approved technical and project decisions that should remain stable unless they are deliberately changed later.

Each decision should describe what was decided, why it was decided, and any important consequences.

---

## D-001 — Java 8 Compatibility

### Decision

The Java implementation must remain compatible with Java 8.

The target runtime environment is expected to provide a Java 8 compatible runtime.

### Rationale

The intended target systems may only provide Java 8.

Using a newer Java language level would reduce compatibility with those systems and would require additional runtime installation or deployment steps.

### Consequences

- Source code must use Java 8 compatible language features.
- Dependencies must support Java 8.
- Build configuration must target Java 8.
- APIs introduced only in later Java versions must not be required.

---

## D-002 — Local Loopback Binding

### Decision

The server should bind to the local loopback interface using:

```text
127.0.0.1
```

for the local desktop use case.

### Rationale

The primary purpose of the server is to provide local web applications for the current machine.

Binding to the loopback interface prevents the server from being exposed to the local network by default.

### Consequences

- The normal local URL uses `127.0.0.1`.
- The service is not reachable from other computers when bound only to the loopback interface.
- Public internet exposure is outside the project scope.

---

## D-003 — Dynamic Port Allocation

### Decision

The server must not use a permanently configured fixed port.

At startup, it should request port:

```text
0
```

from the operating system.

The operating system then selects an available local TCP port.

### Rationale

A fixed port may already be occupied or may conflict with software or policies on the target system.

Allowing the operating system to select an available port avoids unnecessary port conflicts and removes the need for port scanning.

### Consequences

- The actual port must be read from the server socket after startup.
- Any automatically opened browser URL must use the assigned port.
- Application code must not assume a fixed port number.

---

## D-004 — Browser Launch Uses the Assigned Port

### Decision

After the server has started successfully and the operating system has assigned a port, the launcher should open Microsoft Edge with the corresponding local URL.

Example:

```text
http://127.0.0.1:<assigned-port>/<site>/
```

### Rationale

The user should be able to start the server and the web application with one action without manually determining the assigned port.

### Consequences

- Browser startup must happen only after the server port is known.
- The dynamically assigned port must be inserted into the launch URL.
- Edge is used as the intended browser on the Windows target environment.

---

## D-005 — No Database

### Decision

The project does not use a database.

Persistent application data is stored in JSON files.

### Rationale

The intended applications are small and do not require database functionality.

A database would introduce unnecessary complexity, dependencies, installation requirements, and maintenance.

### Consequences

- Each application stores its persistent data in its own JSON file.
- The server provides file-based JSON persistence through the central API.
- Database drivers, servers, migrations, and schemas are outside the project scope.

---

## D-006 — Generic Server-Side Data Handling

### Decision

The server must not interpret application-specific data.

The server understands only the technical structure required for persistence, such as:

- Site
- Section
- JSON data
- API operation

### Rationale

The server should remain reusable for different small web applications without requiring server changes for application-specific business logic.

### Consequences

- Application-specific validation and semantics belong primarily to the web application.
- The Java server must not contain hard-coded knowledge about individual applications.
- New applications should normally be deployable without modifying the server implementation.

---

## D-007 — One JSON Data File per Application

### Decision

Each web application has its own JSON persistence file.

The standard location is:

```text
www/<site>/data/data.json
```

### Rationale

Separate data files keep individual applications simple and isolated while preserving a predictable directory structure.

### Consequences

Examples:

```text
www/example/data/data.json
www/template/data/data.json
www/dashboard/data/data.json
```

The API namespace of one application must not access another application's data file.

---

## D-008 — Application Scope Is Derived from the URL

### Decision

The server determines the application scope from the first relevant path component of the request URL.

The client does not provide a filesystem path or arbitrary storage location.

### Rationale

The server must control which JSON file belongs to a request.

Allowing clients to submit filesystem paths would create unnecessary complexity and security risks.

### Consequences

For example:

```text
/example/api/read?section=settings
```

is scoped to:

```text
www/example/data/data.json
```

The site name is determined by the request path.

---

## D-009 — Shared Central API Implementation

### Decision

The Java server contains one central API implementation.

Individual applications below `www` do not implement separate server-side APIs.

### Rationale

Duplicating API implementations would increase maintenance effort and could cause inconsistent behavior between applications.

### Consequences

The same API operations are available within each application's URL namespace.

Examples:

```text
/example/api/read
/template/api/read
/dashboard/api/read
```

All are handled by the same server-side implementation.

---

## D-010 — Shared JavaScript API Library

### Decision

A common JavaScript library named:

```text
mini-api.js
```

provides the browser-side interface to the server API.

It is stored centrally and reused by all applications.

### Rationale

Application developers should not have to repeatedly implement HTTP requests, JSON serialization, JSON deserialization, and common API handling.

### Consequences

The intended location is:

```text
www/_shared/mini-api.js
```

Applications may include it with:

```html
<script src="/_shared/mini-api.js"></script>
```

The library exposes a simple interface such as:

```javascript
MiniApi.read(section)
MiniApi.readAll()
MiniApi.write(data)
MiniApi.remove(section)
MiniApi.clear()
```

---

## D-011 — Native JavaScript Objects and Arrays

### Decision

Application developers should work with native JavaScript objects and arrays when using `MiniApi`.

They should not normally need to call:

```javascript
JSON.stringify()
JSON.parse()
```

themselves.

### Rationale

JSON transport is an implementation detail of the shared API library and should not complicate application code.

### Consequences

- `mini-api.js` performs serialization before sending requests.
- `mini-api.js` performs deserialization after receiving responses.
- Basic input validation is performed by the library before sending data.

---

## D-012 — Example and Template Applications

### Decision

The initial distribution contains two example application directories:

```text
www/example/
www/template/
```

Both start with equivalent minimal demonstration content.

### Rationale

The `example` application provides a working demonstration.

The `template` application provides a clean starting point for developers.

### Consequences

- `template` should remain an unchanged starting template.
- `example` may be modified to demonstrate functionality.
- Both should initially document and demonstrate the available API.

---

## D-013 — English Repository Language

### Decision

English is the primary language of the repository.

This applies to:

- Source code
- Code comments
- Filenames
- Example applications
- Template content
- Primary project documentation

### Rationale

English keeps the repository accessible to a broader developer audience and provides a consistent technical language.

### Consequences

German documentation should additionally be provided where appropriate, especially within the main `README.md`.

Separate competing documentation structures should be avoided where the same README can reasonably contain both languages.

---

## D-014 — Not Intended for Public Internet Use

### Decision

Mini Server is not designed or supported as a public internet-facing web server.

### Rationale

The project intentionally focuses on a small, simple runtime for local or trusted internal applications.

Internet-facing operation would require a significantly different security model and operational scope.

### Consequences

Features required specifically for hardened public web hosting are outside the intended project scope.

This limitation should remain clearly documented for users and developers.

---

## D-015 — Persistence Data Is Not Served as Static Content

### Decision

Application persistence data stored below:

www/<site>/data/

must not be served directly by the static file handler.

In particular, a request such as:

/example/data/data.json

must not return the contents of:

www/example/data/data.json

Persistent application data may only be accessed through the site's JSON API.

The `data` directory directly below an application directory is therefore reserved for Mini Server persistence and is not part of the application's publicly served static content.

### Rationale

The persistence file is located below the `www` directory to keep each web application portable as a self-contained directory.

However, allowing the static file handler to expose that persistence file would bypass the JSON API completely.

API behavior such as section-based access, validation, error handling, and controlled persistence would become ineffective if clients could read the complete data file directly through a static URL.

Keeping the persistence directory below the application directory while excluding it from static file serving preserves both portability and the intended API boundary.

### Consequences

- `www/<site>/data/` is a reserved Mini Server directory.
- Files below that directory must not be returned by normal static file requests.
- `www/<site>/data/data.json` is accessed through the central JSON API only.
- Static JSON files may still be served from other application locations when they are normal web application resources.
- Static file path validation must detect and reject requests targeting the reserved persistence directory.
- This rule protects the persistence API boundary but does not by itself provide authentication or a security boundary between deliberately interacting local applications.

---

## Changing Decisions

Existing decisions should not be silently rewritten when project requirements change.

When a previously approved decision is changed:

1. Record the new decision explicitly.
2. Reference the decision being superseded.
3. Explain the reason for the change.
4. Update affected requirements, architecture documentation, and implementation as necessary.

Historical decisions should remain understandable from the repository history.