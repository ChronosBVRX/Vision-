# Vision+ 2.5.177 — Android 8/9 por dirección, sin búsquedas que fallen

## Lo que cambia

- **Se retiró la búsqueda automática de teles** (anuncios mDNS + barrido del puerto 5555). En redes reales no era fiable y confundía más de lo que ayudaba.
- **Se queda lo que sí funcionaba**: en **Ajustes → Instalar en TV** está la función de siempre — bajar la última Vision+ y el último Vision+ Home, instalarlos en el televisor y **dejar Vision+ Home como Inicio** (con «Restaurar» por tele). En Android 11 o más se sigue emparejando con el **código de 6 dígitos**.
- **Android 8/9**: como no tienen código de emparejamiento, ahora solo se escribe **la dirección del televisor** (Ajustes → Red, por ejemplo `192.168.1.50`) con **«ADB por red»** encendido, y se pulsa **Conectar**. Es la misma función de Inicio, pero entrando por dirección.
- **Diagnóstico claro antes de esperar**: la app comprueba el **puerto 5555** y, si está cerrado, lo dice al momento con los pasos exactos (activar «ADB por red», revisar la IP y la Wi-Fi). Si el puerto está abierto pero falta autorizar, explica que hay que aceptar **«¿Permitir depuración?»** con el mando y ofrece **«Reintentar conexión»** con la misma dirección.
- **Sin mensajes falsos**: cerrar el diálogo mientras conecta ya no muestra errores; la cancelación se trata como cancelación y se limpian los detalles.

## Requisitos en la tele Android 8/9

1. Misma Wi-Fi que el teléfono.
2. Ajustes → Sistema → Información → 7 toques a «Versión» → Desarrollador → **«ADB por red»** encendido.
3. Ver la IP del televisor en Ajustes → Red y escribirla en la app.
4. Aceptar **«¿Permitir depuración?»** con el mando (marca «Permitir siempre» si aparece).

## Todo lo que se mantiene

- La pestaña **Instalar en TV** con las tres pestañas de Ajustes a la vista, la instalación con `pm install -r`, la descarga del release oficial (`version.json`) y la guardia que **no toca el Inicio** si la tele no tiene Vision+ y no se pudo instalar.
- Lo de la 2.5.171/2.5.170/2.5.169: pestañas y plantillas del launcher, TV en vivo al primer intento, D-pad completo, «Omitir intro» y minuto exacto desde el móvil, perfiles, continuidad y subtítulos.

## Pruebas

**1002 pruebas Android + 153 del launcher** en verde. Guardianes nuevos: la búsqueda automática ya no existe, la dirección escrita se normaliza, se comprueba el puerto 5555 contra un socket real, el diagnóstico de puerto cerrado, la cancelación no se reporta como fallo y «Reintentar conexión». Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3).

Actualiza desde el aviso de la app (o Ajustes → «Buscar actualizaciones»).

— ChronosBVRX
