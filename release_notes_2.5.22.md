## Novedades de Vision+ v2.5.22

### ⚡ Arranque Ultrarrápido y Cero Crasheos en Chromecast y Smart TV
- **Fin a los crasheos y congelamientos al abrir la app**: Corregido el problema crítico en Chromecast con Google TV y dispositivos Android TV de hardware modesto (1.5 - 2 GB RAM / 4 núcleos ARM) donde la app se congelaba o cerraba de golpe antes de mostrar la pantalla principal.
- **Splash Screen nativo en Jetpack Compose para TV (`VisionSplash`)**:
  - Se eliminó el uso de `VideoView` (`MediaPlayer` + `SurfaceView`) en Smart TV y dispositivos ligeros, el cual intentaba inicializar códecs de video por hardware simultáneamente con la carga de la app, saturando la GPU/CPU y provocando un fallo de segmentación fatal (`SIGSEGV` en `libhwui`) al intentar animar la superficie de video.
  - Reemplazado por una pantalla de bienvenida 100% nativa en Compose con tipografía nítida «VISION+», indicador de progreso sutil en azul de la marca y fundido de salida por hardware ultra-suave. Consume 0 instancias de decodificadores de video y renderiza en el primer frame.
- **Memoria Heap ampliada (`android:largeHeap="true"`)**:
  - Habilitado `largeHeap` y aceleración por hardware en el manifiesto, elevando el límite de memoria Dalvik de 128 MB a 512 MB en dispositivos de TV. Esto erradica los errores `OutOfMemoryError` al procesar catálogos grandes e inflar composables.
- **Arranque no bloqueante y optimización de corrutinas (`MainActivity` & `XuperCatalog`)**:
  - Eliminado el bloqueo y fuga de corrutina de 9 segundos en el boot inicial; la pantalla de inicio se libera tan pronto como el catálogo y los canales responden (mínimo de gracia fluido de 600 ms).
  - Eliminada la descompresión innecesaria de archivos DEX de SDK en el hilo de arranque del proceso principal.
  - Las repisas principales de XTV y canales de TV en vivo se despliegan de inmediato; las búsquedas secundarias a bibliotecas externas (`ArchiveSource`) ahora operan de forma concurrente con límite de tiempo de 2.5s y caché en memoria, evitando que peticiones lentas retrasen el inicio de Vision+.
- **Cero regresiones**: Conserva intacto el confinamiento de foco (Focus Trap v2.5.21), carga instantánea (v2.5.20), reproducción garantizada en TV en vivo y 104 pruebas unitarias al 100% en verde.
