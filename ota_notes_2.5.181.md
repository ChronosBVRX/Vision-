# Vision+ 2.5.181 (pruebas) — barra superior más limpia: «En vivo» agrupa TV y Deportes

**Canal beta. No es la OTA estable.** La barra superior de la tele deja de tener una pestaña
para TV en vivo y otra para Deportes: ahora hay un solo destino **«En vivo»** que abre un menú
desplegable con las dos secciones.

## Qué cambia

- **App de TV:** la barra superior queda en
  **Inicio · En vivo · Juegos · Películas · Series · Mi lista**.
- Al pulsar **En vivo** se abre un panel debajo con:
  - **TV en vivo** — canales y guía.
  - **Deportes en vivo** — agenda y partidos.
- La opción activa lleva un check; se navega con Arriba/Abajo y se elige con OK.
- Atrás o tocar fuera cierra el panel sin cambiar de sección.
- **Vision+ Home (launcher):** la misma agrupación en su barra superior; si el usuario ocultó
  una de las dos pestañas, la otra se muestra sola (no se inventan puertas vacías).
- Se conserva el acceso **Juegos** de la 2.5.180 y todo el contenido de Vision+ Play y del
  control remoto «Ahora en la tele».

## Version

- App **2.5.181 / vc421**.
- Launcher **1.3.2 / vc25**.

## Evidencia

- Android: **1074 tests**, 0 fallos.
- Launcher: **155 tests**, 0 fallos.
- Release R8 compilado y alineado; motor inyectado en la app.
- Firma histórica `9593cb0d…` en app y launcher.

## Cómo probar

1. Instalar la app y, si se quiere la barra del launcher, el nuevo `VisionPlusHome.apk`.
2. En la tele, abrir **Vision+**.
3. En la barra superior, enfocar **En vivo** y pulsar OK.
4. Elegir **TV en vivo** o **Deportes en vivo**; el panel se cierra y abre la sección.
5. Repetir desde el launcher para comprobar la misma agrupación.

— ChronosBVRX
