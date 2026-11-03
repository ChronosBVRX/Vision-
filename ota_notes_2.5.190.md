# Vision+ 2.5.190 — OK largo en toda la app + launcher más listo

**Beta de pruebas.** Incluye lo último del árbol de trabajo: el menú contextual de las tarjetas en la app (tele y móvil), el Atrás del launcher a la barra superior y el actualizador OTA interno del launcher.

## OK largo en la app principal
- **Mantén OK (o el dedo) sobre cualquier póster** del Inicio, secciones, categorías, Mi lista, buscador, descargas o ficha: se abre un menú con **Ver ficha**, **Añadir/Quitar de favoritos** y **Añadir/Quitar de Ver más tarde**, sin salir de donde estás mirando.
- Funciona en **Smart TV y en el móvil**. En el móvil también se puede mantener pulsado un canal de **TV en vivo** para ponerlo o quitarlo de favoritos (la tarjeta muestra una estrella).
- El menú se cierra con Atrás, actualiza Mi lista y sincroniza favoritos/Ver más tarde.
- El KeyUp de la pulsación que abrió el menú no activa nada, aunque sigas apretando mientras lo lees.

## Launcher (Vision+ Home 1.3.6)
- **El OK largo ya no abre la app**: el menú contextual se queda abierto y la primera pulsación dentro de él funciona a la primera.
- **Atrás sube el foco a la barra superior** (pestaña activa); el segundo Atrás, dentro de 2.5 s, vuelve a Inicio. Si el mando ya está en la barra, Atrás vuelve a Inicio de una vez.
- **Actualizador OTA interno**: el launcher comprueba el release oficial, lee la versión real del APK publicado y **solo ofrece actualizar si es más nueva** que la instalada. Si ya estás al día, el botón busca otra vez en vez de instalar; nunca se degrada el equipo. El aviso se puede posponer y vuelve a aparecer cuando salga otra versión.

## Verificación
- **1123 pruebas Android + 168 del launcher** en verde.
- Probado en el Chromecast HD: menú de OK largo que se queda abierto, primer OK que actúa, Atrás → barra → Inicio, menú de póster con favoritos/Ver más tarde y la comprobación OTA real (release 1.3.4/27 frente al launcher instalado 1.3.5/28 → «al día», sin abrir el instalador).

— ChronosBVRX
