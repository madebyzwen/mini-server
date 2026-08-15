# Tests

This directory contains test documentation and other test-related project material for Mini Server.

Automated Java test source code belongs in the conventional Maven test source tree:

    src/test/java/

The top-level `tests/` directory is not the Java test source directory.

## Purpose

Automated tests should verify behavior defined by the active requirements and help prevent regressions during development.

Test coverage should focus on server behavior, API behavior, path handling, persistence, startup behavior, error handling, and other functionality that can be verified reliably.

Manual checks may still be necessary for behavior that depends on the Windows desktop environment or its configured default browser.

## Java Test Source

Automated Java test classes belong below:

    src/test/java/

The normal test package hierarchy should correspond to the production package hierarchy where practical.

The base test package is therefore expected below:

    src/test/java/io/github/madebyzwen/miniserver/

The Maven build is responsible for compiling and running these automated Java tests.

Do not place Java test classes directly in the top-level `tests/` directory.

## Testing Priorities

The initial automated test suite should focus on behavior that is both important and reliably automatable.

Priority areas include:

- Static file serving
- Content type handling
- Missing file handling
- Directory request behavior
- Path traversal prevention
- Site detection
- Namespace and persistence scoping
- Explicit shared/private scope selection
- Persistence data protection from static serving
- JSON persistence
- Reading one section
- Reading all sections
- Writing one section
- Writing multiple sections
- Replacing existing sections
- Preserving unrelated sections during partial writes
- Removing sections
- Clearing data
- Stored JSON null handling
- Invalid JSON handling
- Invalid persistence root handling
- Section-name validation
- Invalid API requests
- HTTP method handling
- HTTP error responses
- File permission failures where practical
- Bounded persistence write-lock failures
- Atomic write visibility
- Dynamic port allocation
- Local per-user/computer single-instance behavior
- Concurrent use of one shared installation from different computer contexts
- Runtime state handling
- Startup race behavior where practical
- Server startup failures

## Test Independence

Tests should be independent from each other where practical.

A test must not rely on data created by an unrelated previous test.

Temporary test data must be isolated from normal project data.

Tests must not permanently modify:

- The maintained `www/example/` content
- The maintained reusable `template/` source
- Packaged template artifacts

Tests should use temporary application and persistence directories when behavior requires filesystem modifications.

## Test Data

Test-specific files and directories may be created as part of the test environment.

They must remain clearly separated from normal runtime, demonstration, and reusable template content.

Tests should clean up temporary data where practical.

Do not use developer-specific absolute paths.

Temporary test paths should be created dynamically by the test environment.

## Filesystem Tests

Filesystem-related tests should verify that Mini Server cannot escape its permitted directories.

Tests should include attempts such as:

- Parent directory traversal
- Encoded traversal where relevant
- Invalid site names
- Unknown site namespaces
- Arbitrary filesystem paths
- Attempts to override the persistence location derived from the requested site namespace
- Direct static access to protected persistence directories
- Access to files outside the configured test web root

A successful test suite must not depend on exposing files outside the configured test environment.

## Persistence Tests

Persistence tests should verify the data model defined by D-021 through D-023 and REQ-003.

Tests should confirm that:

- The persistence root is a JSON object
- Top-level properties represent sections
- Section values preserve valid JSON-compatible values
- A stored JSON null value remains distinguishable from a missing section
- `readAll` returns an empty object for a not-yet-created persistence store
- Partial writes preserve unrelated sections
- Existing sections can be replaced
- Missing sections can be created
- One section can be removed without affecting unrelated sections
- Clear produces the logical empty state
- Invalid JSON is detected
- A non-object persistence root is rejected
- Successful modifications leave valid JSON
- Failed modifications are not reported as successful
- Shared and private paths are derived correctly
- Every operation requires an explicit scope
- Modifying operations obtain a bounded exclusive target-file lock
- Write-lock timeout returns `Write failed`
- Atomic writes prevent readers from observing partial JSON
- Reads do not acquire a separate read lock

## API Tests

API tests should verify both successful and failed behavior.

Tests should cover the HTTP contract:

    GET    /<site>/api/<scope>/read?section=<name>
    GET    /<site>/api/<scope>/readAll
    POST   /<site>/api/<scope>/write
    DELETE /<site>/api/<scope>/remove?section=<name>
    DELETE /<site>/api/<scope>/clear

Tests should confirm that:

- Valid requests succeed
- private and shared scope map to their approved locations
- Missing, unknown, and unscoped requests fail
- The alternative `/<site>/<scope>/api/<operation>` layout fails
- Invalid requests fail
- Correct HTTP methods are required
- Unsupported methods return the defined error response
- Missing sections are distinguishable from stored JSON null values
- Successful modifying operations return `204 No Content`
- JSON-returning operations return valid JSON
- Invalid JSON request bodies are rejected
- Empty write objects are rejected
- Invalid section names are rejected
- Failed requests are not reported as successful
- API errors use the defined error structure
- Clients cannot redirect persistence operations to arbitrary filesystem locations
- Unknown site namespaces do not create new application directories
- Reserved Mini Server areas are not treated as normal application API namespaces

## Dynamic Port Tests

Server startup tests must allow the operating system to assign the listening port dynamically.

Tests must request:

    0

as the server port.

Tests must not depend on a fixed TCP port or manual port scanning.

The actual assigned port must be obtained from the running server instance.

## Single-Instance Tests

Where practical, automated tests should verify the local per-user/computer single-instance behavior defined by D-020 and REQ-006.

Tests should cover:

- Coordinating concurrent startup attempts through local startup.lock
- Acquiring instance.lock for a new local context
- Detecting an already owned instance lock
- Preventing a second server instance in the same local user/computer context
- Allowing separate computer contexts to use the same shared installation
- Publishing the active runtime port
- Ignoring stale runtime state when no active lock exists
- Preventing stale runtime state from being treated as proof of a running server
- Handling the startup race between lock acquisition and runtime-state publication
- Releasing process-owned lock state after process termination where reliably testable

Tests must not depend on a permanently configured port.

## Runtime State Tests

Runtime-state tests should use isolated temporary local-runtime directories that represent:

    %LOCALAPPDATA%\MiniServer\runtime\

They must not modify the developer's real local runtime directory or create runtime coordination state in a shared test installation.

Tests should verify that runtime state remains outside the installation/web root, is user/context scoped, and is not exposed by static serving.

## Java Compatibility

Tests for the initial release must remain compatible with the project's Java 8 target.

The test framework and all test dependencies must support Java 8.

A passing test suite on a newer development JDK does not replace final verification against the approved Java 8 runtime.

## Manual Verification

Some behavior may require manual verification on the intended Windows target environment.

Examples include:

- Windows default-browser launch through the configured HTTP URL handler
- Respecting a changed Windows default browser on a later start action
- Desktop shortcut or launcher behavior
- Interaction with existing default-browser instances
- Browser-opening failure remaining nonfatal while exposing the active URL where practical
- Closing the selected browser without stopping Mini Server
- User-visible startup diagnostics
- Repeated desktop start using the active server URL and port
- Behavior during Windows logoff and shutdown where appropriate

These checks are planned manual verification areas; this document does not claim they have already been performed. Results should be documented as part of release verification rather than represented as automated test results.

## Test Execution

The authoritative automated Java test command will be provided by the Maven build.

The normal project-level test operation should therefore be based on:

    mvn test

The exact Maven test framework and plugin versions will be finalized during implementation preparation.

The test command must return a failing exit status when one or more automated tests fail.

Convenience scripts may invoke the Maven test command but must not define an independent test system.

## Coding Agent Guidance

Coding agents should add or update automated tests when implementing behavior that can reasonably be verified automatically.

Before adding tests:

1. Read `AGENTS.md`.
2. Read the relevant active requirements.
3. Check `docs/ARCHITECTURE.md`.
4. Check applicable decisions in `docs/DECISIONS.md`.
5. Review the relevant implementation task in `tasks/ACTIVE.md`.
6. Use the authoritative Maven build configuration once `pom.xml` exists.

Do not weaken or remove a valid test merely to make an implementation pass.

If behavior cannot be tested reliably in an automated environment, document the limitation and use an appropriate manual verification step.

Do not claim that tests passed unless they were actually executed.

## Current State

The automated Java test source location is defined as:

    src/test/java/

The concrete test framework and implementation will be created later through Codex as part of the implementation work.
