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
cannot be opened, use the local URL shown in the console.

Keep the console window open while Mini Server is required. Closing Edge
does not stop Mini Server; the Java process in the console remains the
running server.

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
