# Análisis: gee-sports.apk (`com.geesports.live`) — fuente alternativa de deportes para el apartado de Fútbol

**Fecha**: 2026-10-16 · **APK**: `C:\Users\Axel Rosete\Downloads\gee-sports.apk` (6.3 MB, SHA-256 `914A0329…B8B7`)
**Decompilado**: `Temp/opencode/gee_src` (jadx) + `Temp/opencode/gee_apktool` (apktool/smali).
**Estado**: análisis completo + auditoría de vida real hecha; integración **pendiente de decisión del usuario** (ver «Plan»).

---

## 1. Qué es

- Rebrand de **CricZ TV v5.0** (su `app.json` publica `website: https://t.me/cricztvapk` y el update `ul:` a un APK en GitHub). Pkg `com.geesports.live`.
- Catálogo propio de canales (cricket/desi + deportes internacionales) servido como **CMS JSON sobre GitHub Pages** (`https://absuehhd.online/`, IPs 185.199.x.x) y **dominio descubierto por Firebase Remote Config**.

## 2. Cadena de descubrimiento (lo único «vivo» y estable)

```
Firebase RC (POST fetch, key en el APK, appId 1:54982471369:android:4ffe4d01762eca4b54a718)
   → entries.api_url = https://absuehhd.online/        (verificado en vivo hoy; templateVersion 7)
CMS (todas las respuestas envueltas en {"data": "<base64(JSON)>"}):
   app.json                 → sig/hash/version/website/ul/ads
   cats.json                → 16 categorías (Sports, LIVE Cricket, Live Football, ...)
   categories/sports.json   → 171 canales: {id,title,img,cat,slug,formats[]}
   categories/<cat>.json    → igual por categoría
   home.json                → slides + eventos en vivo (con slug)
   channels/<slug>.json     → {"streamUrls":[{title,link,type,api}], "header":…, "body":…}
```

Modelo `Channel(body, header, streamUrls)` y `StreamUrl(title, link, type, api)`.
**VERIFICADO EN SMALI**: esta versión del APK **solo lee** `getStreamUrls()`, `getLink()`, `getApi()`, `getType()`.
`header` (Basic auth) y `body` (payload `token_data`) **no se usan** — son datos muertos para esta versión (posiblemente para su web). No hacen falta para replicar el comportamiento del APK.

## 3. Semántica de `type` (cómo elige cada URL)

| type | Qué es | Cómo lo resuelve el APK |
|------|--------|--------------------------|
| **0** | HLS directo (a veces `.php`, `.mpd`, o sufijo `\|origin=…&referer=…&user-agent=…`) | directo a ExoPlayer |
| **3** | DASH `cenc` con **ClearKey**: `api` = `kid_hex:key_hex` | `y(hex)` = hex→base64url **sin `=`**; arma el JSON W3C `{"keys":[{"kty":"oct","k":"<key>","kid":"<kid>"}],…}` y lo sirve por un `MediaDrmCallback` local. Si `api` empieza por `http` trata otros DRM (w3c/widevine/playReady por nombre en `api`) |
| **1** | Link que se completa en runtime | **POST** al `api` con body `token_data` (`{data:<rand 0-899>, hash:MD5(pref 'cgahfl'+data), method_name:'token_data'}` urlencoded); la respuesta (string) se des-ofusca con `B.d`: invertir → cortar en tramos de 10 saltando el char en pos. 10/22/34/46/58 → re-invertir → concatenar al `link` |
| **2** | HLS con **llave AES-128 protegida** | `api` = `keyUrl*tt` → MMKV (`keyUrl`, `tt`). El parser HLS modificado («sheda») intercepta `#EXT-X-KEY` con URI que contiene `twitter.com` y reescribe la URL de la llave usando el template: `tt=1` → `keyUrl + base64(AES-CBC({"type":"tw","url":<uri>}, "abcdefghij123456"))`; `tt=2` → doble transform `Z3`; `tt=3` → `keyUrl+stream_name`; otro → quita el param |

Extras de reproducción (smali):
- Sufijo `|`: se parte por `|`, los `k=v` se url-decoden a un **mapa de headers** que se aplica al **OkHttp compartido** que usa el data source de ExoPlayer (UA por defecto “Mozila” si no hay).
- MIME: link contiene `m3u8`/`.php` → HLS; type 3 → `DashMediaSource.Factory` + DRM.
- ExoPlayer **2.18.7** con parser HLS propio y OkHttpDataSource propio.

## 4. Auditoría de vida real (2026-10-16) — la viabilidad

Muestra: **370 enlaces únicos de 33 slugs de `categories/sports.json`** (todos los grandes: ESPN, DAZN, TNT, Sky, Movistar, Fox, beIN, Canal+, TUDN, LaLiga(22), Bundesliga(25), etc.), probados con DNS + TCP + HTTP(GET 1 pasada con headers del `|`).

| Perspectiva | Resultado |
|---|---|
| **PC del usuario** (red residencial MX, sin VPN de salida; IP 187.173.148.116) | **8/370 vivos (2.2 %)** |
| **VPS OCI (EU)** | mismo patrón (dominios muertos = muertos globalmente; paneles por IP inaccesibles desde ambas) |

- **~60 % NXDOMAIN global** (no existen ni en Cloudflare/Google DNS): `webhdrus.onlinehdhls.ru`, `cdn2.aluku91.online`, `redima.pro`, `ptvsports2.com`, `onlinehdhls.ru`, etc. — el grueso de ESPN/DAZN/TNT/Sky/Movistar de los bundles están ahí.
- Paneles por IP (`170.82.37.21:8000`, `181.78.105.146:2000`, `154.58.202.35:8002`, `175.107.244.52:4457`) → **timeout desde PC y desde VPS**.
- Vivos hoy (ejemplos): `tv.cloudcdn.me`, `iptv.cinehub.wf`, `kora-sa.live`, `persiana.mastercast.cloud`, `tv.cdn.xsg.ge`, `serv30.vintera.tv`, `beiN Xtra (amagi, free)`, `linear-novi.stvacdn.spectrum.com` (DASH). Por canal: `star-sports-2` (3), `sports-channels` (2), `star-sports-1`/`willow` (1). El resto de la muestra: 0.
- «Live Football» (cat 90): 1 entrada vieja (Euro-24). «Stream Down» (cat 91): vacía. El mantenedor solo refresca los 24/7 de vez en cuando (oleadas).

**Conclusión de viabilidad**: la app original NO es mágicamente estable — hace lo mismo que podemos hacer nosotros (fetch fresco + N servidores por canal, y el usuario salta entre chips). La diferencia a favor nuestra: podemos **filtrar por vida** y **refrescar solos**. Lo que NO se puede: garantizar canales (hoy los canales de fútbol europeo del CMS están mayormente caídos desde esta red). Sirve como **fuentes alternativas adicionales**, nunca como vía única.

## 5. Lecciones vs. la integración vieja (v2.5.34 → retirada en v2.5.48)

1. Los «seeds curados» mueren en semanas → **nunca hardcodear links**; siempre fetch fresco + validación.
2. Parsear **type 3** (ClearKey `kid:key`) es imprescindible: sin eso, la mitad de los bundles DAZN/TNT/beIN/Fox no abren.
3. El sufijo `|` de headers debe respetarse al pedir playlist/segmentos (igual que ya hace `GeeSportsFallback.parseStreamUrl` + `PlayerCore`).
4. `header`/`body` del Channel: innecesarios (no los usa ni la propia app).
5. Mostrar SOLO lo verificado vivo y **auto-siguiente** si falla en reproducción.

## 6. Plan de integración propuesto (2 fases)

**Fase 1 — pipeline en el VPS + integración en la app (recomendada)**
1. `server/sync/gee.py` (worker, cada ~20-30 min, patrón de `sports.py`):
   - GET `categories/sports.json` → 171 canales (título, slug, formats).
   - Por slug relevante (brands del fútbol): GET `channels/<slug>.json` → decodificar base64 → extraer `streamUrls` (type 0 directo con headers del `|`; type 3 con `kid:key` → JSON ClearKey listo).
   - **Probe de vida** (DNS→TCP→HTTP HEAD/GET 0-2 KB, timeouts cortos, los headers del `|`): guardar `estado` + `checked_at`.
   - Tabla `gee_links(slug, title, url, headers_json, drm_kid, drm_key, alive, checked_at)`.
2. API: `GET /v1/gee/sports?brand=&slug=` (X-Api-Key) sirviendo solo vivos + `POST /v1/gee/feedback` (marca muerto un link al fallar en reproducción → re-probe).
3. App:
   - `GeeSportsFallback` v2: lee del VPS (caché 6 h, cae a fetch directo del CMS si el endpoint no responde), expone `LiveChannel` con `drmKey/drmType` (ya soportados).
   - `SportsLinker`: vuelve a inyectar como **«Deportes · Alternativo»** por partido (dedup con XTV, top ~4, los vivos primero); al fallar una fuente en reproducción → feedback + siguiente opción automática.
4. Guardián de tests: parseo del CMS (base64/type/headers), armado ClearKey == contrato, filtro de vivos, feedback.

**Fase 2 — sólo si aparece tráfico type 1/2** (hoy 0 casos en los bundles de fútbol):
- type 1: POST token_data + des-ofuscación `B.d` (documentada arriba).
- type 2: rewrite de llave `twitter.com` (`tt`) — tanto en servidor (reescribir playlist) como en `ResolvingDataSource` en la app.

**Fase 3 (opcional) — proxy del VPS**: si se quiere blindar contra bloqueos del ISP del usuario, el VPS puede relayar playlists+segmentos (`/v1/gee/relay?u=`). Coste: ancho de banda del VPS. No recomendado de entrada.

## 7. Verificación pendiente (antes de codificar)

1. ~~**Usuario**: abrir gee-sports HOY~~ **RESUELTO (2026-10-16, misma sesión)**: prueba en vivo en el emulador `Vision_Phone` con la **app nueva V3.8** (ver §9): la app carga y **reproduce video real** (TNT Sports en directo). En el A32 rooteado la app nueva se bloquea (chequeo nativo anti-root; ni Magisk denylist lo evade) — el A32 queda fuera de esta app por diseño de ella.
2. Con una muestra viva, probar en Vision+ el triángulo: HLS directo con `|headers`, HLS con llave `twitter.com` (si aparece), y DASH ClearKey `kid:key` (verificación física; en v2.5.34 nunca se verificó en dispositivo). → **Pendiente al implementar la Fase 1** (ya hay muestra viva identificada).
3. Decidir Fase 1 vs fetch directo en app → **DECIDIDO: Fase 1 (pipeline en el VPS)**.

## 8. Artefactos de esta sesión (Temp/opencode)

- `gee_src/` (jadx) + `gee_apktool/` (smali, `PlayerActivity.smali` método `R()` = armado del media source).
- `gee_espn.decoded.json`, `gee_cats*.json`, `gee_slugs.txt` (171 canales), `gee_bundles_multi*.json` (33 bundles), `gee_probe_results.json` (auditoría 370 links), `gee_audit.py`, `gee_live_test.py`, `gee_retest.py`, `gee_vps_probe.sh`, `gee_decode.py`, `gee_check.ps1`.

## 9. APK NUEVA (V3.8) — `GeeSports_(V3.8).apk`, pkg `com.live.geesports` (2026-10-16)

**Ojo: es OTRA app**, no una versión de `com.geesports.live` (aquella era CricZ TV 1.3; su propio update manda a Telegram `t.me/cricztvapk` y exige 3.7.x). La V3.8 es la "actual" del ecosistema: vc11/vn3.8, compileSdk 36, **4 ABIs** (arm64-v8a, armeabi-v7a, x86, x86_64), actividades limpias (sin ofuscar): `SplashActivity → MainActivity` (eventos/categorías/deportes), `PlayerActivity` (Media3/ExoPlayer 1.8.0), `TvActivity`, `SupportActivity` (gate de anuncios "1 ad / 12 h"), `WebActivity`, Room, Glide, Lottie, Firebase, `Fetch`.

**Infra nueva**:
- Firebase RC **propio** (proyecto `gee-sports-dcc62`): `api_url = https://new.whatyouthink.site/` (+ `telegram_url`/`web_url` = t.me/geesportss). Dominios antiguos embebidos (`sohailllllllll.space`, `khhjjshv.com`) muertos; `geesports.xyz` vivo pero sin datos en raíz.
- **Bootstrap cifrado**: `GET <api_url>app.txt` (2,364 B, base64) → `Dash.dashStr(context, texto)` — función **NATIVA** (`libnative-lib.so`, JNI `Java_com_google_android_exoplayer_Dash_dashStr`; usa `/proc/self/maps` + strstr → detecta Magisk; en rooteado devuelve el mensaje «You can't use GeeSports in Rooted Device!» que se ve en pantalla) → si no queda JSON, fallback `s4.v.p()` = AES-128-CBC/PKCS5 con llave/IV estáticos del DEX (`l9K5bT5xC1wP7pK1` / `k5K4nN7oU8hL6l19`; validado con el vector anti-mod «If you are using mod app then download from official site»). El `app.txt` actual NO es múltiplo de bloque AES → su descifrado real es el nativo.
- Respuestas con `{`/`[` pasan tal cual; el resto pasa por el pipeline nativo → JSON (Splash: `JSONArray(str)` → configs `sc.a`; Player: `{link, type}` con headers `|` y DRM por `type`).
- Gate anti-tamper extra: `libtoolChecker.so`.

**Prueba en vivo (emulador Vision_Phone, x86_64, sin root — la app no corre en el A32 rooteado)**:
- Arrancó, cargó **Events** (215 eventos, 18 en vivo) y **Sports** (grupos: TNT Sports, BeIN, Eleven, FOX, LaLiga TV, Telemundo, NBC, Nova, Premier, SPOTV…).
- TNT Sports → "Multiple links available" (TNT SPORTS 1–4) → tap 1: gate de anuncio (SupportActivity) → cerrar → **reproduce VIDEO EN DIRECTO** (UFC; Media3 `ExoPlayerImpl Init [AndroidXMedia3/1.8.0]`, `c2.goldfish.h264.decoder`; capturas `gee_emu_tnt9.png`, `gee_emu_tnt10.png` — reloj del evento avanzando 1:34 → 0:52).
- Conclusión: la nueva app **sí mantiene señales vivas** (el CMS nuevo se refresca); su "estabilidad" es eso + servidores numerados por canal + gate de ads.
- El A32 quédate **sin poder correrla** (root nativo). Banco de pruebas para esta app: emulador Vision_Phone (o cualquier equipo sin root).

**Implicaciones para la integración (Fase 1 aprobada: pipeline en el VPS)**:
1. Fuente fácil y 100% decodificable server-side: el **CMS viejo** (`absuehhd.online`, base64 plano — el de la app 1.3, aún vivo). El worker puede refrescar + filtrar por vida + servir `/v1/gee/sports` sin tocar cripto nativa. Los tipos 0/3 (HLS + ClearKey) ya tienen soporte en Vision+.
2. Fuente "premium" (CMS nuevo): requiere **replicar `dashStr`** (RE de `libnative-lib.so` x86_64/arm64: 250 KB, JNI directo + cifrado + anti-root; sin ofuscación fuerte aparente) o un puente Android. Fase 2 (o extensión de Fase 1) una vez validado el pipeline básico.
3. En móvil/TV de usuario sin root, la app nueva funciona; la nuestra (Vision+) no tiene ese problema (no aplica el bloqueo).

**Artefactos**: `gee38_src/` (jadx), `gee38_apktool/` (smali), `lib/x86_64/libnative-lib.so` + `lib/arm64-v8a/libnative-lib.so`, `newcms_app_txt` (blob), `newcms_app_decrypted.json` (fallido AES — documentado), capturas `gee_emu_*.png`, `gee_a32_new*.png`.
