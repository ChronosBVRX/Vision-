# Plan de localización completa a español mexicano

Fecha: 2026-11-06.

## Respuesta corta

Sí es posible dejar **todo el contenido visible** en español mexicano, sin
rastros de alemán o inglés. No es una traducción automática: hay que
**reescribir** bancos de preguntas, cartas, pistas y nombres que hoy vienen del
upstream. El código interno, IDs, nombres de paquetes, comentarios técnicos y
avisos de licencia seguirán en su idioma original porque no se muestran al
usuario y cambiarlos rompería el proyecto.

## Criterio de aceptación

1. En modo español no aparece ninguna palabra, frase, nombre de carta, estación,
   categoría o instrucción en alemán o inglés.
2. Los textos no se sienten traducidos: usan contexto mexicano, humor local y
   referencias comprensibles para la mesa.
3. Los datos siguen siendo correctos: no se inventan cifras, reglas ni
   respuestas.
4. Los audios de narradora y la transición siguen siendo originales de Vision+.
5. Los créditos Apache 2.0 y avisos de terceros se conservan.

## Fases

### Fase 1 — Fugas visibles y fallbacks

- Auditar el diccionario `party/content/i18n/es.json` y las cadenas de host,
  controlador y juegos.
- Eliminar fallbacks en alemán/inglés que hoy aparecen cuando falta una entrada.
- Revisar los estados de error, configuración, reglas, botones, tooltips y
  pantallas de resultados.
- Criterio: cero cadenas alemanas/inglesas visibles en una partida completa.

### Fase 2 — Contenido heredado que más se nota

1. **Cifras salvajes**: reemplazar el banco alemán por preguntas de México y
   cultura general en español. Es el caso más urgente.
2. **Héroes del sótano**: reescribir cartas, clases, encuentros y textos que
   todavía tienen alemán/inglés.
3. **El Infiltrado**: reescribir palabras y pistas hacia comida, lugares,
   oficios, cultura y vida mexicana.
4. **Palabra maldita**: rehacer tarjetas y palabras prohibidas con contexto
   mexicano.
5. **Mímica en llamas** y **Garabato mental**: limpiar términos híbridos y
   ampliar con acciones, objetos y referencias locales.

### Fase 3 — Juegos con reglas heredadas

- **Baraja brava**: localizar nombres de variantes, categorías, palos, avisos y
  reglas; conservar los juegos tradicionales pero con nombres claros en español.
- **Hexagonia**: cartas, hechizos, acciones, terreno y mensajes de error.
- **Sombra fugitiva**: estaciones, roles, pistas y textos de la ciudad; mantener
  la mecánica de movimiento oculto.
- **Dulce puntería**: textos de puntería, recarga, objetivos y resultados.

### Fase 4 — Contenido original con contexto mexicano

- Nuevas preguntas de **Cifras salvajes** con geografía, comida, transporte,
  deportes, cine, música, familia y vida diaria de México.
- Nuevas palabras y pistas para **El Infiltrado** y **Palabra maldita**.
- Nuevos términos de mímica y dibujo con acciones y objetos reconocibles.
- Cartas y encuentros de **Héroes del sótano** con humor mexicano, sin copiar
  marcas, personajes ni obras protegidas.
- Revisión de que ningún chiste dependa de una referencia que no entienda una
  mesa mexicana.

### Fase 5 — QA y cierre

- Prueba de las 13 salas en español.
- Capturas de host y controlador en Chromecast/TV.
- Búsqueda automática de caracteres y palabras alemanas/inglesas en contenido
  visible.
- Prueba de audio de narradora y transición.
- Actualización de `AGENTS.md`, notas OTA y avisos de terceros.

## Qué no se va a traducir

- IDs internos (`schaetzorama`, `imposter`, etc.).
- Nombres de archivos, carpetas y APIs.
- Comentarios de código y documentación upstream.
- Licencias, `NOTICE`, créditos y avisos legales.
- Nombres de juegos tradicionales cuando su nombre en español sea ambiguo; se
  puede usar una ficha con nombre comercial en español y aclaración.

## Riesgos

- **Cifras salvajes** tiene 147 preguntas heredadas; reescribirlas requiere
  validar cada respuesta.
- **El Infiltrado** tiene 448 palabras/pistas; es el banco más grande.
- **Baraja brava** tiene 12 reglamentos; no se deben romper las reglas al
  cambiar etiquetas.
- Varios README upstream advierten que las listas de palabras y assets requieren
  revisión de derechos antes de distribución pública.
- No conviene presentar todo como “100% original”: la mecánica sigue siendo
  heredada y Apache 2.0.

## Estado

- **Cifras salvajes**: ✅ primer lote completado (2026-11-06). Se reemplazó el banco
  alemán por **10 rondas × 4 categorías = 40 preguntas originales** de México,
  comida, transporte, deporte, cultura, tradiciones, naturaleza, tecnología,
  vida diaria y récords. Se eliminaron los archivos de asignaciones y textos en
  inglés heredados, y el host/controlador muestran etiquetas en español.
- **Héroes del sótano**: siguiente objetivo.
- **El Infiltrado**, **Palabra maldita**, **Mímica**, **Garabato** y el resto:
  pendientes.

## Orden recomendado

1. Cifras salvajes.
2. Héroes del sótano.
3. El Infiltrado.
4. Palabra maldita.
5. Mímica + Garabato.
6. Baraja brava + Hexagonia + Sombra fugitiva + Dulce puntería.
7. Auditoría final de textos y partida completa.
