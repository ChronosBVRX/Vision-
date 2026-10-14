# Privacidad

Vision+ funciona en tu aparato y usa un servidor propio (sin terceros ni analítica comercial) solo
para el catálogo y el diagnóstico técnico.

- **No pide datos personales** ni tiene registro de usuario.
- **Qué sale por internet** (solo para el funcionamiento):
  - El **índice del catálogo** (títulos, pósters, búsqueda), la **agenda de fútbol**, la **guía EPG**
    y los enlaces de actualización, desde el servidor propio de Vision+.
  - Las **portadas y los videos** del contenido, desde los CDN del proveedor del catálogo.
  - Las **listas M3U** que elijas (las de iptv-org por defecto, o las que agregues) y los canales que
    abras desde ellas.
- **Diagnóstico (se puede apagar en Ajustes)**:
  - Si la app falla (crash, congelamiento/ANR) o detecta un error técnico (reproducción, descarga,
    catálogo, sesión, motor nativo), envía al servidor propio un reporte **técnico**: versión de la
    app, modelo, versión de Android, RAM, red, almacenamiento y el detalle del error.
  - **Nunca** se envían: tokens de sesión, número de serie, identificador de usuario, títulos que
    viste ni historial de reproducción. El identificador de dispositivo viaja **hasheado (SHA-256)**
    y solo sirve para contar dispositivos distintos.
  - Puedes desactivarlo en **Ajustes → Sistema y Rendimiento → Diagnóstico y Soporte**, y enviar un
    diagnóstico manual con el botón de esa misma sección.
- **Qué se guarda en el teléfono/TV**: el índice descargado (para abrir rápido y funcionar sin red),
  el historial de «Seguir viendo», tus listas, favoritos y las descargas cifradas. Todo se borra al
  desinstalar; las descargas quedan ligadas al chip seguro del aparato (no se pueden copiar).

Preguntas o dudas: abre un issue en el repositorio del proyecto.
