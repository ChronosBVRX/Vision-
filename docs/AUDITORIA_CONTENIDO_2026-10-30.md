# Vision+ Play — Auditoría de contenido y plan editorial

**Fecha:** 30 de octubre de 2026  
**Banco consultado:** VPS `streaming-db`, tabla `trivia_questions`  
**Estado:** diagnóstico y plan de lotes. No se reescribieron los registros en esta fase.

---

## 1. Foto actual

| Paquete | Preguntas |
|---|---:|
| Cine | 1,373 |
| Series | 1,640 |
| Anime | 320 |
| Infantil | 70 |
| Novelas | 62 |
| **Total** | **3,465** |

El banco creció desde la auditoría inicial de 3,125. La cifra de `/v1/game/health` ya reporta 3,465.

### Dificultad

| Dificultad | Preguntas | % |
|---|---:|---:|
| Fácil (1) | 895 | 25.8% |
| Intermedio (2) | 1,663 | 48.0% |
| Difícil (3) | 907 | 26.2% |
| Experto (4) | 0 | 0% |

La distribución está cerca del objetivo 30/40/25/5, pero no hay nivel experto y el bloque intermedio pesa más de lo previsto.

### Integridad estructural

- Registros con prompt o respuesta vacíos: **0**.
- Grupos de prompts idénticos: **6**, pero **395** corresponden a la plantilla genérica “¿De qué título es este póster?”, que no es un duplicado real: cambian las opciones y el arte.
- Duplicado semántico evidente: una sinopsis larga de un show tokusatsu repetida 3 veces.
- La detección de duplicados debe comparar **prompt + opciones + respuesta + arte**, no solo el texto del prompt.

---

## 2. Paquetes y profundidad

### Cine

Volumen suficiente. Falta una auditoría de exactitud, ambigüedad y mezcla de versiones. Hay preguntas de póster/sinopsis que comparten prompt genérico; son válidas si el arte y las opciones son únicos.

### Series

El paquete más grande. Riesgo de sesgo hacia streaming reciente y de preguntas sobre temporadas/versiones que necesitan delimitarse. Revisar duplicados de títulos con acentos o nombres alternativos.

### Anime

320 preguntas. Es el paquete con más riesgo de mezclar anime clásico de TV mexicana con nicho contemporáneo. Separar `mainstream_mx`, `generation_specific` y `fan_favorite` antes de ampliar.

### Infantil

70 preguntas. Es el paquete más débil para una sesión familiar repetida. Necesita un lote propio de animación clásica, personajes, canciones, objetos y programas conocidos por varias generaciones.

### Novelas

62 preguntas. Suficiente para una noche, insuficiente para un grupo recurrente. Requiere telenovelas mexicanas, personajes, actores, frases y producciones internacionales populares en México.

---

## 3. Mazos de fiesta

| Mazo | Actual | Mínimo propuesto | Objetivo |
|---|---:|---:|---:|
| Fibbage | 30 | 100 | 150 |
| Subasta | 26 | 50 | 75 |
| Dibujo | 50 | 100 | 150 |
| Impostor | 42 | 80 | 120 |

Estos mazos son locales y estáticos. No tienen rotación por grupo ni fuente editorial. La prioridad es ampliarlos y añadir un historial de uso por `X-Sync-Code`.

---

## 4. Problemas editoriales detectados

1. **Cero nivel experto.** No hay preguntas etiquetadas como 4.
2. **Infantil y Novelas** tienen menos de 75 registros.
3. **Plantillas genéricas** de póster/sinopsis; el prompt es el mismo, pero el arte/opciones cambian. No son duplicados, pero complican la analítica.
4. **Duplicado semántico** de una sinopsis tokusatsu (3 copias).
5. **Sin fuente de verificación** en el esquema actual.
6. **Sin etiquetas de reconocimiento** (`mainstream_mx`, `generation_specific`, `fan_favorite`, `niche`, `emerging`).
7. **Sin rotación por grupo** en los mazos de fiesta.
8. **Sin medición de dificultad real** por porcentaje de aciertos.

---

## 5. Plan por lotes

### Lote 0 — respaldo

1. Exportar `trivia_questions` a JSON/CSV.
2. Guardar conteos por pack/dificultad antes.
3. Verificar reimportación en base de pruebas.
4. No tocar IDs estables.

### Lote 1 — higiene

- Resolver el duplicado tokusatsu.
- Detectar duplicados por `prompt + options + answer_key + art`.
- Normalizar tildes y títulos alternativos.
- Completar dificultad 4 con candidatos bien conocidos para fanáticos.

### Lote 2 — Infantil

- 100–150 preguntas.
- Animación clásica, personajes, canciones, objetos, programas de TV.
- Validación con adultos y niños.

### Lote 3 — Novelas

- 100–150 preguntas.
- Telenovelas mexicanas, personajes, actores, frases y producciones internacionales populares en México.
- Fuente por pregunta.

### Lote 4 — Anime

- 150–250 preguntas.
- Separar clásicos de TV mexicana, shonen, shojo, deportes, películas y nicho.
- No llenar de referencias que solo conoce un sector.

### Lote 5 — Cine/Series

- Auditoría de exactitud y ambigüedad.
- Reformular preguntas genéricas.
- Añadir etiquetas de generación y reconocimiento.

### Lote 6 — Mazos de fiesta

- Fibbage a 150, con fuente.
- Subasta a 75.
- Dibujo a 150.
- Impostor a 120.
- Rotación por `X-Sync-Code`.

---

## 6. Criterios de aceptación

- 100% con prompt, opciones, respuesta, pack y dificultad.
- 0 duplicados exactos según la huella completa.
- 100% de los nuevos datos con fuente o referencia interna.
- Distribución de dificultad revisada.
- Muestra humana por lote.
- Comparación de conteos antes/después.
- Reversión posible desde el respaldo.

---

## 7. Estado

La base actual es un buen punto de partida, pero **no está lista para declarar la renovación editorial terminada**. Se necesita trabajar por lotes, con respaldo y revisión humana. En esta fase se dejó el diagnóstico y el plan; no se reescribieron los 3,465 registros.
