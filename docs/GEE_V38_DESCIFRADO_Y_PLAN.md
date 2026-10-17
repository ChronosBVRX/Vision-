# GeeSports V3.8 — CMS nuevo: descifrado resuelto (vía harness) + plan de integración

**Fecha**: 2026-10-16 (sesión maratón) · **Estado**: descifrado OPERATIVO vía harness nativo en emulador/A32; clave pura Python a un detalle del RE; datos completos ya extraídos.
**Doc principal anterior**: `docs/ANALISIS_GEE_SPORTS.md` (§9 tiene el análisis base de la V3.8).

## 1. Endpoints del CMS (verificados)

Base: `https://new.whatyouthink.site/` (descubierta por Firebase RC del proyecto `gee-sports-dcc62`; los dominios embebidos viejos están muertos).

| Archivo | Contenido | Notas |
|---|---|---|
| `app.txt` | config JSON (1316 B descifrado) | telegram/web/download_url, `cric_live_url`, `foot_live_url` (SofaScore), ads, versión 11 |
| `sports.txt` | **36 grupos de canales** (8567 B) | cada uno `{cat: "<JSON interno>"}` con `{name, logo, type, api}` |
| `events.txt` | **229 eventos** (155 KB) | cada uno `{event: "<JSON interno>"}` con category/eventName/teams/flags |
| `channels/<base64(nombre+ts)>.txt` | canales de un grupo (632 B–…) | mismo cifrado |
| (m3u externos) | p.ej. `fawasp.pages.dev/playlist.txt` (19.9 KB PLANO con #EXTVLCOPT) | los `type:"m3u"` |

**Grupos deportivos** (36): Live Events SD, FIFA TV, FIFA HIGHLIGHTS, SPORTS HD, MLB LIVE, CANAIS DO BRASIL, Sky Sports (x2), South American Sports, **TNT Sports, BeIN Sports, Eleven Sports, FOX Sports**, FANCODE, WILLOW, Ziggo, NOVA, Premier, **LaLiga TV**, Laliga Hypermotion, NBC Sports, Formula 1, FIFA+, SPOTV, **Telemundo, TUDN**, TAPMAD BD, Sport TV, CBS, Sport 5, Highlights, T Sports, RMA TV, Willow Event (m3u), PRIME SPORTS (m3u)…

## 2. El cifrado (ingeniería inversa casi total)

```
texto servido  = base64( X( base64( AES-128-CBC(JSON+PKCS7) ) ) )
    X = pairswap(adyacentes) ∘ reverse   [involutiva; deja el '=' de padding en la posición 1]
    IV = constante embebida en la .so al final de la tabla (decA y decB tienen una cada una; valores exactos en el archivo local de credenciales)
    CLAVE = derivada del CERTIFICADO DEL APK (¡no del bundle!):
        SHA-256( certDER ) → base64 → pairswap de los chars → base64 OTRA VEZ →
        los 16 chars [1:17] … [DETALLE PENDIENTE: con el oráculo esta composición tal cual NO
        descifró; falta afinar 1 paso — ver §5]
```

**Verificado en la .so (x86_64/arm64, `libnative-lib.so`)**:
- `Dash.dashStr(Context, String)` JNI: anti-debug (`/proc/self/status` TracerPid), anti-frida (maps: "frida", "re.frida.server", "gum-js-loop", "substrate", "xposed"; socket), **verificación de firma** (getPackageName→PM→getPackageInfo→signatures→toByteArray vs constante ofuscada) y **derivación de clave** (getPackageCodePath→ZipFile→META-INF/*.RSA→CertificateFactory→getEncoded→SHA-256 nativo con IV estándar→base64→pairswap→base64→substring).
- AES-128-CBC estándar por software (S-box estándar en 0xADA0, inv en 0xAEB0, Rcon; cifrado "equivalent inverse"); expansión estándar 0x18780.
- "Modo" = número de archivos `.so` en `nativeLibraryDir` (== 5 con los 5 libs oficiales) — sello de instalación legítima.
- **decA** (firma OK) vs **decB** (fallback) — con textos reales el que descifra es **decB**.

## 3. El harness (descifrador operativo HOY)

Proyecto: `Temp/opencode/gee38_harness/` (mini-APK "GeeProbe"):
- Bundlea **las 5 .so** del APK original (x86_64 + arm64) + clase `com.google.android.exoplayer.Dash` (JNI) + `FakeCtx` (ContextWrapper: `getPackageCodePath()` → copia del **APK original** en la carpeta externa; `getPackageName()` → `com.live.geesports`) + `MainActivity` de diagnóstico.
- **Manifest**: `<queries><package android:name="com.live.geesports"/></queries>` (visibilidad de paquetes — sin esto getPackageInfo lanza NameNotFoundException) y `android:debuggable="true"` (para run-as).
- **Requisito**: la app `com.live.geesports` INSTALADA en el mismo dispositivo (la firma se valida contra ella) + `orig.apk` (el APK original) + input `gee_in.txt` → output `gee_out.bin`.
- **Funciona en emulador (Play, sin root)** y debería en cualquier dispositivo sin frida (los checks nativos no buscan magisk). Con archivos REALES: app=1316 B, sports=8567 B, events=155082 B ✓✓✓.
- **OJO anti-latch**: ~la primera llamada inválida en un proceso deja las siguientes en 0 — usar UN input real por proceso o reiniciar el proceso entre pruebas.
- **Rebuild** (sin gradle): javac (JBR) → d8 (JAVA_HOME=JBR) → `aapt package` + `aapt add classes.dex lib/<abi>/*.so` → zipalign → apksigner (debug keystore). Scripts en Temp: `gee38_build*.ps1`.

## 4. Datos descifrados ya extraídos (Temp/opencode)

- `gee38_harness/gee_out_app.bin` (config), `gee_out_sports.bin` (36 grupos), `gee_out_events.bin` (229 eventos).
- `gee_tudn_cat.bin`, `gee_tnt_cat.bin` — ejemplos de `channels/*.txt` (cifrados, mismo esquema).
- `gee_live_events_m3u.txt` — el M3U PLAINO del grupo "Live Events SD" (`fawasp.pages.dev/playlist.txt`, con #EXTVLCOPT referrer/origin/UA).

## 5. ✅ RESUELTO — Clave pura Python (independencia total, 2026-10-17)

**La receta EXACTA y la clave derivada NO se guardan en este repo (es PÚBLICO).** Están en el archivo local de credenciales:
`C:\Users\Axel Rosete\.visionplus\gee38_key.txt` (y copia del descifrador en `gee38_crypto.py` de esa misma carpeta; versión de trabajo en `Temp/opencode/gee38_crypto.py`).

En corto (sin literales sensibles): la clave se deriva del **certificado del APK** (SHA-256 → base64 → pairswap → reverse → base64 → primeros 16 chars), IV = la segunda constante de la `.so`, y el descifrador puro Python (`derive_key_from_apk()` + `gee_decrypt()`) fue **verificado EN VIVO** contra el CMS (app/sports/events + `channels/*.txt` ✓). Si rotan el certificado del APK, la clave se re-deriva sola.
- Método de caza usado (documentado por si hiciera falta de nuevo): harness "GeeProbe" en **A32 con root** (bucle caliente de `dashStr`) + `su dd from /proc/PID/mem` de las regiones `libc_malloc` (rondas rápidas de ~35MB) → la clave apareció en el contexto del "outer" base64 completo en memoria → oráculo sin IV `D(C1)^C0 == P1` la confirmó; el resto fue re-derivar la receta.

## 5b. Docker en el VPS — veredicto: NO viable (e innecesario)

- El VPS (`159.54.146.146`) **no tiene `/dev/kvm` ni flags vmx/svm** (4 vCPU, 22.9 GB RAM). Un Docker-Android (budtmo/docker-android, Waydroid, Android-x86 en QEMU) quedaría en emulación por software: arranques de 10-30+ min, frágil, y solo para llamar una función. **Descartado.**
- Con la clave pura ya no hace falta: el worker `sync/gee38.py` del VPS descifra directo en Python (contenedor `sync` existente).

## 6. Plan de integración en Vision+ (calendario de fútbol)

1. **Worker VPS** `sync/gee38.py`: fetch `app/sports/events.txt` (+ `channels/*.txt` de los grupos de fútbol + M3Us externos) → descifrar (harness-post → o Python cuando cierre §5) → parsear grupos/canales/eventos → probe de vida → tablas `gee_channels`/`gee_events` → `GET /v1/gee38/sports`.
2. **App**: pestañas por deporte con filtros, carga diferida; en Fútbol, por partido de nuestro calendario, opciones "Alternativo" (solo vivas) + salto automático si falla; feedback al VPS.
3. **Carril B ya no aplica** (esa era la CMS vieja; el usuario decidió integrar SOLO la V3.8).
