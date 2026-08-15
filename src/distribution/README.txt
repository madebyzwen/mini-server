Mini Server
===========

Mini Server is a portable local web server for Windows. It requires a
Java 8 compatible runtime on PATH. Mini Server asks Windows to open local
application URLs using the current user's configured default browser.

Installation and startup
------------------------

1. Extract or copy the complete Mini Server directory to a local,
   mapped-drive, or network location that you can access.
2. Double-click start.bat.

No installer or administrator rights are normally required. Mini Server
listens only on 127.0.0.1 and lets Windows select an available port.
On a first v1.1 start without a personal selection, the Windows default browser
opens the built-in Welcome to Mini Server page. All available applications are
proposed, but no personal file is created until Save and open succeeds. Saving
requires at least one application, immediately opens the selection, and also
controls later normal starts.
If the browser cannot be opened, Mini Server remains active and its runtime
state remains valid. start.bat does not wait for startup or browser-opening
confirmation because the server process is detached.

The start window closes immediately while Mini Server continues in a detached
javaw process. Closing the browser does not stop Mini Server. Double-clicking
start.bat again reuses the already-running local instance and asks Windows to
open its active URL without starting a second server. A changed Windows default
browser is used on a later start action.

Automatic start sites
---------------------

The Shared installation configuration is:

    config\start-sites.txt

It centrally approves applications for automatic opening and defines their
canonical opening order. The standard distribution contains example as its
active entry.

The current-user selection is:

    %APPDATA%\MiniServer\Config\start-sites.txt

Private configuration may reduce the Shared selection. It cannot enable an
application absent from Shared and cannot reorder Shared applications. Missing
Private opens root setup without creating the file. A readable nonempty
effective selection opens its applications in Shared order; an empty, stale,
unreadable, or otherwise ineffective selection opens root recovery instead.

Both files use UTF-8 text with one first-level application name per line. Empty
lines and lines beginning with # after surrounding whitespace are ignored.
Missing or unreadable Shared and readable Shared with no valid applications
open root recovery and make saving unavailable. Invalid entries and entries for
missing applications are ignored.

Changes to either file apply the next time start.bat is invoked. The active
server does not need to be restarted. Start-site selection controls automatic
browser opening only; it is not application access control.

Double-click configure.bat to start or reuse the same server and open only the
selection page. Configure does not modify Private by itself. The page proposes
all Shared choices only when Private is missing; otherwise it reflects the
current readable selection or shows recovery without guessing unreadable state.
Saving replaces the complete personal selection, navigates the current root tab
to the first selected application, and opens additional applications through
the Windows default browser.

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

    %APPDATA%\MiniServer\Data\<site>\data.json

Persistence data is accessed through Mini Server's explicitly scoped API;
shared data directories are not served as normal static files.

When the canonical Private file is absent, released v1.0 data at
%APPDATA%\MiniServerData\<site>\data\data.json is migrated safely before normal
Private use. Existing canonical data always wins.
