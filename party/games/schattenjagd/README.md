# Schattenjagd

Verfolgungsspiel mit verdeckter Bewegung fuer Open Party Lab. Ein Spieler reist als "Der Schatten" unsichtbar durch ein festes Verkehrsnetz, alle anderen jagen ihn als Ermittler.

## Status

Alpha. Der komplette Spielablauf ist spielbar: Kartengenerierung, Zugphasen, Tickets, Sonderzuege, Enttarnungsrunden, Fang- und Fluchtauswertung, KI-Schatten. Gegen die Server-KI reichen zwei Spieler; mit menschlichem Schatten werden mindestens drei Spieler benoetigt.

## Regeln in Kurzform

- Gespielt wird auf einer festen, illustrierten Stadtkarte mit 64 Stationen und drei Verkehrsebenen (Taxi, Bus, Metro). Der Server waehlt beim Rundenstart passend zum Raum-Theme die Nacht- oder Tagesansicht; waehrend einer laufenden Runde bleibt diese Variante unveraendert. Ein schematisches Strassennetz verbindet alle Stationen. Metro verlaeuft oben/links, Bus mittig und Taxi unten/rechts auf denselben Abschnitten. Die Haltestellen tragen nur einen farbigen Rand: Taxi gelb/rund, Bus gruen/quadratisch, Metro violett/sechseckig. An Bus-Stationen halten weiterhin Taxis, an Metro-Stationen auch Busse und Taxis. Ermittler besetzen das Stationszentrum in ihrer Spielerfarbe.
- 22 Zuege. In den Zuegen 3, 8, 13, 18 und 22 wird die Position des Schattens oeffentlich sichtbar.
- Pro Zug zieht zuerst der Schatten, danach jede ermittelnde Person einzeln in fester Sitzreihenfolge. Jede Person erhaelt ihr eigenes Zeitfenster. Nach Zeitablauf bleibt nur diese Person stehen; getrennte Spieler und Personen ohne legalen Zug werden uebersprungen.
- Das Verkehrsnetz wird aus den echten Verbindungen gezeichnet. Gemeinsame Strassenabschnitte mit deckungsgleichen Knicken ersetzen die individuellen Routen; die schmalen Farbspuren liegen direkt nebeneinander. Die ruhigen Grundfarben werden fuer legale Strecken/Ziele der aktiven Person kraeftig hervorgehoben. Nur nummerierte Stationen sind Haltestellen, an unnummerierten Kreuzungen kann man nicht umsteigen. Jede Station hat mindestens zwei unterschiedliche Taxi-Nachbarn und damit keine Sackgasse; Station 6 besitzt getrennte Zugaenge aus mehreren Richtungen.
- Ein Ticket fuehrt zur naechsten Haltestelle des jeweiligen Verkehrsmittels. Bus und Metro passieren kleinere Haltestellen, ueberspringen aber keine Station, an der ihr Verkehrsmittel haelt. Diese Netz-Anpassung entfernt die bisherigen, auf einer gemeinsamen Linie nicht erkennbaren Express-Abkuerzungen.
- Der Schatten reist unbegrenzt, verraet aber nach jedem Zug das benutzte Verkehrsmittel.
- Ermittler haben ein begrenztes Ticketkonto: 12 Taxi, 9 Bus, 5 Metro.
- Zwei Sonderzuege des Schattens: **Schleier** verbirgt das Verkehrsmittel, **Doppelzug** erlaubt zwei Zuege hintereinander.
- Ermittler duerfen nicht auf eine Station ziehen, auf der bereits ein anderer Ermittler steht. Der Schatten weicht besetzten Stationen ebenfalls aus.
- Die Ermittler gewinnen, sobald eine Figur auf der Station des Schattens landet oder der Schatten keinen legalen Zug mehr hat.
- Der Schatten gewinnt, wenn er alle 22 Zuege uebersteht.

## Lobby

Die Lobby oeffnet sich als eigene Ansicht, sobald das Spiel gewaehlt ist. Einstellbar sind:

**Schatten**

- **Zufaelliger Spieler** - ein Spieler wird geheim ausgelost.
- **Fester Sitzplatz** - der Spieler mit der gewaehlten Platznummer (Platz 1 = zuerst beigetreten).
- **Server-KI** - alle Spieler ermitteln gemeinsam gegen einen vom Server gesteuerten Schatten.

**Zugzeit**

- 20, 30, 45 (Standard), 60 oder 90 Sekunden pro Zug.
- **Ohne Zeitlimit** schaltet den Countdown ganz ab. Es wird dann auf jeden Zug gewartet;
  getrennte Spieler werden weiterhin uebersprungen, damit die Runde nicht haengt.

## Punkte

- Festnahme: alle Ermittler +2, die festnehmende Person zusaetzlich +1.
- Gelungene Flucht: der Schatten +4 (nur bei menschlichem Schatten).

## Run Through Open Party Lab

Dieses Repo ist keine eigenstaendige App. Es laeuft ueber die Open-Party-Lab-Plattform.

Empfohlenes Layout:

```text
Open-Party-Lab/
  local-games/
    schattenjagd/
```

Aus dem Plattform-Repo:

```bash
npm install
npm run games:sync-local
npm run dev:all
```

Die Plattform laedt dieses Spiel nur, wenn das Repo lokal existiert und `npm run games:sync-local` es verlinkt hat.

## Eigenstaendigkeit und Rechte

Karte, Stationsnetz, Namen, Rollenbezeichnungen, Regelwerte, Texte und Grafik dieses Repos sind Eigenentwicklungen. Es werden keine fremden Marken, Kartenvorlagen, Illustrationen oder Textbausteine verwendet. Das feste Netz wird deterministisch erzeugt (`src/map/generateTransitMap.ts`) und deckungsgleich auf zwei eigene fiktive Stadtillustrationen (Nacht und Tag) gelegt. Taxi-, Bus- und Metro-Tickets sowie die animierten Fahrzeugmarker sind ebenfalls eigene Spielgrafiken.

## Package-Entrypoints

```text
@open-party-lab/game-schattenjagd/manifest
@open-party-lab/game-schattenjagd/protocol
@open-party-lab/game-schattenjagd/server
@open-party-lab/game-schattenjagd/host
@open-party-lab/game-schattenjagd/controller
```

## Entwicklung

```bash
npm install
npm run typecheck
npm run build
npm test
```

## Lizenz

Apache-2.0
