# Vision+ 2.6.9 — Sala Smart TV, D-pad y portadas definitivas

Actualización estable sobre la 2.6.8. Mantiene reproducción, catálogo, perfiles, descargas y ajustes.

## Sala de Juegos unificada

- La pantalla previa de cada juego reúne en un solo lugar:
  - **QR y código** para entrar desde el celular.
  - La **portada temática** del juego.
  - Los **packs de prompts, tiempos y configuración** de la partida.
  - Las **reglas curadas**, claras y en español.
  - La lista de jugadores y el botón para empezar.
- El catálogo de la tele muestra portadas en lugar de iconos genéricos.
- **D-pad**: las flechas del mando mueven el foco por tarjetas, opciones, reglas y botones; OK/Enter activa. El WebView recibe el foco para que el mando funcione dentro de la interfaz del juego. Atrás sale de la sala.

## Portadas nuevas

- Se regeneraron las **13 portadas** con inteligencia artificial local (ComfyUI + Juggernaut XL), siguiendo la temática real de cada juego.
- Ahora la selección se siente como una colección premium: Banterbolt, ¿Le atinaste? Bien., El Comisionado Merk, Pantomima, Dibuja y adivina, Adivina cuánto, Impostor, Palabra prohibida, Cacería de sombras, Tiro al blanco, Arena mágica, Mesa de cartas y Mazmorra party.

## Optimización

- En televisores y equipos de 2 GB se activa el modo ligero: 30 FPS, menos efectos, `prefers-reduced-motion`, resolución interna más ligera y prioridad de render del WebView.
- La app evita animaciones costosas en la selección de juegos.

## Pruebas

- Android: **1,219 pruebas**, 0 fallos, 3 omitidas.
- Backend: **538 pruebas**.
- Prueba automática de los **13 juegos** en español.
- APK con firma histórica `9593cb0d…` y motor X4K dentro.

## Cómo actualizar

Desde **Ajustes → Buscar actualizaciones**, o instalando el APK de este release encima de la versión actual. No borra datos, perfiles, favoritos, historial, descargas ni ajustes.

— ChronosBVRX
