## Novedades de Vision+ v2.5.19

### 📺 Corrección Crítica de Reproducción en TV en Vivo
- **Resolución de pantalla negra / carga en canales en vivo**: Se corrigió el flujo de inicialización del motor nativo (Titan/Ranger) para asegurar que la lista de servidores de distribución (SLB CDN entries) y parámetros de reproducción se entreguen siempre al iniciar cualquier canal en vivo.
- **Respaldo dinámico en reproducción nativa**: Si los datos CDN no llegan por Intent, el reproductor ahora los recupera de forma transparente desde el almacenamiento seguro local antes de inicializar la señal.
- **Blindaje de superficie de video en Smart TV**: Se eliminó la manipulación de opacidad/transparencia en la capa de video (`SurfaceView`), garantizando que la señal se dibuje inmediatamente sin dejar la pantalla en negro.
- **Detección inteligente de primer cuadro en emisiones en vivo**: Se mejoró la comprobación de reproducción en transmisiones en tiempo real donde la posición de tiempo reportada es cero o continua, ocultando la pantalla de carga a tiempo y evitando falsos errores de conexión.
- **Protección ante renovaciones concurrentes de sesión**: Manejo seguro del token cuando múltiples llamadas solicitan canales o catálogo simultáneamente, evitando bloqueos por límite de peticiones.
- **Selector de canales de fútbol**: Plena compatibilidad con la selección interactiva de canales por partido introducida en v2.5.18.
