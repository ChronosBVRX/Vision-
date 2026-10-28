# Vision+ 2.5.175 — teles Android 8/9 sin código, sin PC y sin tener nada instalado

## Lo nuevo de esta versión

- **Ajustes → Instalar en TV** ya no depende de que la tele tenga Vision+ para conocer su dirección. Antes, en Android 8/9, el flujo pedía «vincular el control remoto» — que exige tener la app ya instalada en el televisor (la pescadilla que se mordía la cola). Ahora:
  - **«Buscar teles Android 8/9»**: el teléfono busca en tu Wi-Fi los anuncios de Android TV y las direcciones con el puerto **5555** abierto, y te las lista con su nombre. Tocas la tuya y listo.
  - **Dirección manual**: si la tele no se anuncia (otra red, VLAN o firmware raro), escribes `192.168.1.x` y pulsas **Conectar**.
  - En la tele solo hay que encender **«ADB por red»** (Ajustes → Sistema → Información → 7 toques a «Versión» → Desarrollador). El televisor mostrará **«¿Permitir depuración?»**: acéptalo con el mando; la app espera hasta 90 s a que lo hagas.
  - Después hace lo mismo de siempre: instala la **última Vision+** y el **último Vision+ Home**, deja Home como **Inicio** y guarda el Inicio original para **Restaurar**.
- **Sin falsos positivos propios**: el barrido excluye todas las direcciones del teléfono, así que no se lista a sí mismo.
- **Una sola búsqueda a la vez**: la búsqueda con código (Android 11) ya no puede terminar después y tapar la lista de teles Android 8/9; al empezar una acción se cancela la anterior.

## Para las teles Android 11 o más

- Se mantiene el emparejamiento con el **código de 6 dígitos** de «Depuración inalámbrica», con la misma instalación automática de Vision+ + Home y el Inicio.

## Todo lo que llega desde la estable anterior

- Se mantiene todo lo de la **2.5.174/2.5.173/2.5.172**: la pestaña **Instalar en TV**, las tres pestañas de Ajustes a la vista, la instalación con `pm install -r`, la descarga del release oficial y la guardia que **no toca el Inicio** si la tele no tiene Vision+ y no se pudo instalar.
- Y lo de la 2.5.171/2.5.170/2.5.169: pestañas y plantillas del launcher, TV en vivo al primer intento, D-pad completo, «Omitir intro» y minuto exacto desde el móvil, perfiles, continuidad y subtítulos.

## Pruebas

**1006 pruebas Android + 153 del launcher** en verde. Guardianes nuevos: red /24 del teléfono, direcciones privadas, normalización de la dirección escrita, unión de lo que anuncia Android TV con lo que responde el puerto 5555, barrido real contra un socket local, exclusión de las direcciones propias y el cableado del flujo en la interfaz (incluida la cancelación de la búsqueda anterior). Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3).

Actualiza desde el aviso de la app (o Ajustes → «Buscar actualizaciones»).

— ChronosBVRX
