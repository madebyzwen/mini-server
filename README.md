# Mini Server

A lightweight web server for local or internal web applications with a simple JSON-based API.

The project is intended for use on trusted local systems or internal environments. It is **not intended for public internet use**.

## Project Status

The project is currently in early development.

The initial goal is to provide a minimal local web server that can host multiple independent small web applications from a shared `www` root directory.

Each application is stored in its own subdirectory and can use the server's central JSON API for reading and writing its own data.

## Core Concept

The server provides:

- A shared `www` root directory
- Multiple independent web applications below that root
- Static file serving
- A central server-side JSON API
- A shared JavaScript client library for using the API
- Separate JSON data storage for each web application
- Isolation between individual web applications

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
├── template/
│   ├── index.html
│   ├── assets/
│   └── data/
│       └── data.json
└── another-app/
    └── ...
```

A request such as:

```text
/example/api/read?section=settings
```

is handled by the server's central API implementation and is automatically scoped to:

```text
www/example/data/data.json
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

Implementation has not started yet.

See `AGENTS.md` for the repository workflow and instructions for coding agents.