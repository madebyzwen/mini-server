# Mini Server

Mini Server is a lightweight, portable Windows web server for local or trusted internal web applications. It serves multiple applications from a directly editable `www/` directory and provides an explicitly scoped JSON persistence API.

Mini Server listens only on `127.0.0.1`, asks the operating system to select an available port, and opens the maintained example application in Microsoft Edge. It is not intended for public internet use.

## Current status

The v1.0 implementation is complete through runtime distribution assembly. Final release-scope and Windows launcher verification remain pending; v1.0 has not been released.

## Requirements and startup

- Windows with a Java 8 compatible runtime available on `PATH`
- Microsoft Edge, the v1 target browser
- No installer, administrator privileges, database, or separate web server

Run `mvn clean package` to create the portable runtime ZIP in `target/`. Extract the single directory from that ZIP and double-click `start.bat`. The launcher uses the included thin `mini-server.jar` and runtime libraries from `lib/`; the Java application remains responsible for dynamic port selection, single-instance coordination, and Edge launch.

If Edge cannot be opened, the console shows the valid local URL. Keep the console window open while Mini Server is required. Closing Edge does not stop the server.

## Applications and persistence

Hosted applications are first-level directories below `www/`. The distribution includes:

- `www/example/`, the maintained example application
- `www/_shared/mini-api.js`, the shared browser client
- `miniweb-template.zip`, a reusable application template

Extract the template into a new directory such as `www/my-app/`; no application-specific Java changes are required.

Every persistence operation explicitly selects a scope:

```javascript
MiniApi.read("settings").private()
MiniApi.read("settings").shared()
MiniApi.readAll().shared()
MiniApi.write({ settings: { theme: "dark" } }).private()
MiniApi.remove("settings").shared()
MiniApi.clear().private()
```

Shared data is stored at `<installation-root>\www\<site>\data\data.json`. Private user-profile data is stored at `%APPDATA%\MiniServerData\<site>\data\data.json`. Persistence data is accessed through the API and is not exposed as ordinary static content.

## Development

Requirements and architecture are maintained in `requirements/`, `docs/ARCHITECTURE.md`, and `docs/DECISIONS.md`.

```text
mvn clean test
mvn clean package
node src/test/js/mini-api.test.js
```

The Node command runs the dependency-free MiniApi tests when Node.js is available. See `tests/README.md` and `AGENTS.md` for testing and repository workflow details. Maintainers should follow `docs/RELEASE.md` for CI and semantic release procedures.

---

## Deutsch

Mini Server ist ein leichtgewichtiger, portabler Windows-Webserver für lokale oder vertrauenswürdige interne Webanwendungen. Er stellt mehrere Anwendungen aus dem direkt bearbeitbaren Verzeichnis `www/` bereit und bietet eine JSON-Persistenz-API mit ausdrücklich gewähltem Speicherbereich.

Mini Server lauscht ausschließlich auf `127.0.0.1`, lässt das Betriebssystem einen freien Port auswählen und öffnet die Beispielanwendung in Microsoft Edge. Der Server ist nicht für den öffentlichen Internetbetrieb vorgesehen.

### Aktueller Stand

Die v1.0-Implementierung umfasst nun auch die Zusammenstellung der Laufzeitdistribution. Die abschließende Prüfung des Release-Umfangs und des Windows-Starters steht noch aus; v1.0 wurde noch nicht veröffentlicht.

### Voraussetzungen und Start

- Windows mit einer Java-8-kompatiblen Laufzeitumgebung im `PATH`
- Microsoft Edge als Zielbrowser für v1
- Kein Installer, keine Administratorrechte, keine Datenbank und kein separater Webserver erforderlich

`mvn clean package` erzeugt die portable Laufzeit-ZIP-Datei unter `target/`. Das enthaltene Verzeichnis vollständig entpacken und anschließend `start.bat` doppelklicken. Der Starter verwendet die enthaltene schlanke `mini-server.jar` und die Laufzeitbibliotheken unter `lib/`. Portauswahl, lokale Einzelinstanz-Koordination und Edge-Start verbleiben in der Java-Anwendung.

Falls Edge nicht geöffnet werden kann, zeigt die Konsole die gültige lokale URL. Das Konsolenfenster muss geöffnet bleiben, solange Mini Server benötigt wird. Das Schließen von Edge beendet den Server nicht.

### Anwendungen und Persistenz

Webanwendungen liegen als Verzeichnisse der ersten Ebene unter `www/`. Die Distribution enthält die Beispielanwendung `www/example/`, die gemeinsame Bibliothek `www/_shared/mini-api.js` und die wiederverwendbare Vorlage `miniweb-template.zip`.

Die Vorlage kann beispielsweise nach `www/my-app/` entpackt werden. Dafür sind keine anwendungsspezifischen Änderungen am Java-Server erforderlich.

Jede Persistenzoperation wählt ausdrücklich `.private()` oder `.shared()`. Gemeinsame Daten liegen unter `<Installationsverzeichnis>\www\<site>\data\data.json`; private Benutzerdaten unter `%APPDATA%\MiniServerData\<site>\data\data.json`. Persistenzdaten sind nur über die API zugänglich und werden nicht als normale statische Dateien ausgeliefert.

### Entwicklung

Die maßgeblichen Anforderungen und Architekturunterlagen befinden sich in `requirements/`, `docs/ARCHITECTURE.md` und `docs/DECISIONS.md`. Die Build- und Testbefehle entsprechen dem englischen Abschnitt „Development“. Hinweise für Maintainer zu CI und semantischen Releases stehen in `docs/RELEASE.md`.
