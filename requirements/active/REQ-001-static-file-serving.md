# REQ-001 — Static File Serving

## Requirement ID

REQ-001

## Title

Static File Serving

## Status

Active

## Purpose

Mini Server must serve static files from a configured web root so that small local or internal web applications can be opened in a normal web browser.

## Description

The server must use a directory named:

www/

as its web root.

Files below this directory must be accessible through HTTP using paths relative to the web root.

Example:

Request:

/example/index.html

must resolve to:

www/example/index.html

Likewise, static resources such as CSS, JavaScript, images, and other application assets must be served from their corresponding paths below the web root.

Example:

/example/assets/app.js

must resolve to:

www/example/assets/app.js

The server must support multiple independent first-level application directories below the same web root.

Example:

www/
├── example/
├── template/
├── dashboard/
└── notes/

Each of these directories may contain its own HTML files, scripts, stylesheets, assets, and other static content.

The server must not require application-specific Java code in order to serve a newly added application directory.

## Path Handling

Requested paths must be resolved relative to the configured web root.

The server must prevent requests from escaping the web root.

Path traversal sequences or equivalent filesystem manipulation must not allow access to files outside:

www/

The client must not be able to supply an arbitrary filesystem path.

## Directory Requests

When a request targets an application directory without explicitly naming a file, the server should serve the application's default entry page when available.

Example:

/example/

should resolve to:

www/example/index.html

when that file exists.

The exact behavior for requests where no default file exists may be defined during implementation, but directory contents must not be exposed automatically.

## Content Types

The server should return appropriate HTTP content types for commonly used web resources.

At minimum, the implementation should support sensible content types for:

- HTML
- CSS
- JavaScript
- JSON
- Plain text
- Common image formats

Unknown file types may be served using a generic binary content type.

## Missing Resources

If a requested static resource does not exist, the server must return an appropriate HTTP error response instead of exposing internal filesystem information.

A missing file should normally result in:

404 Not Found

## Security Boundary

Static file serving must remain limited to the configured web root.

The implementation must not expose:

- Source files outside the web root
- Configuration files outside the web root
- User home directories
- Arbitrary absolute paths
- Parent directories of the web root

## Acceptance Criteria

REQ-001 is fulfilled when all of the following are true:

- The server uses www/ as its web root.
- Existing files below www/ can be requested through matching HTTP paths.
- Multiple independent application directories can be served from the same web root.
- A request for /<site>/ resolves only within www/<site>/.
- A request for /<site>/ serves index.html when the file exists.
- Static HTML, CSS, JavaScript, JSON, text, and common image files can be served.
- Missing files return an appropriate HTTP error response.
- Directory listings are not exposed automatically.
- Path traversal outside www/ is prevented.
- Arbitrary filesystem paths cannot be requested by the client.
- Adding another application directory below www/ does not require application-specific changes to the Java server.

## Constraints

The implementation must remain intentionally lightweight.

Static file serving should use the capabilities available in the selected Java runtime and should avoid unnecessary external server frameworks unless a later approved decision explicitly changes this.

The implementation must remain compatible with the project's approved Java runtime requirements.

## Related Decisions

- D-001 — Java 8 Compatibility
- D-014 — Not Intended for Public Internet Use

## Related Architecture

See:

docs/ARCHITECTURE.md

Relevant sections include:

- Runtime Structure
- Web Application Model
- Static File Serving
- Application Isolation

## Related Tasks

No implementation tasks have been assigned yet.

## Target Release

Initial release