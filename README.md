# Mini Server

A lightweight web server for local or internal web applications with a simple JSON-based API.

The project is intended for use on trusted local systems or internal environments. It is **not intended for public internet use**.

## Project Status

The project is currently in early development.

The initial goal is to provide a minimal local web server that can host multiple independent small web applications from a shared `www` root directory.

The distribution may reside on a shared/network drive and may be used from different computers concurrently. Each computer runs its own loopback-only server and keeps runtime coordination state in the current user's local profile.

Each application is stored in its own subdirectory and can use the central JSON API with an explicit shared or private persistence scope.

## Core Concept

The server provides:

- A shared `www` root directory
- Multiple independent web applications below that root
- Static file serving
- A central server-side JSON API
- A shared JavaScript client library for using the API
- Shared and private JSON data storage for each web application
- Controlled application and persistence-scope mapping

Example structure:

```text
www/
├── _shared/
│   └── mini-api.js
├── example/
│   ├── index.html
│   ├── assets/
│   └── data/
│       └── data.json
└── another-app/
    └── ...
```

A shared-scope request such as:

```text
/example/api/shared/read?section=settings
```

is handled by the server's central API implementation and is automatically scoped to:

```text
<installation-root>\www\example\data\data.json
```

A private request uses `/example/api/private/...` and maps to:

```text
%APPDATA%\MiniServerData\example\data\data.json
```

Browser code selects the scope explicitly after the operation, for example:

```javascript
MiniApi.read("settings").private()
MiniApi.read("settings").shared()
```

The web application itself does not implement its own server-side API.

## Documentation

Detailed project information is maintained in the repository documentation:

- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/PROJECT_NOTES.md`
- `docs/DEBUGGING.md`
- `docs/ROADMAP.md`
- `requirements/`

The repository uses English as its primary language.

A German documentation section will be added to this README as the project documentation matures.

## Development

Application implementation has not started yet.

The v1.0 architecture, requirements, decisions, and implementation tasks have been defined and prepared for implementation through Codex.

See `AGENTS.md` for the repository workflow and instructions for coding agents.
