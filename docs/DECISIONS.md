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

    www/<site>/data/data.json

### Rationale

Separate data files keep individual applications simple and separated while preserving a predictable directory structure.

### Consequences

Examples:

    www/example/data/data.json
    www/dashboard/data/data.json
    www/dashboard/data/data.json

Each API namespace maps predictably to the persistence file belonging to the site identified by that request URL.

For example:

    /example/api/

maps to:

    www/example/data/data.json

The client must not be able to redirect that namespace to another persistence file by supplying an arbitrary filesystem path or storage location.

This mapping provides namespace and filesystem separation but is not an authentication or authorization boundary between hosted applications.

A deliberately written client may explicitly address another valid application's API namespace as described in D-016.

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

    www/_shared/mini-api.js

Applications may include it with:

    <script src="/_shared/mini-api.js"></script>

The library exposes the following public browser-side API:

    MiniApi.readSection(section)
    MiniApi.readAll()
    MiniApi.write(data)
    MiniApi.removeSection(section)
    MiniApi.clear()

These method names form the intended public MiniApi interface for the initial release.

Application code should use this public interface rather than constructing persistence API requests directly during normal use.

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

## D-012 — Example Application and Reusable Template Package

### Decision

The initial distribution contains a working example application at:

    www/example/

The example application is part of the normal Mini Server web root and serves as the maintained reference implementation and API demonstration.

A reusable starter template is distributed separately as:

    miniweb-template.zip

The template archive is stored outside the `www` web root and is not itself served as web content.

The distribution does not require a permanently installed:

    www/template/

application.

Developers may extract or copy the template into a new first-level application directory below `www/` when creating a new application.

For example:

    www/my-app/

### Rationale

The example application and the reusable template have different purposes.

The `example` application is a living demonstration and may evolve as Mini Server functionality changes.

The template should remain a clean and unchanged starting point that developers can copy when creating a new application.

Keeping the template as a separate archive prevents the distributed starter template from being modified accidentally during normal use or development of the example application.

Keeping the archive outside `www/` also prevents the template package itself from becoming normal static web content.

### Consequences

- `www/example/` is included as the maintained demonstration application.
- `example` may be updated as Mini Server evolves.
- `miniweb-template.zip` provides the reusable clean starting point for new applications.
- The template archive is distributed outside the `www` web root.
- A permanent `www/template/` directory is not required in the normal distribution.
- Developers create a new application by extracting or copying the template into a new first-level directory below `www/`.
- The extracted template must work without application-specific changes to the Java server.
- The template uses the shared `www/_shared/mini-api.js` library.
- The template demonstrates the public MiniApi interface.
- The template contains a minimal visible `Hello Mini Webserver` example.
- The template remains application-neutral and contains no application-specific business logic.

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

## D-016 — Application Separation Is Namespace Isolation, Not Authentication

### Decision

Mini Server separates application persistence by URL namespace and filesystem location.

Each application has its own persistence location:

    www/<site>/data/data.json

and its own API namespace:

    /<site>/api/

The server derives the target site from the request URL and does not allow the client to provide an arbitrary persistence filesystem path.

This separation is intended to prevent accidental cross-site persistence access and filesystem path manipulation.

It is not an authentication or authorization boundary between hosted applications.

### Rationale

All applications hosted by one Mini Server instance normally share the same HTTP origin:

    http://127.0.0.1:<port>

Browser same-origin rules therefore do not isolate applications merely because they use different URL path prefixes.

For example, JavaScript loaded from:

    /example/

could deliberately send a request to:

    /template/api/readAll

The server would interpret that request as a request for the `template` namespace because the target site is derived from the requested URL.

Preventing such deliberate interaction would require an authentication or authorization mechanism that is outside the intended scope of the lightweight initial implementation.

### Consequences

- Each API request may access only the persistence location derived from that request's site namespace.
- Clients cannot provide arbitrary filesystem paths to select another persistence file.
- `MiniApi` automatically uses the current application's site namespace for normal application code.
- Separate application directories and persistence files prevent accidental mixing of application data.
- Hosted applications must not be treated as mutually untrusted security principals.
- A deliberately written application can request another application's API namespace.
- Mini Server v1.0 does not provide authentication or authorization between hosted applications.
- Applications hosted together by one Mini Server instance should therefore be considered part of the same trusted local environment.
- Public internet exposure remains outside the supported project scope.

---

## Changing Decisions

Existing decisions should not be silently rewritten when project requirements change.

When a previously approved decision is changed:

1. Record the new decision explicitly.
2. Reference the decision being superseded.
3. Explain the reason for the change.
4. Update affected requirements, architecture documentation, and implementation as necessary.

Historical decisions should remain understandable from the repository history.