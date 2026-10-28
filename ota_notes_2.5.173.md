# Vision+ 2.5.173 — «Instalar en TV» desde el teléfono

## Lo nuevo de esta versión

- **Ajustes → pestaña «Instalar en TV»**:
  - El teléfono deja la tele lista **sin PC y sin descargar nada a mano**: baja del release oficial la **última Vision+** y el **último Vision+ Home**, los instala por **ADB inalámbrico**, fija Vision+ Home como **Inicio** y solo retira el launcher de fábrica si sigue robándose la pantalla.
  - Un botón por televisor: **«Instalar Vision+ y su Inicio»**. Si la tele ya tenía una versión anterior, la actualiza; nunca instala una versión más vieja que la que ya tiene (por ejemplo, una beta).
  - **Guardia**: si la tele no tiene Vision+ y no se pudo instalar, **no se toca su Inicio** — nunca queda una pantalla sin app que abrir. Cada tele conserva su Inicio original y lo que se le retiró, con **Restaurar**.
  - La fila de cada tele muestra lo que tiene instalado: **«Vision+ 2.5.173 · Home 1.3.1 en la tele»**.
  - Sin red, usa la copia que ya tenía el teléfono o la que viaja dentro de la app; el APK del launcher **1.3.1** va incluido como respaldo.
  - Las tres pestañas de Ajustes (**Reproducción · Sistema · Instalar en TV**) quedan a la vista sin desplazar la barra.
- **Corrección de instalación**: se usa `pm install -r`, así una tele con una versión anterior **sí se actualiza** (antes el sistema respondía «ya existe» y se quedaba la vieja). El APK grande de Vision+ se transmite por ADB por bloques, sin cargar 82 MB en la memoria del teléfono.

## Requisitos en la tele

- Depuración inalámbrica encendida: Ajustes → Sistema → Información → pulsar 7 veces «Versión» → Desarrollador → Depuración inalámbrica → «Emparejar dispositivo con código». En teles Android 9/10, activar «ADB por red».
- La descarga de Vision+ son ~82 MB: la primera instalación puede tardar unos minutos según el Wi-Fi.

## Todo lo que llega desde la estable anterior

- Se mantiene todo lo de la **2.5.171 + Home 1.3.1**: pestañas con orden/visibilidad, las 4 plantillas de Inicio y la limpieza del diálogo placebo de intro/outro.
- Y lo de las 2.5.170/2.5.169: TV en vivo al primer intento, el canal conserva el menú desde el que lo abriste, D-pad completo en la tele y en el launcher, «Omitir intro» y elegir minuto desde el móvil, perfiles, continuidad entre equipos y subtítulos del proveedor.

## Pruebas

**999 pruebas Android + 153 del launcher** en verde. Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3); Vision+ Home 1.3.1 firmado con la misma llave. Guardianes nuevos: descarga del release (`version.json` y los dos assets), `pm install -r`, transmisión del APK grande, comparación de versiones sin downgrade, caché con vencimiento, lectura de `versionName` del televisor, la pestaña propia de Ajustes y que las tres pestañas quepan sin desplazar.

Actualiza desde el aviso de la app (o Ajustes → «Buscar actualizaciones»). El launcher se actualiza desde su Centro de control o desde Ajustes → Sistema → Vision+ Home.

— ChronosBVRX
