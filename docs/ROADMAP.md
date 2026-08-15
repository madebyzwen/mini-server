# Roadmap

This document collects possible future development directions for Mini Server.

Items listed here are ideas, options, or potential improvements.

They are not approved requirements unless they are explicitly moved into the requirements workflow.

---

## Current Focus

Mini Server v1.0.0 has been released. The current focus is planning for v1.1.

Current planning candidates include:

- Using the Windows default browser instead of selecting Microsoft Edge directly
- Optionally configuring which existing Mini Server applications are opened automatically at startup

These candidates are described in `docs/notes/V1.1-DEFAULT-BROWSER.md` and `docs/notes/V1.1-START-SITES.md`. They are non-binding planning material, not approved requirements or implementation work.

The detailed scope of a future release will be defined by approved requirements, not by this roadmap.

---

## Possible Future Development

### Additional Runtime Implementations

The same Mini Server concept may later be implemented for additional runtime environments.

Possible implementations include:

- Python
- .NET for Windows

The goal would be to provide equivalent functionality while allowing Mini Server to run on systems where Java is unavailable.

These implementations should follow the same external behavior and API concept where practical.

---

### Improved Launcher Integration

Possible future launcher improvements may include:

- More convenient Windows startup integration
- Desktop shortcuts
- Improved detection of the preferred browser
- Optional launcher configuration
- Better startup error reporting

---

### Additional API Convenience Features

The shared JavaScript client library may later receive additional convenience functionality.

Possible examples include:

- Improved error objects
- Optional helper functions
- Better validation
- Simplified diagnostics
- API status information

Any additions should preserve the intentionally small and generic API concept.

---

### Developer Tooling

Possible improvements for developers include:

- Additional example applications
- Automated project setup
- Validation tools
- Development scripts
- Automated tests
- Packaging helpers
- Release automation

---

### Distribution and Packaging

Version 1.0 established a compact distribution that can be copied to the target Windows system without requiring a traditional system-wide installer.

The released distribution provides:

- Minimal installation effort
- Clear Java 8 runtime requirements
- A maintained ready-to-use example application
- A reusable starter template package distributed as `miniweb-template.zip`
- A simple startup experience for non-developer users
- No separate database, web server, application server, or container runtime

---

## Out of Scope Direction

Mini Server is not intended to evolve into a general-purpose public web server.

The project should remain focused on lightweight local or trusted internal web applications.

Features whose primary purpose is public internet hosting should not be added merely to compete with full web server platforms.

---

## Roadmap Workflow

Ideas may be added to this document without making them binding.

When a roadmap item becomes concrete enough to implement:

1. Define it as a requirement.
2. Add it to the appropriate requirements area.
3. Create implementation tasks where necessary.
4. Remove or update the corresponding roadmap entry if it is no longer merely a future idea.

The roadmap should remain a view of possible future directions rather than a second requirements list.
