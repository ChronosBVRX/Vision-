# Dungeon Party

Kompetitives Koop-Fantasyspiel: gemeinsam durch den Dungeon, persönlich um Ruhm und Beute.

**Status: spielbare Alpha in Überarbeitung.** Acht Räume, zwei geheime Wegabstimmungen, ein dreiphasiger Boss, sechs Klassen und ein kompetitives Ruhmziel bilden die Kampagne. Abstimmungen, Grundaktionen, Kartenreaktionen und das Lesen der Auflösung haben kein Zeitlimit. Der Host zeigt eigene Raum- und Monsterillustrationen, Würfelwürfe und konkrete Folgen pro Held; auf dem Handy liegt die eigene Hand als auswählbare Kartenfächer vor. Inhaltstiefe, Laufzeit, Balance und Mehrtelefon-Runden bleiben noch zu prüfen.

Der Lauf mischt fünf normale Räume aus **13 Vorlagen** und je zwei Angebote aus **acht Routen**. Fünf wechselnde Raumregeln machen Kampf, Hilfe, Klassenaktionen und Beute je nach Situation wertvoller. Vor Raum 4 und dem Boss gibt es eine Lagerpause: kostenlos 3 Leben heilen, für 2 Gold zwei Aktionskarten kaufen, für 3 Gold dauerhaft 1 Kampfkraft aufrüsten oder kostenlos 2 Gruppenkraft für den nächsten Raum vorbereiten.

Der Kartenpool enthält **20 Vorlagen: sechs Ausrüstungen und 14 Aktionskarten in zehn Effektfamilien**. Neu sind Heilung, Gegner schwächen, Glückswürfe und Gruppenschutz. Alle starten mit drei Aktionskarten und 2 Gold. Nach erfolgreichen normalen Räumen zieht jede Person mit weniger als drei Handkarten eine Aktionskarte nach; Beuteaktionen und geschaffte Bossphasen geben weitere Karten. Karten werden aus dem Vorlagenpool mit Zurücklegen gezogen. In der Planungsphase kann die Hand bereits angetippt und vollständig gelesen werden.

## Spielen über Open Party Lab

Das Paket wird lokal über `config/known-games.json` und `npm run games:sync-local` geladen. Im Host-Lobby wählt jede Person eine Klasse; Mehrfachauswahl einer Klasse ist erlaubt.

## Paket-Entrypoints

- `@open-party-lab/game-dungeon-party/manifest`
- `@open-party-lab/game-dungeon-party/protocol`
- `@open-party-lab/game-dungeon-party/server`
- `@open-party-lab/game-dungeon-party/host`
- `@open-party-lab/game-dungeon-party/controller`

## Entwicklung

```bash
npm run typecheck
npm run build
```

Die vollständige Spiel- und Umsetzungsplanung liegt in [`docs/game-design.md`](docs/game-design.md). Das Spiel ist im öffentlichen Repository [Hartwich/dungeon-party](https://github.com/Hartwich/dungeon-party) verfügbar.
