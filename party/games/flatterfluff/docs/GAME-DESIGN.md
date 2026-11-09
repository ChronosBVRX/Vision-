# Flatterfluff – Spielkonzept

## Elevator Pitch

Über einer sonnigen Zuckerprärie geraten geflügelte Marshmallow-Wesen außer Rand und Band. Bis zu sechs Spieler teilen sich den großen Bildschirm, steuern aber jeweils ein eigenes farbiges Fadenkreuz über ihr Telefon. Präzision, Munitionsmanagement und kurze Treffer-Serien entscheiden über die Rangliste.

## Modi

### Zeitjagd

Der Host wählt 45 bis 180 Sekunden. Nach Ablauf gewinnt die höchste Punktzahl. Für eine Standardrunde sind 90 Sekunden vorgesehen.

### Endlos

Ziele und Munitionskisten erscheinen ohne Zeitlimit. Die Runde läuft, bis der Host sie beendet. Der Modus eignet sich für freies Training, jüngere Gruppen und offene Party-Stationen.

## Zieltypen

| Ziel | Verhalten | Trefferpunkte | Grundwert |
| --- | --- | ---: | ---: |
| Flitzfluff | klein, schnell, Aviator-Brille | 1 | 180 |
| Flatterfluff | mittelgroß, gut lesbar | 1 | 110 |
| Karamell-Captain | groß, langsam, gepanzert | 2 | 320 |
| Munitionskiste | langsam in Bodennähe | 1 | volles Magazin |

Entferntere Flugbahnen erhalten einen kleinen Wertbonus. Treffer innerhalb von 2,6 Sekunden bauen eine Serie auf; bis zu fünf Serienstufen erhöhen den Zielwert.

Die Flugziele bleiben bewusst kompakt. Ihre serverseitig aufgelösten Flugbahnen halten ungefähr eine halbe sichtbare Zielbreite Abstand, damit sie sich höchstens etwa zur Hälfte verdecken und kein Ziel vollständig hinter einem anderen verschwindet.

Große Ziele werden stärker verkleinert als kleine Ziele; auch Munitionskisten bleiben kompakt. Alle Ziele erscheinen zunächst mit 12 Prozent ihrer Endgröße und wachsen über 5,5 Sekunden. Solange sie klein sind, schrumpft ihre Trefferfläche mit und ihr Punktwert steigt entsprechend an.

## Steuerung

- Virtueller Stick: Fadenkreuz bewegen
- Feuer: einen Schuss abgeben
- Automatisches Nachladen: startet beim letzten Schuss oder beim Abzug mit leerem Magazin
- Zielen bleibt während der 1,9 Sekunden langen Nachladephase ohne Unterbrechung aktiv

Der vergrößerte Stick nutzt innen feine Steuerung und eine progressive Kennlinie, die zum Außenrand hin schneller reagiert. Der Controller zeigt während des Spiels ausschließlich Stick und Feuerknopf; Titel, Abmelden- und Vollbild-Chrome werden ausgeblendet.

## HUD

- Holztafel links: Zeit oder Endlos-Symbol
- Logo mittig
- Rangliste rechts
- Patronen und individuelle Punktzahl unten
- farbiges, beschriftetes Fadenkreuz pro Spieler
- Zuckerwolke und schwebender Punktwert bei Treffern
- kompakte Zeit- und Punktetafeln ohne darüberliegende Raumcode-Anzeige

## Sounddesign

- Schuss: kurzer gefilterter Noise-Impuls mit tiefem Körper
- normaler Treffer: aufsteigende helle Doppelnote
- Rüstung: metallischer kurzer Ton
- leeres Magazin: trockener Klick
- Nachladen: eigene dreistufige mechanische Klangfolge mit Abschlussbestätigung
- Controller-Haptik: kurzer Vibrationsimpuls beim Schießen, sofern das Gerät die Vibrations-API unterstützt
- Munitionskiste: dreistufiger Dur-Akkord
- Musik: 16-taktiger, gemäßigter Country-Loop mit gezupft wirkender Dreieckswellen-Melodie, Bass, leichter Begleitung und zurückhaltendem Schlagzeug

## Visuelles System

- handgemalte Comic-Arcade-Optik
- warme Karamell-, Creme- und Messingtöne
- staubiges Türkis und Salbeigrün in der Landschaft
- dicke Espresso-Konturen im Vordergrund
- weichere atmosphärische Ferne für gute Zielerkennung
- keine harten Trefferfolgen; Figuren verschwinden in harmlosen Zuckerwolken

## ImageGen-Asset-Pass

Built-in ImageGen wurde in vier Schritten verwendet:

1. vollständiger 16:9-Host-Screen als Designreferenz;
2. saubere Zuckerprärie ohne Figuren und HUD;
3. drei Figuren auf einfarbigem Chroma-Key-Hintergrund;
4. Munitionskiste, Patronenfächer und Zuckerwolke auf Chroma-Key.

Die beiden Chroma-Key-Bilder werden mit `scripts/slice_chroma_assets.py` in Einzelassets zerlegt. Der Slicer verwendet keine starren Drittelgrenzen: Rund um jede Sollgrenze sucht er die nächstgelegene vertikale Linie, deren gesamte Höhe ausschließlich aus Pink-Key-Pixeln besteht. Fehlt eine vollständig freie Schnittlinie, bricht der Vorgang mit einer Diagnose ab, statt ein Motiv zu beschädigen. Erst nach dieser Prüfung wird der Key entfernt und das Einzelasset an seiner Alpha-Grenze zugeschnitten.
