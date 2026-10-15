# Vision+ v2.5.70 — El reproductor ya no se queda mudo (cajas atascadas)

## Qué pasaba
En algunas Smart TV (p. ej. ZTE B866F) la reproducción moría en silencio: el motor
descargaba el video pero nunca arrancaba la imagen, y la app mostraba «No se pudo iniciar
la reproducción» a los 20 s una y otra vez — incluso con títulos que en el teléfono
reproducían perfecto. Podía durar horas y solo se curaba borrando todos los datos de la app.

## Causa raíz (cazada con root en la tele)
El motor LEGADO «Xuper» que vive dentro de la app guardaba una sesión rancia. Al intentar
«rotarla» (auto-login) martillaba el portal compartido de la cuenta y tumbaba la
autorización de la reproducción XTV del mismo equipo — el motor descargaba datos pero el
stream nunca arrancaba. Su configuración capturada renace del código, así que el estado
volvía solo. Se reprodujo y curó de forma controlada quitando y reponiendo ese almacén.

## El arreglo
- **Desarme automático del motor legado**: si su sesión queda rancia y rotar no produce una
  lista nueva, el origen se desarma de forma permanente (bandera local). Nunca más toca el
  portal ni el almacén compartido (gate/Magis intactos).
- **Auto-reparado del reproductor**: si el motor se queda mudo 2 veces seguidas, la app
  rota su carpeta de trabajo, renueva sesión/SLB y desarma el origen legado — sola, sin que
  el usuario borre datos.
- **Sin tormentas de renovación**: «contenido no existe» (100008/100006) ya no se trata
  como sesión caída.
- **Diagnóstico con causa**: los fallos del reproductor ahora registran etapa/modo, y hay
  evento PLAYER_SELF_HEAL, para no volver a adivinar.

## Verificado
- 291 tests Android en verde (nuevos: PlayerRecoveryTest, XuperEngineDisarmTest,
  anti-tormenta en X4KApiSessionTest).
- Prueba física en la TV ZTE B866F (la que estaba atascada): con el archivo envenenado
  restaurado a propósito, la app se auto-desarmó al arrancar y 3/3 reproducciones con video.
