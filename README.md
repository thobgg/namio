# Namio

Android-App, mit der Lehrer die Namen ihrer Schulklassen lernen.
Klassen anlegen, Fotos zuordnen, per Quiz mit Spaced Repetition üben — komplett offline.

<p align="center">
  <img src="docs/screenshots/klassen.png" width="180" alt="Klassenliste">
  <img src="docs/screenshots/klasse.png" width="180" alt="Klassendetail">
  <img src="docs/screenshots/schueler.png" width="180" alt="Schülerdetail">
  <img src="docs/screenshots/avatare.png" width="180" alt="Avatar-Auswahl">
</p>
<p align="center">
  <img src="docs/screenshots/auswahl.png" width="180" alt="Quizauswahl">
  <img src="docs/screenshots/frage.png" width="180" alt="Quizfrage">
  <img src="docs/screenshots/ergebnis.png" width="180" alt="Ergebnis">
</p>
<p align="center">
  <img src="docs/screenshots/sitzplan.png" width="180" alt="Sitzplan-Editor">
  <img src="docs/screenshots/sitzplan_quiz.png" width="180" alt="Sitzplan-Quiz">
</p>

## Rechtlicher Hinweis

Namio verarbeitet Namen und Fotos von Schülerinnen und Schülern – in der Regel Minderjährigen.
Ob und wie du solche Daten erheben, speichern und auf einem privaten Gerät nutzen darfst, richtet
sich nach der DSGVO, dem Schulgesetz deines Bundeslandes und den Vorgaben deiner Schule
(z. B. Einwilligung der Erziehungsberechtigten, dienstliche Geräte). **Das musst du selbst klären,
bevor du die App mit echten Daten benutzt.** Namio ist ein privates Hobbyprojekt ohne Gewähr;
der Autor übernimmt keine Verantwortung für die rechtmäßige Nutzung und keine Haftung für Schäden,
die aus der Nutzung entstehen. Die App speichert alles ausschließlich lokal und verschlüsselt und
sendet nichts – die Verantwortung für Gerät, Sperre, Backups und Löschung liegt bei dir.

## Datenschutz zuerst

Die App verarbeitet Fotos von Minderjährigen. Deshalb gelten harte Regeln, keine Empfehlungen:

- **Kein Netzwerk.** Keine `INTERNET`-Berechtigung, keine Analytics, kein Crash-Reporting.
- **Kein Cloud-Backup.** `allowBackup="false"` und leere `dataExtractionRules`.
- **Verschlüsselte Datenbank.** Room + SQLCipher, Passphrase im Android Keystore.
- **Fotos nur im internen Speicher.** Nie in der Galerie, nie auf der SD-Karte.
- **Echtes Löschen.** Klasse weg heißt Bilddateien weg.

## Funktionen

**Klassen & Schüler**
- Klassenliste mit Lernfortschritt, Schülerraster mit Fotos
- Foto per Kamera (quadratischer Sucher mit Gesichtsrahmen), aus der Galerie oder aus 35 mitgelieferten Cartoon-Avataren
- Vorname, Nachname, Spitzname, Notiz, Geschlecht
- CSV-Import der Klassenliste (Untis/Excel): Trennzeichen und Zeichensatz werden erkannt, Spaltenzuordnung mit Vorschau
- Demoklasse mit 24 Schülern beim ersten Start, jederzeit löschbar
- Deutsch und Englisch

**Sitzplan**
- Frei positionierbare, drehbare Plätze – schräge Doppeltische, U-Form, Gruppentische, alles was der Raum hergibt
- Vorlagen zum Start (Doppeltischreihen, U-Form, Gruppentische), Partnerplatz mit einem Tipp, Möbel wie Pult und PC als Beschriftung
- Drag & Drop oder Antippen, Einrasten am Raster, Sitzordnung mischen, mehrere Pläne pro Klasse, Plan kopieren
- Mehrfachauswahl: Tische gemeinsam verschieben, drehen (Buttons oder Zwei-Finger-Drehung) und ausrichten; zwei Kinder mit zwei Tipps tauschen
- Sperre gegen versehentliche Änderungen, Rückgängig, zufällige Person auslosen (Platz blinkt)
- Blickrichtung umschaltbar: vom Lehrerpult aus oder wie auf dem Papier

**Quiz**
- *Foto → Name (Auswahl)*: Foto oben, vier Namen zur Auswahl
- *Foto → Name (Tippen)*: Freitext, Tippfehler und fehlende Akzente werden toleriert
- *Name → Foto*: Name oben, neun Gesichter zur Auswahl
- *Sitzplan*: „Wo sitzt …?“ – auf den Platz tippen, trainiert die Situation im Klassenraum
- *Speedrun*: 60 Sekunden, so viele wie möglich – ohne Einfluss auf den Lernstand
- Statistik je Klasse: Boxverteilung, Trefferquote der letzten Runden, häufigste Verwechslungen
- Sofortiges Feedback, Fortschrittsbalken, Ergebnis mit Fehlerliste und Wiederholungsrunde
- Ablenker werden bewusst gewählt: erst frühere Verwechslungen, dann gleicher Anfangsbuchstabe, dann Zufall — immer gleiches Geschlecht, immer aus derselben Klasse

**Sicherheit & Daten**
- App-Sperre beim Öffnen (Fingerabdruck, Gesicht oder Geräte-PIN), standardmäßig an
- Export/Import aller Daten als passwortgeschützte Datei (`.namio`, AES-256-GCM, Schlüssel per PBKDF2) – zum Gerätewechsel oder als Sicherung, ohne Cloud
- „Alle Daten löschen“ in den Einstellungen, echtes Löschen inklusive Fotodateien
- Hinweis zur rechtlichen Verantwortung beim ersten Start

**Lernalgorithmus**
- Leitner mit fünf Boxen und kurzen Intervallen (sofort · 10 min · 1 Tag · 3 Tage · 7 Tage)
- Falsch heißt zurück auf Box 1. Ein Lernstand pro Schüler *und* Modus.
- Falsch beantwortete Schüler kommen in derselben Runde noch einmal dran

## Geplant

- Erinnerung am Schuljahresende, Accessibility-Durchgang

## Tech-Stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Room + SQLCipher (`sqlcipher-android`) · CameraX · Coil 3 · Navigation Compose (Type-Safe) · JUnit 5

minSdk 26 · targetSdk 35 · Handy und Tablet

## Bauen

```bash
JAVA_HOME=<JDK 17 oder 21> ./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Für Release-Builds wird eine `keystore.properties` im Projektstamm erwartet (nicht im Repo).

## Struktur

```
de.namio
├── core
│   ├── data          Room-Entities, DAOs, Datenbank, Migrationen
│   ├── lernen        Leitner, Kartenauswahl, Ablenkerwahl, Rundenlogik (reines Kotlin, getestet)
│   ├── media         FotoStore
│   ├── model         Domain-Modelle
│   ├── repository    Datenzugriff für ViewModels
│   └── security      Keystore
├── feature           klassen · schueler · quiz
└── ui                Theme, Komponenten, Navigation
```
