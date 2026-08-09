# Tests

This directory contains automated tests for Mini Server.

Tests should verify the behavior defined by the active requirements and help prevent regressions during development.

## Purpose

Use this directory for repeatable tests covering server behavior, API behavior, path handling, persistence, error handling, and other functionality that can be verified automatically.

Manual checks may still be necessary for behavior that depends on the Windows desktop environment or Microsoft Edge.

## Testing Priorities

The initial test suite should focus on behavior that is both important and reliably automatable.

Priority areas include:

- Static file serving
- Content type handling
- Missing file handling
- Directory request behavior
- Path traversal prevention
- Site detection
- Site isolation
- JSON persistence
- Reading one section
- Reading all sections
- Writing one section
- Writing multiple sections
- Replacing existing sections
- Removing sections
- Clearing data
- Invalid JSON handling
- Invalid API requests
- File permission failures where practical
- Dynamic port allocation
- Server startup failures

## Test Independence

Tests should be independent from each other where practical.

A test should not rely on data created by an unrelated previous test.

Temporary test data should be isolated from normal project data.

Tests must not modify the distributed example or template data permanently.

## Test Data

Test-specific files and directories may be created as part of the test environment.

They should remain clearly separated from production or demonstration content.

Tests should clean up temporary data where practical.

Do not use developer-specific absolute paths.

## Filesystem Tests

Filesystem-related tests should verify that Mini Server cannot escape its permitted directories.

Tests should include attempts such as:

- Parent directory traversal
- Encoded traversal where relevant
- Invalid site names
- Arbitrary filesystem paths
- Access to another site's data

A successful test suite must not depend on exposing files outside the configured test web root.

## API Tests

API tests should verify both successful and failed behavior.

Tests should confirm that:

- Valid requests succeed.
- Invalid requests fail.
- Failed requests are not reported as successful.
- One site cannot modify another site's data.
- Existing unrelated sections remain intact during partial writes.
- Invalid JSON is detected.
- Stored JSON remains valid after successful write operations.

## Dynamic Port Tests

Server startup tests should allow the operating system to assign the listening port dynamically.

Tests must not depend on a fixed TCP port.

The actual assigned port should be obtained from the running server instance.

## Java Compatibility

Tests for the initial release must remain compatible with the project's Java 8 target.

The test framework and all test dependencies must support Java 8.

A passing test suite on a newer JDK does not replace final verification on the approved Java 8 runtime.

## Manual Verification

Some behavior may require manual verification on the intended Windows target environment.

Examples include:

- Microsoft Edge launch
- Desktop shortcut or launcher behavior
- User-visible startup errors
- Interaction with existing Edge instances

Manual verification results should be documented as part of release verification rather than represented as automated test results.

## Coding Agent Guidance

Coding agents should add or update tests when implementing behavior that can reasonably be verified automatically.

Do not weaken or remove a valid test merely to make an implementation pass.

If a requirement cannot be tested automatically, document the limitation and use an appropriate manual verification step.

Do not claim that tests passed unless they were actually executed.

## Test Execution

The project should eventually provide one clear command for running the complete automated test suite.

The exact command will be defined when the build system and test framework are implemented.

The test command must return a failing exit status when one or more tests fail.

## Current State

No automated tests have been implemented yet.