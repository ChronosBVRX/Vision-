# Vision+ 2.6.34: Protocolo de licencias FHD y alineación con Xuper Hydra

- **Licencias 1080p FHD alineadas con Hydra**: Se corrige la negociación de licencias con el portal v10:
  - En películas se envía `seriesContentId` igual al `contentId` y `authType` vacío (en lugar de forzar `"0"`), permitiendo que el portal firme la pista en su resolución nativa 1080p sin restringir a 480p.
  - En series se envía `authType = "1"` y la lista de capítulos correspondiente.
- **Resolución sincrónica de columnas del catálogo**: Al abrir un título sin columna en caché, se consulta de forma prioritaria su estante real antes de recurrir a columnas de respaldo.
- **Sincronización SLB en identidad de cortesía**: La solicitud de balanceador CDN (`getSlbInfo`) para firmas VIP respeta el tenant de reproducción (`masmobile`), garantizando concordancia con el token de cortesía.
- **Broker VIP optimizado**: Alineación del servicio en el servidor VPS con las columnas principales de películas (79715) y series (79716).
- Datos y configuración conservados: perfiles, historial y descargas continúan intactos.

— ChronosBVRX
