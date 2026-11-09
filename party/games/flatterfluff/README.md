# Flatterfluff

Flatterfluff ist eine serverautoritativ ausgewertete Arcade-Zieljagd für Open Party Lab. Ein bis sechs Spieler bewegen je ein farbiges Fadenkreuz mit dem virtuellen Stick, feuern über eine große Aktionstaste und laden ihr Sechs-Schuss-Magazin automatisch nach.

![Flatterfluff – frühe Konzeptgrafik der Zuckerprärie](docs/art/host-concept.png)

*Frühe Konzeptgrafik der Zuckerprärie; das aktuelle Controller- und HUD-Layout ist bewusst reduzierter.*

## Spielumfang

- 1–6 Spieler auf einem gemeinsamen Host-Screen
- Zeitjagd von 45–180 Sekunden oder offene Endlosrunde
- drei fliegende Zieltypen mit eigenen Größen, Geschwindigkeiten und Punktwerten
- alle Zieltypen fliegen mit 12 Prozent Größe ein und wachsen langsam mit skalierter Trefferfläche
- gepanzerter Captain mit zwei Trefferpunkten
- Munitionskisten, Serienbonus, Trefferfeedback und Rangliste
- sechs sichtbare Patronen pro Magazin
- eigene Schuss-, Treffer-, Rüstungs-, Leer-, Nachlade- und Pickup-Sounds
- unterbrechungsfreies Zielen beim automatischen Nachladen und optionale Schuss-Haptik auf unterstützten Telefonen
- eigener 16-taktiger Country-Loop über das Musikmodul der Plattform
- Deutsch und Englisch in Host- und Controller-Feedback

## Architektur

Der Controller sendet nur Zielrichtung und Feuerabsicht. Zielbewegung, Trefferprüfung, Munition, automatisches Nachladen, Serien und Punkte werden ausschließlich im Serverpaket berechnet.

Öffentliche Entry-Points:

```text
@open-party-lab/game-flatterfluff/manifest
@open-party-lab/game-flatterfluff/protocol
@open-party-lab/game-flatterfluff/server
@open-party-lab/game-flatterfluff/host
@open-party-lab/game-flatterfluff/controller
```

## Entwicklung

```bash
npm run typecheck
npm run build
```

Danach aus dem Plattform-Repo:

```bash
npm run games:sync-local
npm run typecheck
npm run build
```

## Art und Rechte

Die Produktionsgrafiken wurden für dieses Spiel mit dem eingebauten ImageGen-Workflow erstellt. Als Form- und Materialreferenz diente die lokal vorhandene, projektzugehörige Marshmallow-Konzeptart aus Chaos-Kommando. Es wurden keine externen Marken, Logos oder Stock-Assets verwendet.

Das Designkonzept liegt unter `docs/art/host-concept.png`. Chroma-Key-Quellen werden zur Nachvollziehbarkeit unter `docs/art/` aufbewahrt; die spielbaren Alpha-PNGs liegen unter `public/host/flatterfluff/`.

Einzelassets werden mit `npm run assets:slice` neu erzeugt. Das Skript akzeptiert eine Schnittlinie nur dann, wenn die vollständige vertikale Linie aus Chroma-Key-Hintergrund besteht; dadurch können Flügel oder Props nicht unbemerkt angeschnitten werden.

## Alpha-Hinweise

Balancing, Zieltempo, Trefferflächen und Rundendauer sind bewusst einfach konfiguriert und sollten mit Gruppen unterschiedlicher Displaygröße getestet werden. Die Endlosrunde endet absichtlich nur über die Host-Steuerung.
