<p align="center">
  <img
    src="docs/images/logo.png"
    alt="MADE by ZWEN"
    width="220"
  >
</p>

<h1 align="center">MINI SERVER</h1>

<p align="center">
  <strong>A lightweight portable web server for local and trusted internal web applications.</strong>
</p>

<p align="center">
  Windows · Local only · Dynamic port · JSON persistence API · No installation
</p>

<p align="center">
  Created by Sven Hüttmann<br>
  <a href="https://madebyzwen.dev">madebyzwen.dev</a>
</p>

<p align="center">
  <a href="https://buymeacoffee.com/madebyzwen">☕ Buy me a coffee</a>
</p>

<p align="center">
  <strong><a href="https://github.com/madebyzwen/mini-server/releases/latest">⬇ Download latest release</a></strong><br>
  <a href="https://github.com/madebyzwen/mini-server/releases">All releases</a>
</p>

---

<a id="documentation-languages"></a>
<h2 align="center">Documentation</h2>

<p align="center">
  <a href="#english-documentation">English</a> · <a href="#deutsche-dokumentation">Deutsch</a>
</p>

---

<a id="english-documentation"></a>

## Overview

Mini Server is a lightweight, portable Windows web server for local or trusted internal web applications. It serves multiple applications from the editable `www/` directory and provides a file-based JSON persistence API without requiring a database, installer, or separate web server.

The server listens only on `127.0.0.1` and lets the operating system select an available TCP port. It is designed for local use and is not intended for exposure to the public internet.

The v1.1 behavior documented below is implemented on the current development branch but has not yet been published as a release; the latest published release remains v1.0.0 until final verification succeeds.


## Features

- Portable Windows runtime distribution
- Java 8 compatible
- No installer or administrator rights normally required
- Loopback-only HTTP listener on `127.0.0.1`
- Dynamic, operating-system-selected port
- One active instance per local user/computer context
- Detached startup through `start.bat` and `javaw.exe`
- Graceful shutdown through `stop.bat`
- Automatic launch through the Windows default browser
- Built-in first-run welcome and start-site selection page
- Repeated starts reuse the active local instance and port
- Multiple hosted applications below `www/`
- Shared `MiniApi` browser library
- Explicit private and shared JSON persistence
- Reusable `miniweb-template.zip` application template
- No database

## Requirements

- Windows
- A Java 8 compatible runtime available on `PATH`

## Download & Quick Start

1. Open the [latest GitHub Release](https://github.com/madebyzwen/mini-server/releases/latest).
2. Download the Mini Server release ZIP.
3. Extract the complete ZIP to the desired location.
4. Double-click `start.bat`.
5. The command window closes immediately after launching the detached `javaw.exe` process.
6. On the first v1.1 start without a personal selection, the Windows default browser opens `Welcome to Mini Server`.
7. Select the applications to open on later starts and save the selection.
8. Closing the browser does not stop Mini Server.
9. Double-click `stop.bat` when Mini Server is no longer required.

`start.bat` launches the existing `MiniServer` entry point through `javaw.exe`; the batch window is not tied to the server lifetime. Mini Server continues running after that window disappears. Starting it again reuses the active local server and its dynamic port instead of creating a second HTTP server.

`stop.bat` gracefully stops the active local instance. Running it when Mini Server is already stopped is harmless. The released v1.0 start, repeated-start, and stop workflow was manually verified on Windows; the new v1.1 welcome flow still requires its planned Windows verification.

## Automatic start sites

The installation owner approves automatic start sites and their order in `<installation-root>\config\start-sites.txt`. Each Windows user has a complete personal selection at `%APPDATA%\MiniServer\Config\start-sites.txt`. Shared approval is always the upper bound: a personal selection can reduce it, but cannot add or reorder applications.

When the personal file is missing, Mini Server creates it from the current valid Shared entries after the local server is ready, then opens only the built-in welcome page. The page initially checks every currently approved application. Saving creates a new personal selection and replaces the entire existing selection; an empty selection is allowed and takes effect on the next start action. Visit `http://127.0.0.1:<active-port>/` later to repeat the same fresh replacement workflow.

Start-site selection controls automatic browser opening only. It is not access control, and valid applications remain directly reachable at their normal URLs.

## Applications

Hosted applications are first-level directories below `www/`. The distribution includes:

- `www/example/` — the maintained example application
- `www/_shared/mini-api.js` — the shared browser API client
- `miniweb-template.zip` — a reusable starting point for a new application

To create an application, extract or copy the template into a new directory such as `www/my-app/`. No application-specific Java code is required.

## Persistence API

Include `/_shared/mini-api.js` in an application to use the browser-side `MiniApi`. Every operation explicitly selects its storage scope with `.private()` or `.shared()`; there is no default scope.

```javascript
MiniApi.read("settings").private()
MiniApi.read("settings").shared()
MiniApi.readAll().shared()
MiniApi.write({ settings: { theme: "dark" } }).private()
MiniApi.remove("settings").shared()
MiniApi.clear().private()
```

## Data locations

Shared data is stored with the application:

```text
<installation-root>\www\<site>\data\data.json
```

Private data is stored in the current Windows user profile:

```text
%APPDATA%\MiniServer\Data\<site>\data.json
```

Shared data can travel with an application and can be shared when the installation directory itself is shared. Private data belongs to the current Windows user profile. Here, “private” describes the storage location and scope; it is not authentication or authorization isolation between hostile applications.

Persistence data is accessed through the Mini Server API and is not served as ordinary application static content.

On first Private access after updating from v1.0, Mini Server safely migrates existing data from `%APPDATA%\MiniServerData\<site>\data\data.json` when no canonical v1.1 file exists. An existing canonical file always wins.

## Project structure

A release ZIP contains a portable runtime directory similar to this:

```text
mini-server-<version>\
├── mini-server.jar
├── lib\
├── miniweb-template.zip
├── start.bat
├── stop.bat
├── README.txt
├── config\
│   └── start-sites.txt
└── www\
    ├── _shared\
    │   └── mini-api.js
    └── example\
```

## Privacy and security scope

- The HTTP server listens only on `127.0.0.1` and is not intended for public internet exposure.
- Mini Server adds no analytics or tracking.
- Persistence remains local or shared according to the explicitly selected scope.
- Applications hosted by one Mini Server instance should be treated as part of the same trusted environment.
- Private persistence means user-profile storage, not user authentication.
- The graceful-stop mechanism is local infrastructure, not a public application API.

For deeper technical details, see [Architecture](docs/ARCHITECTURE.md) and [Decisions](docs/DECISIONS.md).

## Development

Maven builds and tests the Java project:

```text
mvn clean test
mvn clean package
```

When Node.js is available, run the dependency-free MiniApi JavaScript tests with:

```text
node src/test/js/mini-api.test.js
```

Developer and maintainer documentation:

- [Architecture](docs/ARCHITECTURE.md)
- [Technical decisions](docs/DECISIONS.md)
- [Release process](docs/RELEASE.md)
- [Agent workflow](AGENTS.md)
- [Test documentation](tests/README.md)

## Support the project

If Mini Server is useful to you and you would like to support its continued development, you can [buy me a coffee](https://buymeacoffee.com/madebyzwen).

## License

Mini Server is licensed under the [MIT License](LICENSE).

[Back to language selection](#documentation-languages)

---

<a id="deutsche-dokumentation"></a>

<details>
<summary><strong>🇩🇪 Deutsche Dokumentation</strong></summary>

## Übersicht

Mini Server ist ein leichtgewichtiger, portabler Windows-Webserver für lokale oder vertrauenswürdige interne Webanwendungen. Er stellt mehrere Anwendungen aus dem direkt bearbeitbaren Verzeichnis `www/` bereit und bietet eine dateibasierte JSON-Persistenz-API – ohne Datenbank, Installer oder separaten Webserver.

Der Server lauscht ausschließlich auf `127.0.0.1`. Einen freien TCP-Port wählt das Betriebssystem automatisch aus. Mini Server ist für den lokalen Einsatz vorgesehen und nicht für den öffentlichen Internetbetrieb bestimmt.

Das nachfolgend dokumentierte v1.1-Verhalten ist im aktuellen Entwicklungsstand implementiert, aber noch nicht als Release veröffentlicht. Bis zum erfolgreichen Abschluss der finalen Prüfung bleibt v1.0.0 das zuletzt veröffentlichte Release.


## Funktionen

- Portable Laufzeitdistribution für Windows
- Kompatibel mit Java 8
- Normalerweise weder Installer noch Administratorrechte erforderlich
- HTTP-Zugriff ausschließlich über `127.0.0.1`
- Dynamischer, vom Betriebssystem ausgewählter Port
- Eine aktive Instanz pro lokalem Benutzer-/Computer-Kontext
- Abgekoppelter Start über `start.bat` und `javaw.exe`
- Kontrolliertes Beenden über `stop.bat`
- Automatisches Öffnen über den Windows-Standardbrowser
- Integrierte Willkommens- und Startauswahlseite für den ersten Start
- Wiederholter Start verwendet die aktive lokale Instanz und deren Port
- Mehrere Anwendungen unterhalb von `www/`
- Gemeinsame Browserbibliothek `MiniApi`
- Explizit gewählte private und gemeinsame JSON-Persistenz
- Wiederverwendbare Anwendungsvorlage `miniweb-template.zip`
- Keine Datenbank

## Voraussetzungen

- Windows
- Eine Java-8-kompatible Laufzeitumgebung im `PATH`

## Download & Schnellstart

1. Das [aktuelle GitHub Release](https://github.com/madebyzwen/mini-server/releases/latest) öffnen.
2. Die Release-ZIP-Datei von Mini Server herunterladen.
3. Die ZIP-Datei vollständig am gewünschten Ort entpacken.
4. `start.bat` doppelklicken.
5. Das CMD-Fenster schließt sich sofort wieder, nachdem der abgekoppelte `javaw.exe`-Prozess gestartet wurde.
6. Beim ersten v1.1-Start ohne persönliche Auswahl öffnet der Windows-Standardbrowser `Welcome to Mini Server`.
7. Die Anwendungen auswählen, die bei späteren Starts geöffnet werden sollen, und die Auswahl speichern.
8. Der Browser kann geschlossen werden, ohne Mini Server zu beenden.
9. `stop.bat` doppelklicken, um Mini Server kontrolliert zu beenden.

`start.bat` startet den vorhandenen `MiniServer`-Einstiegspunkt über `javaw.exe`. Das Batchfenster ist nicht an die Laufzeit des Servers gekoppelt; Mini Server läuft weiter, nachdem das Fenster verschwunden ist. Ein erneuter Start verwendet die bereits aktive lokale Instanz und deren dynamischen Port, anstatt einen zweiten HTTP-Server zu starten.

`stop.bat` beendet die aktive lokale Instanz kontrolliert. Der Aufruf ist auch dann harmlos, wenn Mini Server bereits beendet ist. Der veröffentlichte v1.0-Ablauf für Start, wiederholten Start und Stopp wurde unter Windows manuell geprüft; für den neuen v1.1-Willkommensablauf steht die geplante Windows-Prüfung noch aus.

## Automatische Startanwendungen

Der Eigentümer der Installation legt die für den automatischen Start freigegebenen Anwendungen und ihre Reihenfolge in `<Installationsverzeichnis>\config\start-sites.txt` fest. Jeder Windows-Benutzer besitzt eine vollständige persönliche Auswahl unter `%APPDATA%\MiniServer\Config\start-sites.txt`. Die gemeinsame Freigabe bleibt immer die Obergrenze: Die persönliche Auswahl kann sie reduzieren, aber keine Anwendungen ergänzen oder umsortieren.

Fehlt die persönliche Datei, erzeugt Mini Server sie nach dem erfolgreichen Serverstart aus den aktuell gültigen gemeinsamen Einträgen und öffnet ausschließlich die integrierte Willkommensseite. Dort sind zunächst alle aktuell freigegebenen Anwendungen markiert. Das Speichern erzeugt eine neue persönliche Auswahl und ersetzt die gesamte vorhandene Auswahl; auch eine leere Auswahl ist zulässig und gilt ab der nächsten Startaktion. Unter `http://127.0.0.1:<aktiver-port>/` lässt sich dieser neue Ersetzungsvorgang später erneut aufrufen.

Die Startauswahl steuert nur das automatische Öffnen im Browser. Sie ist keine Zugriffskontrolle; gültige Anwendungen bleiben über ihre normalen URLs direkt erreichbar.

## Anwendungen

Webanwendungen liegen in Verzeichnissen der ersten Ebene unterhalb von `www/`. Die Distribution enthält:

- `www/example/` — die gepflegte Beispielanwendung
- `www/_shared/mini-api.js` — den gemeinsamen Browser-Client für die API
- `miniweb-template.zip` — eine wiederverwendbare Vorlage für neue Anwendungen

Für eine neue Anwendung wird die Vorlage beispielsweise nach `www/my-app/` entpackt oder kopiert. Anwendungsspezifischer Java-Code ist nicht erforderlich.

## Persistenz-API

Über `/_shared/mini-api.js` steht Anwendungen die Browser-API `MiniApi` zur Verfügung. Jede Operation wählt ihren Speicherbereich ausdrücklich mit `.private()` oder `.shared()`; einen Standardspeicherbereich gibt es nicht.

```javascript
MiniApi.read("settings").private()
MiniApi.read("settings").shared()
MiniApi.readAll().shared()
MiniApi.write({ settings: { theme: "dark" } }).private()
MiniApi.remove("settings").shared()
MiniApi.clear().private()
```

## Speicherorte

Gemeinsame Daten werden zusammen mit der Anwendung gespeichert:

```text
<Installationsverzeichnis>\www\<site>\data\data.json
```

Private Daten liegen im Profil des aktuellen Windows-Benutzers:

```text
%APPDATA%\MiniServer\Data\<site>\data.json
```

Gemeinsame Daten können mit einer Anwendung weitergegeben werden und stehen gemeinsam zur Verfügung, wenn das Installationsverzeichnis selbst geteilt wird. Private Daten gehören zum Profil des aktuellen Windows-Benutzers. „Privat“ beschreibt hier Speicherort und Geltungsbereich, nicht die Authentifizierung oder Autorisierung gegeneinander abgeschotteter Anwendungen.

Persistenzdaten werden über die Mini-Server-API gelesen und geschrieben. Sie werden nicht als gewöhnliche statische Anwendungsinhalte ausgeliefert.

Beim ersten privaten Datenzugriff nach einem Update von v1.0 migriert Mini Server vorhandene Daten sicher aus `%APPDATA%\MiniServerData\<site>\data\data.json`, sofern noch keine kanonische v1.1-Datei existiert. Eine vorhandene kanonische Datei hat immer Vorrang.

## Projektstruktur

Eine Release-ZIP-Datei enthält ein portables Laufzeitverzeichnis mit ungefähr folgender Struktur:

```text
mini-server-<version>\
├── mini-server.jar
├── lib\
├── miniweb-template.zip
├── start.bat
├── stop.bat
├── README.txt
├── config\
│   └── start-sites.txt
└── www\
    ├── _shared\
    │   └── mini-api.js
    └── example\
```

## Datenschutz und Sicherheitsrahmen

- Der HTTP-Server lauscht ausschließlich auf `127.0.0.1` und ist nicht für den öffentlichen Internetbetrieb vorgesehen.
- Mini Server fügt keine Analytics- oder Trackingfunktionen hinzu.
- Persistenzdaten bleiben je nach ausdrücklich gewähltem Speicherbereich lokal oder gemeinsam.
- Gemeinsam gehostete Anwendungen sind als Teil derselben vertrauenswürdigen Umgebung zu betrachten.
- Private Persistenz bedeutet Speicherung im Benutzerprofil, nicht Benutzerauthentifizierung.
- Der kontrollierte Stoppmechanismus ist lokale Infrastruktur und keine öffentliche Anwendungs-API.

Technische Details stehen in der [Architektur](docs/ARCHITECTURE.md) und den [Entscheidungen](docs/DECISIONS.md).

## Entwicklung

Maven baut und testet das Java-Projekt:

```text
mvn clean test
mvn clean package
```

Wenn Node.js verfügbar ist, werden die abhängigkeitsfreien JavaScript-Tests für MiniApi so ausgeführt:

```text
node src/test/js/mini-api.test.js
```

Weiterführende Dokumentation:

- [Architektur](docs/ARCHITECTURE.md)
- [Technische Entscheidungen](docs/DECISIONS.md)
- [Release-Prozess](docs/RELEASE.md)
- [Agenten-Workflow](AGENTS.md)
- [Testdokumentation](tests/README.md)

## Projekt unterstützen

Wenn Mini Server für dich nützlich ist und du die Weiterentwicklung unterstützen möchtest, kannst du mir [einen Kaffee spendieren](https://buymeacoffee.com/madebyzwen).

## Lizenz

Mini Server steht unter der [MIT-Lizenz](LICENSE).

[Zurück zur Sprachauswahl](#documentation-languages)

</details>

---

<p align="center">
  Created by Sven Hüttmann · <a href="https://madebyzwen.dev">madebyzwen.dev</a>
</p>
