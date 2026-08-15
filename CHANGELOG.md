# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-08-15

### Added

- Portable Windows distribution compatible with Java 8, including detached `start.bat`/`javaw.exe` startup and graceful `stop.bat` shutdown.
- Loopback-only HTTP serving on `127.0.0.1` with an operating-system-selected dynamic port and local per-user/computer single-instance behavior.
- Automatic Microsoft Edge launch using the active Mini Server URL, including repeated-start reuse of the running instance and port.
- Static hosting for multiple independent applications below the editable `www/` web root, with protected persistence directories and path-traversal prevention.
- Shared dependency-free `MiniApi` browser library with explicitly selected private and shared JSON persistence operations.
- File-based per-application persistence with server-controlled paths, bounded write locking, atomic replacement, and concise path-safe error handling.
- Maintained example application and reusable `miniweb-template.zip` starter package.
- Maven build, automated Java and JavaScript test coverage, GitHub Actions CI, and automated semantic release publication.

[Unreleased]: https://github.com/madebyzwen/mini-server/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/madebyzwen/mini-server/releases/tag/v1.0.0
