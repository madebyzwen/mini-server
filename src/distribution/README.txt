Mini Server
===========

Mini Server is a portable local web server for Windows. It requires a
Java 8 compatible runtime on PATH and targets Microsoft Edge for v1.0.

Installation and startup
------------------------

1. Extract or copy the complete Mini Server directory to a local,
   mapped-drive, or network location that you can access.
2. Double-click start.bat.

No installer or administrator rights are normally required. Mini Server
listens only on 127.0.0.1 and lets Windows select an available port.
Microsoft Edge should open the example application automatically. If Edge
cannot be opened, start.bat does not remain open to show that detached-process
failure; startup and browser confirmation are intentionally not awaited.

The start window closes immediately while Mini Server continues in a detached
javaw process. Closing Edge does not stop Mini Server. Double-clicking
start.bat again reuses the already-running local instance and opens its active
URL without starting a second server.

Stopping Mini Server
--------------------

Double-click stop.bat to stop the active local Mini Server gracefully. The
command uses a token stored only in the current user's local runtime state and
sends an authenticated request to the existing loopback HTTP listener. Running
stop.bat when Mini Server is already stopped is harmless.

Applications and data
---------------------

miniweb-template.zip can be extracted into a new first-level directory
below www to create another application, for example www\my-app\.

Shared data is stored at:

    www\<site>\data\data.json

Private user data is stored at:

    %APPDATA%\MiniServerData\<site>\data\data.json

Persistence data is accessed through Mini Server's explicitly scoped API;
shared data directories are not served as normal static files.
