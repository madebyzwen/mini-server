# REQ-005 — Example Application and Template Package

## Requirement ID

REQ-005

## Title

Example Application and Template Package

## Status

Active

## Purpose

Mini Server must include a maintained example application and a reusable starter template package.

The example application demonstrates the intended Mini Server application structure and public MiniApi interface.

The template package provides developers with a clean and reusable starting point for creating new Mini Server web applications.

## Description

The initial distribution must contain a working example application at:

    www/example/

The example application is part of the normal Mini Server web root and may evolve as Mini Server functionality changes.

A reusable starter template must be distributed separately as:

    miniweb-template.zip

The template archive must be stored outside the `www` web root.

A permanent:

    www/template/

application is not required in the normal distribution.

Developers create a new application by extracting or copying the template into a new first-level application directory below `www/`.

For example:

    www/my-app/

The resulting application must work without application-specific changes to the Java server.

## Example Application

The `example` application is the maintained reference implementation for Mini Server.

It must provide a simple browser-based demonstration of the public persistence API.

The demonstration must make it possible to exercise:

- Read one section
- Read all stored data
- Write one or more sections
- Remove one section
- Clear stored data

The example application must use the shared:

    www/_shared/mini-api.js

library rather than implementing its own HTTP or JSON transport layer.

The example application may be modified and extended as Mini Server evolves.

Its bundled shared persistence data is stored at:

    <installation-root>\www\example\data\data.json

The example must also demonstrate private persistence at:

    %APPDATA%\MiniServerData\example\data\data.json

and clearly label which scope each operation uses.

## Template Package

The reusable starter template is distributed as:

    miniweb-template.zip

The archive is stored outside the `www` web root and is not itself a hosted Mini Server application.

The template contents must be suitable for use as a new first-level application below `www/`.

A newly created application should have a structure similar to:

    www/<site>/
    ├── index.html
    ├── assets/
    └── data/
        └── data.json

Additional files may be included when required by the template demonstration.

The template must use the shared Mini Server JavaScript library from:

    /_shared/mini-api.js

Shared Mini Server functionality must not be duplicated unnecessarily inside the template.

The template should remain intentionally small, application-neutral, and suitable as a clean starting point for developers.

## Initial Template Content

The template must contain a minimal visible example demonstrating that the application is being served correctly and can access its persistence data through MiniApi.

The visible example must include:

    Hello Mini Webserver

The displayed value must be obtained from the persistence data of the application created from the template through:

    MiniApi.read("start").shared()

rather than being used only as hard-coded page text.

The template persistence data must therefore provide a `start` section containing the value required for this demonstration.

The source must also contain a clear developer-facing indication that the demonstration content may be replaced with the developer's own application.

The exact visual appearance is not part of this requirement.

## MiniApi Demonstration

Both the maintained example application and the reusable template must demonstrate the public MiniApi interface:

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

Every demonstrated persistence operation must select a scope explicitly and must use operation-first chaining.

Application code must work with native JavaScript objects and arrays.

The demonstration must not require manual `JSON.stringify()` or `JSON.parse()` calls for normal MiniApi usage.

## Persistence Scoping

The example application's shared scope uses:

    <installation-root>\www\example\data\data.json

Its private scope uses:

    %APPDATA%\MiniServerData\example\data\data.json

A new application created from the template can use both corresponding locations.

For example:

    <installation-root>\www\my-app\data\data.json

and:

    %APPDATA%\MiniServerData\my-app\data\data.json

During normal MiniApi usage, requests are automatically scoped to the site namespace of the current application.

For example, MiniApi calls made from:

    /example/

normally target:

    /example/api/<scope>/

while MiniApi calls made from:

    /my-app/

normally target:

    /my-app/api/<scope>/

Different application directories or persistence scopes must not accidentally share the same persistence file.

This separation is based on URL namespaces and controlled persistence path mapping.

It is not an authentication or authorization boundary between deliberately interacting applications.

Applications hosted by one Mini Server instance are considered part of the same trusted local environment.

## API Documentation

The example application and template content must provide enough information for a developer to understand normal MiniApi usage.

At minimum, developers must be able to determine:

- How to include `mini-api.js`
- How to read one Section with `MiniApi.read(section).private()` or `.shared()`
- How to read all data with `MiniApi.readAll().private()` or `.shared()`
- How to write data with `MiniApi.write(data).private()` or `.shared()`
- How to remove one Section with `MiniApi.remove(section).private()` or `.shared()`
- How to clear data with `MiniApi.clear().private()` or `.shared()`
- That every operation requires an explicit persistence scope and that operation-first chain order is mandatory
- That native JavaScript objects and arrays can be used directly
- That MiniApi automatically scopes normal requests to the current application
- That each application has separate shared and private persistence files
- That persistence files are accessed through the JSON API rather than through direct static file access

## Independence from Server Implementation

The example application and applications created from the template must interact with Mini Server through the documented browser-side API.

They must not depend on internal Java implementation details.

A developer creating a new application from the template must not need to modify the Java server for normal application-specific content.

## Acceptance Criteria

REQ-005 is fulfilled when all of the following are true:

- `www/example/` exists.
- The example application contains a working `index.html`.
- The example application has its own shared `data/data.json` and can use its private user-profile persistence file.
- The example application uses the shared `www/_shared/mini-api.js` library.
- The example application demonstrates read, readAll, write, remove, and clear with explicit private and shared scope selectors.
- `miniweb-template.zip` is included in the distribution.
- The template archive is stored outside the `www` web root.
- A permanent `www/template/` application is not required.
- The template can be extracted or copied into a new first-level application directory below `www/`.
- An application created from the template uses the shared `mini-api.js` library.
- An application created from the template receives its own shared `data/data.json` and can use its private user-profile persistence file.
- The template contains a visible `Hello Mini Webserver` demonstration.
- The displayed `Hello Mini Webserver` value is obtained through `MiniApi.read("start").shared()`.
- The template clearly indicates that its demonstration content may be replaced by the developer.
- Application code works with native JavaScript objects and arrays.
- Manual `JSON.stringify()` and `JSON.parse()` are not required for normal MiniApi usage.
- Normal MiniApi usage derives the current application's namespace and explicitly selects its private or shared persistence file.
- A developer can create another application from the template without application-specific Java server changes.
- The included documentation explains basic MiniApi usage.

## Constraints

The example application and template must remain intentionally small.

Their purpose is to demonstrate Mini Server and provide a starting point, not to introduce a full frontend framework or complex application architecture.

The template must remain application-neutral.

The content must use English for source code, comments, labels, and developer documentation.

The template archive must not be placed inside the served `www` web root.

## Related Decisions

- D-006 — Generic Server-Side Data Handling
- D-008 — Application Scope Is Derived from the URL
- D-009 — Shared Central API Implementation
- D-010 — Shared JavaScript API Library
- D-011 — Native JavaScript Objects and Arrays
- D-012 — Example Application and Reusable Template Package
- D-013 — English Repository Language
- D-015 — Persistence Data Is Not Served as Static Content
- D-016 — Application Separation Is Namespace Isolation, Not Authentication
- D-021 — Explicit Shared and Private Persistence Scopes
- D-022 — Explicitly Scoped Persistence API Contract

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Web Application Model
- Shared JavaScript Client Library
- Example Application and Template
- Application and Scope Isolation

## Related Tasks

See:

    tasks/ACTIVE.md

Relevant implementation tasks include:

- T-008 — Create Example Application
- T-009 — Create Reusable Template Package
- T-013 — Add Automated Tests
- T-014 — Verify Initial Release Scope

## Target Release

v1.0
