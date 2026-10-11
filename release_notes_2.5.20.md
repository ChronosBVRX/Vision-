## Novedades de Vision+ v2.5.20

### ⚡ Carga Instantánea de Catálogos (VOD y TV Online)
- **Apertura ultra-rápida (0 ms) en Películas, Series, Novelas y Anime**: Implementación del patrón *Stale-While-Revalidate* y almacén en memoria RAM (*In-Memory Store*) en `RemoteIndexSource`. Al navegar a cualquier sección con el control remoto o en el móvil, la pantalla se dibuja de inmediato sin spinners ni bloqueos de red.
- **Precalentamiento en segundo plano (*Boot Prewarm*)**: Durante la pantalla de carga inicial (*Splash* de Vision+), la app calienta en memoria las 4 secciones principales y la lista de canales de TV en vivo en segundo plano sin demorar el arranque.
- **TV en Vivo instantánea**: La lista de más de 1,000 canales y su taxonomía por géneros y países se conservan en memoria viva a nivel de proceso (`LiveStore` y `X4KApi`), eliminando re-parseos de JSON y bloqueos al entrar a la sección.
- **Caché acelerada en Backend VPS**: El endpoint `/v1/section/{name}` incorpora almacenamiento en memoria indexado por versión, reduciendo drásticamente las consultas a base de datos y acelerando la respuesta a 1-2 ms.
