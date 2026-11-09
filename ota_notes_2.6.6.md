# Vision+ 2.6.6 — Juegos de fiesta completos

Actualización estable sobre la 2.6.5. Mantiene el catálogo, la reproducción, los perfiles y las descargas, y completa la sección de Juegos.

## Juegos

- La pestaña **Juegos** ya tiene **13 juegos disponibles**, con portada propia para cada uno.
- Se sumaron **Pantomima**, **Dibuja y adivina**, **Adivina cuánto**, **Impostor**, **Palabra prohibida**, **Cacería de sombras**, **Tiro al blanco**, **Arena mágica**, **Mesa de cartas** y **Mazmorra party**.
- La selección se ve como **Selección premium**, con tarjetas grandes, imagen, categoría y descripción; se recorre completa con el mando de la tele.
- La tele crea la sala y los teléfonos entran por QR. El juego se abre en pantalla completa y se sale con **Salir** o Atrás.
- La narradora tiene **186 audios** en español: 3 variantes por momento para no repetir la misma toma.

## Optimización

- La app detecta televisiones y equipos con poca memoria o pocos núcleos y activa el **modo ligero**.
- En ese modo baja a **30 FPS**, reduce animaciones y efectos, usa una resolución interna más ligera y da prioridad al WebView.
- La pantalla de Juegos evita animaciones costosas y reutiliza las tarjetas al desplazarse.
- En equipos potentes se conserva la experiencia completa.

## Pruebas

- Android: **1,219 pruebas**, 0 fallos, 3 omitidas.
- Backend: **538 pruebas**.
- Los **13 juegos** inician ronda correctamente en la prueba automática.
- APK final: **87,930,583 B**, 5 DEX, firma histórica `9593cb0d…`.

## Cómo actualizar

Desde **Ajustes → Buscar actualizaciones**, o instalando el APK de este release encima de la versión actual. No borra perfiles, favoritos, historial, descargas ni ajustes.

— ChronosBVRX
