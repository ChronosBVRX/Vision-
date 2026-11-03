# Vision+ 2.5.243 — BETA: conexión ADB arreglada

**Canal de pruebas.** Actívalo en **Ajustes → Actualizaciones → Canal de pruebas (beta)** y pulsa
**Buscar actualizaciones**.

## Ya no se queda en «conectado»

- La conexión ADB usa ahora **Kadb**, el mismo cliente que la aplicación original.
- En la TCL Android 9, el cliente anterior cerraba la conexión después de dos comandos; por eso
  se quedaba en «conectado» y no seguía. Kadb mantiene el shell y completa identificación,
  lectura de apps, limpieza, instalación y cambio de Inicio.
- Usa **la misma llave** que la tele ya autorizó: no hay que aceptar otro permiso.
- Android 9 usa el shell clásico; Android 11+ usa shell v2. La app elige solo.

## Para ver qué pasa

- **Registro en vivo** con hora, último paso y segundos sin respuesta.
- Botón **Copiar** para enviar el registro si algo falla.
- El registro ya no se queda en 16 líneas: guarda 200 pasos.

## Lo demás sigue igual

- Limpieza por grupos y apps individuales, apps opcionales y apps de otras listas.
- Espacio, Revisar mi tele y Anuncios por Private DNS.
- Restaurar apps, Inicio y DNS.
- 335 listas por modelo, sin desinstalar nada.
- Misma firma: se instala encima sin perder datos.

— ChronosBVRX
