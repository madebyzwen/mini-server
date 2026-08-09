# REQ-005 — Example and Template Applications

## Requirement ID

REQ-005

## Title

Example and Template Applications

## Status

Active

## Purpose

Mini Server must include a working example application and a reusable template application.

These applications should demonstrate the intended project structure and provide developers with a simple starting point for creating additional Mini Server web applications.

## Description

The initial distribution must contain the following application directories:

www/example/

and:

www/template/

Both applications should initially use the same basic structure and demonstrate the same core Mini Server functionality.

The `example` application serves as a working demonstration.

The `template` application serves as a clean reusable starting point for developers.

## Directory Structure

Both applications should initially follow a structure similar to:

www/<site>/
├── index.html
├── assets/
└── data/
    └── data.json

Additional files may be included where required for the demonstration.

Shared Mini Server functionality must not be duplicated unnecessarily inside these application directories.

The shared JavaScript API library must continue to be provided centrally through:

www/_shared/mini-api.js

## Example Application

The `example` application is intended to demonstrate how a web application uses Mini Server.

It should provide a simple browser-based demonstration of the available persistence API.

The demonstration should make it possible to exercise the following operations:

- Read one section
- Read all stored data
- Write data
- Remove one section
- Clear stored data

The example should use the shared `mini-api.js` library rather than implementing its own HTTP or JSON transport layer.

The example may be modified and extended over time as the Mini Server functionality evolves.

## Template Application

The `template` application is intended to provide a clean starting point for new web applications.

It should initially contain the same minimal functional demonstration as the example application.

The template should remain intentionally simple.

Developers should be able to copy the template directory and use the copy as the basis for a new application.

The distributed template should not contain application-specific business logic.

## Initial Page Content

The template should contain a minimal visible example demonstrating that the page is being served correctly.

The initial content should include the text:

Hello Mini Webserver

The source should also contain a clear developer-facing indication that the demonstration content may be replaced with the developer's own application.

The exact visual appearance is not part of this requirement.

## API Demonstration

The example and template applications must demonstrate use of the shared JavaScript API.

The demonstration must use:

MiniApi.read(section)

MiniApi.readAll()

MiniApi.write(data)

MiniApi.remove(section)

MiniApi.clear()

Application code should work with native JavaScript objects and arrays.

The demonstration must not require manual JSON.stringify() or JSON.parse() calls for normal MiniApi usage.

## Demonstration Data

Each application must use its own data file.

For example:

www/example/data/data.json

and:

www/template/data/data.json

Actions performed in the example application must not modify the template application's data.

Actions performed in the template application must not modify the example application's data.

The demonstration data should remain simple and neutral.

Application-specific example data should not create unnecessary dependencies or assumptions about future use cases.

## API Documentation

The example and template content should provide enough information for a developer to understand how the shared API is used.

The documentation may be presented directly in the application, in accompanying files, or through a combination of both.

At minimum, developers should be able to determine:

- How to include mini-api.js
- How to read one section
- How to read all data
- How to write data
- How to remove a section
- How to clear data
- That native JavaScript objects and arrays can be used directly
- That each application automatically operates on its own data file

## Independence from Server Implementation

The example and template applications must interact with Mini Server through the documented browser-side API.

They must not depend on internal Java implementation details.

A developer creating a new application from the template should not need to modify the Java server for normal application-specific content.

## Acceptance Criteria

REQ-005 is fulfilled when all of the following are true:

- www/example/ exists.
- www/template/ exists.
- Both applications contain a working index.html.
- Both applications have their own data/data.json file.
- Both applications use the shared www/_shared/mini-api.js library.
- The example application demonstrates read.
- The example application demonstrates readAll.
- The example application demonstrates write.
- The example application demonstrates remove.
- The example application demonstrates clear.
- The template provides the same initial minimal API demonstration.
- The template contains the visible text "Hello Mini Webserver".
- The template clearly indicates that the demonstration content may be replaced by the developer.
- Application code works with native JavaScript objects and arrays.
- Manual JSON.stringify() and JSON.parse() are not required for normal MiniApi usage.
- The example and template applications cannot modify each other's persistent data through their normal API namespace.
- A developer can copy the template and use it as the basis for another application without requiring application-specific Java server changes.
- The included documentation explains the basic MiniApi usage.

## Constraints

The example and template applications should remain intentionally small.

Their purpose is to demonstrate Mini Server, not to introduce a full frontend framework or complex application architecture.

The content should use English for source code, comments, labels, and developer documentation.

## Related Decisions

- D-006 — Generic Server-Side Data Handling
- D-007 — One JSON Data File per Application
- D-009 — Shared Central API Implementation
- D-010 — Shared JavaScript API Library
- D-011 — Native JavaScript Objects and Arrays
- D-012 — Example and Template Applications
- D-013 — English Repository Language

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Web Application Model
- Shared JavaScript Client Library
- Example and Template Applications
- Application Isolation

## Related Tasks

No implementation tasks have been assigned yet.

## Target Release

Initial release