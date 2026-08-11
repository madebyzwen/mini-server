# Project Notes

## Current Project State

The project is currently in the preparation and initial implementation phase.

The repository structure, documentation framework, and Codex workflow have been prepared before starting the actual server implementation.

## Development Environment

The project is developed primarily with Visual Studio Code and Codex.

The active development workspace is accessed through VS Code Remote SSH.

The development environment runs on a Linux Mint virtual machine named `dev-mint`.

Project files are stored on a NAS-backed development workspace and mounted into the virtual machine.

## Repository Language

The repository uses English as the primary language for:

- Source code
- Code comments
- Filenames
- Project documentation
- Examples
- Template content

German documentation will also be provided where appropriate, especially in the main `README.md`.

## General Project Direction

The project provides a lightweight web server intended for local or internal use.

It is not designed as a public internet-facing web server.

The server is intended to host multiple small web applications below a shared `www` root directory.

Each application will live in its own subdirectory.

A shared server-side API will provide JSON-based persistence for the individual applications.

A shared JavaScript client library will provide a simple interface to that API.

## Development Principles

Keep the implementation intentionally small and understandable.

Avoid unnecessary frameworks and dependencies where native platform functionality is sufficient.

The server should remain generic and should not interpret application-specific JSON data.

Persistence should remain predictably scoped by application and explicit shared/private selection. This prevents accidental mixing but is not authentication or authorization between hosted applications.

Project documentation should distinguish clearly between:

- Requirements
- Architectural decisions
- Implementation notes
- Future ideas
- Debugging information

## Notes for Future Work

The exact implementation details belong in the authoritative architecture, decision, and requirement documents.

Do not treat these project notes as a substitute for those documents.

This file should be updated with useful discoveries and operational knowledge that may help future development but does not justify a formal architectural decision.
