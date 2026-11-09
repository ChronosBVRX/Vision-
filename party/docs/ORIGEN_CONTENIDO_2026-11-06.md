# Origen del contenido de Vision+ Party

Fecha: 2026-11-06. Este documento separa **mecánica**, **contenido heredado** y
**contenido original de Vision+**. No sustituye una auditoría legal; sirve para
saber qué se puede defender como propio y qué arrastra licencia o revisión de
derechos.

## Regla general

- **Motor y contratos**: Open Party Lab, Apache 2.0. Los 13 juegos corren sobre
  esa plataforma.
- **Juegos portados**: la mecánica y una parte variable del contenido vienen del
  juego upstream. La licencia declarada en los paquetes es Apache 2.0, pero sus
  README advierten que assets, listas de palabras y referencias pueden requerir
  revisión separada.
- **Capa Vision+**: nombres, descripciones, reglas curadas, textos de sala,
  portadas generadas localmente, narración Qwen, música/SFX, transición, lobby,
  integración Android/TV y dirección de arte.
- **Contenido original Vision+ dentro de un juego**: hoy el caso más claro es
  **Banterbolt** (`src/content/prompts.ts` lo declara explícitamente). Los demás
  deben tratarse como **adaptados** hasta documentar autoría por archivo.

## Inventario juego por juego

| Juego | Mecánica | Contenido heredado | Contenido original / adaptado por Vision+ | Nota de derechos |
|---|---|---|---|---|
| **Banterbolt** | Port de Banterbolt (The Merkive) sobre OPL | Formato de prompts, votación A/B, ZAP y ronda final | 4 packs en español mexicano (México, cine/series, after dark, mixto), textos de UI, 27 audios, nombre y arte | Verificar atribución de la obra original de The Merkive. Los prompts están marcados como originales de Vision+. |
| **¿Le atinaste? Bien.** | Port estilo Wavelength: espectro, dial, equipos y Undercut | Mecánica de espectro y flujo de equipos | Packs de ejes adaptados a México (core y after dark), textos, UI, 18 audios, arte | Wavelength es referencia de tercero. Mantener nombre/arte propios y revisar si algún eje copia cartas originales. |
| **El Comisionado Merk** | Port estilo Secret Hitler: roles ocultos, baraja 6/11, votos y poderes | Esqueleto de deducción social, proporciones de baraja y poderes | Tema Merk, roles, decretos, textos, UI, 21 audios, arte | No usar arte, logo ni nombres de Secret Hitler. La mecánica puede ser similar; la expresión visual y textual debe seguir siendo propia. |
| **Mímica en llamas** | Charadas de Open Party Lab | **124 términos familiares + 76 adultos** del paquete upstream; el archivo `terms.ts` los declara originales del juego, no de Vision+ | Nombre, descripción, reglas, portada, narración y textos de sala | Los términos no son autoría Vision+. Conservar atribución upstream. |
| **Garabato mental** | Dibujo y adivinanza de Open Party Lab | Lista bilingüe de palabras heredada; categoría adulta ampliada en el upstream | Nombre, descripción, reglas, portada, narración y textos de sala | El README pide revisión de derechos de word lists. |
| **Cifras salvajes** | Quiz de estimación, orden y clasificación de Open Party Lab | Motor y formato heredados | **120 preguntas originales de Vision+** en 30 rondas: México, comida, transporte, deporte, cultura, tradiciones, naturaleza, tecnología, vida diaria y récords; UI, portada y narración propias | Primer lote de localización completado. Ya no conserva el banco alemán. |
| **El Infiltrado** | Deducción social estilo Imposter de Open Party Lab | **288 palabras familiares + 160 adultas** y **448 pistas** heredadas, con restos de alemán/inglés | Nombre, UI, portada, textos de reglas, narración y adaptación al español | Revisión de derechos de palabras/pistas; mantener separación U18/18+. |
| **Palabra maldita** | Explicar términos sin palabras prohibidas, de Open Party Lab | Mazo de tarjetas y listas heredadas | Nombre, UI, portada, textos, narración y adaptación al español | El README del juego pide revisión de derechos del word list. |
| **Sombra fugitiva** | Persecución con movimiento oculto de Open Party Lab | Mapa, red de transporte, roles, reglas y gráficos originales del juego upstream; el README declara que no usa marcas ni arte externo | Nombre, traducción, UI, portada, narración y textos de sala | El contenido es original **del juego upstream**, no de Vision+. Revisar autoría antes de reclamarlo como propio. |
| **Dulce puntería** | Arcade de puntería de Open Party Lab | Objetivos, arte y diseño original del upstream; usó una referencia interna de marshmallow de Chaos-Kommando | Nombre, UI, portada, textos, narración y adaptación | Revisar provenance de los assets del upstream y la referencia interna. |
| **Hexagonia** | Táctica 3D por hexágonos de Open Party Lab | Cartas, acciones, arena y arte del upstream; referencia visual a Emberfall | Nombre, UI, portada, textos, narración y adaptación | Revisar la referencia artística y conservar atribución. |
| **Baraja brava** | Doce juegos de cartas con reglas tradicionales, implementación de Open Party Lab | Reglas tradicionales, implementación, arte e iconos de Symbol Hunt del upstream | Nombre, UI, textos, portada, narración y localización | Los juegos tradicionales no son propiedad de nadie; sí lo es la implementación/arte. Revisar assets. |
| **Héroes del sótano** | Cooperativo de mazmorra de Open Party Lab | Cartas, clases, encuentros, monstruos y arte del upstream; quedan textos en alemán/inglés | Nombre, UI, portada, narración y localización parcial | Completar localización y revisar derechos de arte/contenido. |

## Lo que sí es claramente Vision+

- Nombres nuevos y identidad de los 10 juegos renombrados.
- Descripciones, reglas curadas, textos de sala, metadata de tarjetas y mensajes
  de la app.
- 13 portadas 600×800 generadas localmente con ComfyUI + Juggernaut XL.
- 630 audios de narradora (10 variantes por momento), voz mexicana clonada con
  Qwen y dirección de tono.
- Transición inmersiva, pantalla de “Preparando la sala”, lobby, dock, D-pad y
  flujo de sala bloqueada a un juego.
- Prompts de **Banterbolt** y los packs mexicanos de **¿Le atinaste? Bien.**
  (estos últimos deben documentarse como adaptación si no hay declaración de
  autoría archivo por archivo).

## Riesgos principales antes de una tienda

1. **Cifras salvajes** sigue siendo un quiz alemán traducido; no es contenido
   mexicano original.
2. **Mímica, Garabato, El Infiltrado y Palabra maldita** dependen de listas de
   palabras/prompts heredados. Hay que etiquetar cada archivo con origen y
   licencia.
3. **El Comisionado Merk y ¿Le atinaste? Bien.** son mecánicas inspiradas en
   juegos comerciales. La protección está en el nombre, arte, textos y assets
   propios; no en la mecánica.
4. **Sombra fugitiva, Dulce puntería, Hexagonia, Baraja brava y Héroes del
   sótano** tienen contenido original del upstream, pero no necesariamente
   original de Vision+. No conviene presentarlos como “100% creación Vision+”.
5. Los README de varios juegos advierten que **assets y word lists requieren
   revisión de derechos** antes de distribución pública.

## Resumen ejecutivo

- **Motor**: heredado de Open Party Lab y juegos upstream, Apache 2.0.
- **Contenido de juego**: mayoritariamente heredado y localizado; no es todo
  original de Vision+.
- **Identidad, arte, voz, reglas curadas e integración**: sí es la capa original
  de Vision+.
- **Banterbolt** es el juego con contenido propio más claramente declarado.
- **Cifras salvajes** es el caso más urgente de contenido heredado que todavía
  no se siente mexicano.
