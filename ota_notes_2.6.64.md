# Vision+ 2.6.64 — El 4K público sí se usa desde la actualización

Actualización sobre 2.6.63.

## Qué se corrigió

- La caché 4K guardada por versiones anteriores (solo el proxy **LAN de Kronos**) tapaba al proveedor público durante 6 h. Ahora la caché tiene **esquema**: las entradas viejas se ignoran y la ficha vuelve a consultar **4KHDHub** en cuanto abres la película.
- Esto es lo que impedía que, tras actualizar, se viera el 4K público: el equipo seguía reproduciendo el enlace `192.168.1.101:7015` y caía a 1080p sin llegar a probar HdHub.
- No hay que borrar datos ni caché manualmente: la migración es automática y solo afecta al módulo 4K.

## Se mantiene

- HdHub como fuente 4K pública (Cloudflare R2). Kronos queda como segunda fuente para latino/LAN.
- Preflight real de 1 KB antes de ofrecer una pista, archivo más ligero primero y vencimiento de las URLs firmadas.
- Caída automática a la máxima calidad nativa de Xuper si el equipo no puede con el 4K.

## Verificación

- Compilación `:app:compileNormalDebugKotlin` correcta.
- **1,356 pruebas** ejecutadas; solo los **3 fallos ambientales conocidos** de la suite local.
- Nueva prueba: una entrada antigua sin esquema se ignora y fuerza la consulta al proveedor nuevo.
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
