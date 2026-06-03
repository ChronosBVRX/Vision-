# Documentación: Sistema de Resolución Automática de Fuentes de Video (VOD Resolver)

Este módulo backend implementa un motor avanzado de extracción, normalización, ordenamiento y validación de fuentes de streaming de video para películas y series del catálogo de **Vision+**. 

El objetivo es lograr que el usuario final reproduzca el contenido directamente en el reproductor interno libre de anuncios de la app sin interactuar con popups, iframes externos, scripts maliciosos de terceros ni selectores manuales de servidor.

---

## Arquitectura del Resolver

El resolvedor de fuentes está ubicado en el directorio `server/resolvers/` y consta de los siguientes submódulos:

```
server/
 └── resolvers/
      ├── videoSourceResolver.js      # Orquestador y Scraper de páginas del catálogo
      ├── languageNormalizer.js       # Normalización y priorización de idiomas
      ├── sourceValidator.js          # Validador de conectividad y Content-Type de los streams
      ├── browserResolver.js           # Extractor mediante Playwright (solo dominios en lista blanca)
      └── adapters/
           ├── directFileAdapter.js   # Para enlaces directos (.mp4, .m3u8, .mpd)
           ├── authorizedEmbedAdapter.js # Para embeds autorizados en lista blanca (usa browserResolver)
           ├── plutoAdapter.js        # Para películas de Pluto TV México
           └── fallbackAdapter.js     # Descarte de fuentes HTML o no reproducibles
```

---

## Flujo de Resolución Técnico

1. **Recepción del ID:** El endpoint `GET /api/movies/:id/resolve?lang=auto` recibe el identificador del contenido.
2. **Búsqueda en Base de Datos:** Busca el item de VOD en `sources`, `movieCatalog` o `seriesCatalog` en `database.json`.
3. **Scraping del Catálogo:** Si el ítem es de páginas externas (e.g. PelisPlus, Cuevana, PelisOnline):
   - Realiza un request rápido y analiza el HTML para extraer los diferentes idiomas y servidores disponibles (e.g. `video[...]` en PelisOnline, `li.playurl` en PelisPlus, o `/ver.html` VideoJS en Cuevana).
4. **Normalización de Idiomas:** Las etiquetas extraídas se unifican bajo los nombres estándar:
   - `Español Latino`
   - `Español España`
   - `Subtitulado`
   - `Inglés`
5. **Ordenamiento por Prioridad:** Si `lang=auto`, se ordenan de acuerdo a la prioridad:
   1. `Español Latino`
   2. `Español España`
   3. `Subtitulado`
   4. `Inglés`
   5. Cualquier fuente disponible
6. **Iteración de Servidores:** Prueba secuencialmente cada servidor/idioma prioritario.
7. **Resolución de Reproductores y Emisión:**
   - Si la opción es un archivo directo, se comprueba su conectividad.
   - Si es un iframe de un dominio en la lista blanca de `.env`, se levanta Playwright para interceptar las llamadas de red y extraer el stream `.m3u8` o `.mp4` real en segundo plano.
   - Si no es directa y el dominio no está autorizado, se descarta.
8. **Validación:** Se comprueba el enlace final haciendo una petición `HEAD` o `GET` parcial (`Range: bytes=0-100`) confirmando que sea de tipo `application/vnd.apple.mpegurl` o `video/mp4` y no una página HTML o error `403`/`404`.
9. **Respuesta al Cliente:** Entrega la primera fuente funcional lista para Hls.js o Plyr.

---

## Configuración y Lista Blanca (.env)

El archivo `.env` en la raíz del proyecto contiene el puerto y la lista blanca de dominios de video autorizados para resolución automatizada mediante navegador:

```env
PORT=5000
AUTHORIZED_VIDEO_DOMAINS=mi-dominio.com,mi-cdn.com,proveedor-autorizado.com,mp4movies.us,cuevana.ac,voe.sx,streamwish.to,filemoon.to
```

---

## Integración Frontend

### Loader y Resolución (`Movies.jsx`)
Cuando el usuario da clic en reproducir una película, el catálogo muestra un Loader premium: *"Buscando la mejor fuente disponible..."*.
Al resolver exitosamente, construye un objeto `playableSource` y monta el reproductor:
```javascript
const playableSource = {
  id: movie.id,
  title: movie.title,
  type: "movie",
  poster: movie.poster,
  provider: "internal-resolver",
  selectedLanguage: response.selectedLanguage,
  selectedServer: response.selectedServer,
  streams: [{
    name: response.selectedServer,
    url: response.stream.url,
    type: response.stream.type,
    headers: response.stream.headers || {},
    quality: response.stream.quality || "auto",
    resolver: "direct"
  }],
  attempts: response.attempts || []
};
```

### Reintentos Automáticos (`VideoPlayer.jsx`)
Si la transmisión del video falla en el reproductor (por caída de red o de servidor):
- Detecta el error del reproductor o error de carga de HLS.
- Llama a `/api/movies/:id/resolve?lang=auto&excludeServer=SERVIDOR_ACTUAL` excluyendo el servidor caído.
- Reemplaza el stream y aumenta la prop `playerKey` para desmontar y remontar el video de forma limpia.
- Realiza hasta 3 intentos antes de arrojar un error final.

### Modo Debug para Administradores
Habilitado en el frontend mediante la variable:
```env
VITE_DEBUG_SOURCES=true
```
- Si está activo, el reproductor de video muestra un botón **"Intentos"** en los controles.
- Al pulsarlo, despliega un panel lateral detallado que muestra cada servidor analizado, el idioma detectado y el motivo de descarte (e.g. `TIMEOUT`, `HTML_PAGE_NOT_PLAYABLE`, `HTTP_404`), permitiendo a los administradores diagnosticar problemas del catálogo al instante.
