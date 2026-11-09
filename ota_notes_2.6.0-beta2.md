# Vision+ 2.6.0-beta2 — Perfiles con imágenes y reproductor más limpio

**Esta es una beta de pruebas.** No cambia la versión estable: `/releases/latest` sigue en 2.5.245 y se instala encima de la versión actual sin perder datos, perfiles, descargas, favoritos ni ajustes.

## Perfiles

- Al **crear o renombrar** un perfil aparece el **teclado del sistema** de la tele o del teléfono para escribir el nombre. Ya no se crea un «Perfil N» automático sin preguntar.
- Cada perfil puede llevar una **imagen de cine, series o anime**: hay 24 imágenes populares precargadas (8 de cine, 8 de series y 8 de anime), organizadas por pestañas.
- También se puede dejar la opción **«Inicial»**, que conserva el avatar con la letra y el color de siempre.
- La imagen elegida se ve en la lista de perfiles, en el móvil y en el chip del perfil de la barra superior de la tele.
- Los perfiles que ya existían siguen funcionando sin cambios.

## Reproductor

- El **título de la película o serie** del menú de reproducción ya no se corta en teles con overscan o resoluciones bajas: ahora puede ocupar dos líneas, se recorta con puntos suspensivos si hace falta y tiene un margen de seguridad contra el borde.
- Las barras superior e inferior son **más discretas**: usan degradados suaves en vez de franjas opacas, dejan libre el margen de la tele y los controles son más compactos.
- Los botones, la barra de avance y los textos ocupan menos espacio para que el video se vea más limpio.

## Descargas portátiles

Se mantiene todo lo de la beta anterior:

- Las descargas nuevas pueden viajar en una **memoria USB** y abrirse en otra TV con Vision+ aunque no tenga internet.
- La primera descarga de una USB genera un **PIN de 4 dígitos**; las siguientes de la misma memoria lo reutilizan.
- En la TV de destino aparece **Biblioteca portátil · PIN requerido** y, al escribir el PIN, reproduce desde la USB.
- La TV que descargó puede estar apagada; la TV de destino no necesita internet para validar el PIN.

## Cómo probar esta beta

1. Instala el APK en el teléfono y/o en la tele.
2. Entra a **Ajustes → Perfiles** y crea un perfil: debe abrirse el teclado del sistema. Escribe el nombre, elige una imagen y guarda.
3. Cambia la imagen de un perfil existente y comprueba que también se actualiza en el chip de la barra superior.
4. Abre una película o un capítulo y pulsa OK en el mando: revisa que el título se vea completo (o con puntos suspensivos) y que las barras superior e inferior no tapen demasiado el video. Si puedes, pruébalo en una tele de 720p.
5. Para las descargas portátiles, repite el flujo de la beta anterior: descarga a USB en una TV, llévala a otra sin internet, escribe el PIN y reproduce.

## Límites de esta beta

- Las descargas portátiles solo aplican a **descargas nuevas**; las `.vpack` antiguas siguen atadas a su TV.
- El PIN de 4 dígitos es una protección de acceso casual. El contenido sigue cifrado y no hay video en claro en la USB.
- La TV de destino debe tener esta beta o una versión posterior.
- La USB debe ser almacenamiento extraíble normal (FAT32/exFAT), no almacenamiento interno adoptado.
- Sigue pendiente la prueba física con una USB en dos TVs y la prueba del teclado y el menú de reproducción en una tele real.

## Estado técnico

- 1,303 pruebas Android en verde (3 omitidas).
- Incluye el motor X4K dentro del APK, la firma histórica de siempre y `versionCode` 487.
- La estable `/releases/latest` permanece en 2.5.245.

— ChronosBVRX
