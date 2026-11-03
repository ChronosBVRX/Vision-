# Vision+ 2.5.200 — «Instalar en TV» ya no se queda en «Comprobando…»

**Fecha:** 30 de octubre de 2026  
**Versión:** 2.5.200 / vc440  
**Launcher:** 1.3.15 / vc38

## Qué cambia

- **Progreso real en el diálogo**: al conectar con la tele ya se ve qué está pasando: «Conectado con B866V2. Preparando Vision+…», «Buscando la última Vision+…», «Descargando Vision+… 42 %» y «Instalando…». Antes el mensaje se quedaba en «Comprobando B866V2…» durante toda la descarga y parecía colgado.
- **La app viaja desde el teléfono**: si la tele no tiene Vision+ o tiene una versión más vieja, se instala la **copia que ya está en el teléfono** por ADB. Ya no se bajan 97 MB del release para instalar la misma versión; solo se usa el release si la copia local falla o si la tele necesita una versión más nueva.
- **El diálogo deja de ser confuso**: mientras instala se oculta el campo de dirección y el botón «Conectar» (ya no hay un botón gris que parezca roto), el título cambia a «Preparando la tele» y el botón de cierre dice **«Cancelar instalación»**.
- **La descarga de Vision+ Home no se queda esperando**: si el release tarda más de 30 s, se usa la copia cacheada o la incluida en la app.
- **Se mantiene la corrección de Android 8/9 de la 2.5.199**: se envía la llave pública, la tele muestra **«¿Permitir depuración?»**, se espera hasta 90 s, se apaga el verificador de instalaciones por ADB y se manejan los Inicios de respaldo sin desactivar apps protegidas.
- Todo lo de la 2.5.198 sigue incluido: **Vision+ Play v2** y el **launcher cinematográfico 1.3.15**, con el **modo persistente del Inicio**: un servicio de accesibilidad devuelve Vision+ Home al frente si Google TV abre su launcher. Se activa en Ajustes → Inicio → Modo persistente (Android exige conceder Accesibilidad una vez); después se mantiene tras reinicios. También incluye las entradas compactas con TV en vivo sin sintonizador.

## Pruebas

- Android: **1,148 tests** en verde (incluye `TvAdbLegacyAuthTest`, que levanta un servidor ADB falso y demuestra que se envía la llave pública).
- Launcher: **183 tests** en verde.
- Backend: **570 tests** en verde (sin cambios de servidor).

## Verificación

- APK release R8 con motor X4K inyectado (`classes3/4/5.dex`).
- Firma histórica `9593cb0d…` en la app y en el launcher.
- `VisionPlus.apk`: **97,637,875 B**, SHA-256 `e390ed9e63d613033f7e966e1e283fc4a9772063550edf6eba706114475cd3d6`.
- `VisionPlusHome.apk` (1.3.15): **2,824,059 B**, SHA-256 `caebd556c1a7c2cc11354b53f69a2a08a8e80242389821f83a029dcada0e3ba9`.

## Pendiente

- Reintentar la instalación en el **B866V2** con esta build y confirmar que ahora se ve el progreso y que termina.
- Prueba física en una tele Android 8/9 real (aceptar el aviso, reiniciar y confirmar que Vision+ Home sigue siendo el Inicio).

Actualiza desde el aviso de la app (o Ajustes → «Buscar actualizaciones»).

— ChronosBVRX
