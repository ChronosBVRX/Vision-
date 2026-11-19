# Vision+ 2.6.63 — 4K público, sin depender de una red local

Actualización sobre 2.6.62.

## El 4K ya no se queda en la LAN

- El módulo 4K ahora consulta primero **4KHDHub**, un proveedor con archivos 4K en **CDN público** (Cloudflare R2 firmado). Sirve aunque el equipo no esté en la red del addon de Kronos.
- Kronos queda como segunda fuente: aporta audio latino cuando existe y respaldo local.
- Antes de ofrecer una pista, el módulo hace una petición real de 1 KB al CDN. Si no responde, no se muestra el badge ni se pierde tiempo al dar Play.
- Se prioriza el archivo 4K más ligero de cada proveedor: un REMUX de 85 GB no es la mejor primera impresión en un teléfono.
- Las URLs firmadas de R2 se respetan por su vencimiento (5–8 h); el worker las renueva como siempre.
- Si el equipo no decodifica HEVC o el CDN falla, se mantiene la caída automática a la máxima calidad nativa de Xuper en el mismo minuto.

## Aviso de audio

- El 4K público de 4KHDHub viene en **audio original/subtitulado** (Hindi/English). Cuando Kronos tenga la misma película en latino, sigue disponible como fuente alternativa.
- El 4K de Kronos en latino sigue funcionando si el equipo está en su red.

## Verificación

- Compilación `:app:compileNormalDebugKotlin` correcta.
- **1,355 pruebas** ejecutadas; solo los **3 fallos ambientales conocidos** de la suite local.
- Nuevas pruebas del proveedor público: nombre con «4KHDHub» no confunde un 1080p, tamaño, vencimiento firmado y preferencia por el archivo más ligero.
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
