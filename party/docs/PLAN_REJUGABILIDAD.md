# Plan de rejugabilidad — 10 sesiones por juego

Fecha: 2026-11-06.

## Criterio

No se trata de llenar los juegos de texto. Cada juego debe tener suficiente
variedad para que **10 sesiones** se sientan distintas, con contenido acorde a su
temática y sin depender de repetir el mismo chiste o la misma pregunta.

Hay dos tipos de rejugabilidad:

- **Contenido finito** (quiz, palabras, cartas, prompts): necesita un banco
  grande. Para Cifras salvajes, una sesión consume 40 preguntas; 10 sesiones sin
  repetir requieren **400**.
- **Contenido social/procedural** (Merk, Sombra fugitiva, Baraja brava, Dulce
  puntería): la variación viene de jugadores, mapas, roles o reglas. Aun así se
  amplían cartas, escenarios y modos.

## Objetivos por juego

| Juego | Consumo por sesión | Objetivo para 10 sesiones | Estado |
|---|---|---|---|
| **Banterbolt** | 13–25 prompts | 250 prompts (≈60 por pack) | **✅ 250** (63 México, 63 cine, 63 after dark, 61 mixto) |
| **Cifras salvajes** | 40 preguntas | 400 preguntas (100 por categoría) | **✅ 400** (100 por categoría), tono de sobremesa |
| **Mímica en llamas** | 10–20 términos | 250 términos | **✅ 250**, todos en español mexicano |
| **Garabato mental** | 10–20 palabras | 600 palabras | **✅ 600**, todos en español mexicano |
| **El Infiltrado** | 20–40 palabras | 500 palabras + pistas | **✅ 500** (400 familiar + 100 18+), 100% español mexicano |
| **Palabra maldita** | 10–20 tarjetas | 240 tarjetas | **✅ 240**, todas en español mexicano y con tono de fiesta |
| **¿Le atinaste? Bien.** | 10–20 espectros | 150 espectros | **✅ 154** (94 base + 60 after dark) |
| **Hexagonia** | 4–8 cartas | 40 cartas/acciones y más arenas | **20 cartas**, ya en español y con tono de fiesta |
| **Héroes del sótano** | 5–10 cartas + 5 salas | 40 cartas y 20 salas | **20 cartas, 13 salas**, ya en español y con tono de fiesta |
| **Dulce puntería** | 1 ronda | 6 tipos de objetivo y 2 modos | **✅ 6 tipos** (scout, flier, captain, jumbo, golden, crate) y 2 modos |
| **Sombra fugitiva** | 1 partida | mapas procedurales + IA | ✅ procedural; textos de rol, turno y avistamiento ya en español |
| **Baraja brava** | 1 ruleset | 12 rulesets | ✅ 12 |
| **El Comisionado Merk** | 1 partida social | roles y escenarios suficientes | ✅ social |

## Lotes

### Lote 1 — Cifras salvajes (✅ cerrado)

- ✅ Paquete 1: 10 rondas / 40 preguntas originales de México.
- ✅ Paquete 2: 10 rondas más / 40 preguntas de animales, música, ciudades, comida, historia, ciencia, deportes, cine, trabajo y tecnología.
- ✅ Paquete 3: 10 rondas más / 40 preguntas de fiestas, turismo, escuela, movilidad, naturaleza, espacio, antojitos, deporte, entretenimiento y México en el mundo.
- Se retiró el paquete de censo porque era demasiado seco y sonaba a examen. La meta de 400 quedó cerrada con preguntas de fiesta y contexto mexicano.
- ✅ Las 400 preguntas usan tono de sobremesa, apuesta y desmadre; ya no usan el formato de examen.
- Cada pregunta debe tener fuente, respuesta verificable y contexto mexicano.
- No se aceptan preguntas de relleno ni datos inventados.

### Lote 2 — Banterbolt

- ✅ De 102 a **250 prompts**: 63 México, 63 cine, 63 after dark y 61 mixto.
- ✅ Meta de rejugabilidad cumplida para este juego.
- Los prompts deben ser situaciones, no simples listas de palabras.

### Lote 3 — Palabra maldita + El Infiltrado (✅ cerrado)

- Ampliar tarjetas y palabras con comida, lugares, oficios, transporte, familia,
  televisión, música y vida diaria de México.
- Cada entrada debe tener pistas o palabras prohibidas coherentes.
- No copiar barajas comerciales.

### Lote 4 — Mímica + Garabato

- Ampliar términos con acciones, objetos, oficios, animales, comida y cultura
  mexicana.
- Separar familiar y 18+ sin mezclar.

### Lote 5 — Hexagonia + Héroes del sótano + Dulce puntería

- Más cartas, acciones, salas, enemigos y tipos de objetivo.
- Revisar balance después de ampliar; no solo agregar cantidad.

### Lote 6 — ¿Le atinaste? Bien.

- Más ejes mexicanos: familia, trabajo, transporte, televisión, música, comida,
  redes y vida diaria.
- Evitar referencias que caduquen en una semana.

## Reglas de calidad

1. Nada de relleno genérico.
2. Cada dato debe ser verificable o claramente subjetivo.
3. Cada juego conserva su tono y sus reglas.
4. Nada de copiar preguntas, cartas o barajas comerciales.
5. Los lotes se prueban con partidas reales, no solo con `typecheck`.
6. El contenido 18+ nunca se mezcla con el familiar por accidente.

## Audio

- **630 tomas** de narración: 10 variantes por momento en 10 juegos base,
  Banterbolt, Merk, ¿Le atinaste? y selección.
- Voz clonada de referencias mexicanas con Qwen3-TTS Base; rotación por bolsa
  mezclada para no repetir dentro de una sesión.
- Auditoría final: **630/630** con 0 fallas y 0 avisos; WER promedio 0.031 (máximo 0.333).
  Herramientas: `audit_party_audio.py`, `normalize_party_audio.py` y
  `test_party_audio.sh`.
- Pendiente: escucha física con 3 teléfonos.

## Arte visual

- **14 portadas 600×800** (13 juegos + default) y copias Android sincronizadas.
- **7 fondos ambientales 1344×768** para los hosts que no tenían arte raster,
  aplicados como capa suave sin cambiar layout ni hitboxes.
- Se conservan intactos el atlas de Symbol Hunt, el tablero 3D de Hexagonia y
  los hitboxes de Dulce puntería.
- Auditoría: `party/tools/audit_party_art.py` → **41 archivos, 0 fallas**.
- Detalle: `party/docs/ARTE_VISUAL.md`.
