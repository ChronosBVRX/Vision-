# Vision+ 2.5.199 — Android 8/9: «Instalar en TV» ya puede autorizar el ADB

**Fecha:** 30 de octubre de 2026  
**Versión:** 2.5.199 / vc439  
**Launcher:** 1.3.14 / vc37 (sin cambios)

## Qué cambia

- **Android 8/9 — la tele por fin muestra «¿Permitir depuración?»**: el flujo usaba LibADB con `throwOnUnauthorised = true`, que corta el segundo `A_AUTH` **antes de enviar la llave pública**. Por eso la tele nunca mostraba el aviso y la app se quedaba esperando. Ahora se envía la llave y se espera hasta **90 segundos** a que la aceptes con el mando.
- **La dirección manual ya no queda bloqueada** mientras corre la búsqueda del código de Android 11+: puedes escribir la IP y pulsar **Conectar** de inmediato.
- **Avisos claros durante la espera**: el diálogo explica que hay que aceptar el aviso en la tele y que la primera vez puede tardar. Si la tele está en espera, enciéndela con el mando.
- **Instalación sin el verificador de Android**: antes de instalar se apaga `verifier_verify_adb_installs`; si aun así el sistema bloquea el APK con `INSTALL_FAILED_VERIFICATION_FAILURE`, se reintenta con el verificador apagado.
- **Inicio de respaldo**: en Android 8/9 el sistema puede quedarse unos segundos en su pantalla de respaldo (`com.android.tv.settings`, `fallbackhome`, `provision`). La app espera, abre Vision+ Home, y **no desactiva** esos Inicios para no dejar la tele sin pantalla.
- **Más compatibilidad de firmware**: se acepta `mFocusedActivity` en el `dumpsys` y, si `pm set-home-activity` falla, se prueba `cmd package set-home-activity`.
- **Cancelar de verdad**: cerrar el diálogo interrumpe la espera de ADB y cierra el socket en vez de dejarlo ocupado.

Todo lo de la 2.5.198 sigue incluido: **Vision+ Play v2** y el **launcher cinematográfico 1.3.14** (fondo continuo, hero limpio, tarjetas 16:9/2:3, foco 10-foot, entradas de TV y TV en vivo sin sintonizador).

## Requisitos en la tele Android 8/9

1. Misma Wi-Fi que el teléfono.
2. Ajustes → Sistema → Información → 7 toques a «Versión» → Desarrollador → activa **Depuración USB** y **ADB por red**.
3. Mira la IP en Ajustes → Red y escríbela en la app.
4. Acepta **«¿Permitir depuración?»** con el mando y marca **«Permitir siempre»**.

## Pruebas

- Android: **1,148 tests** en verde (incluye el nuevo `TvAdbLegacyAuthTest`, que levanta un servidor ADB falso y demuestra que se envía la llave pública con `throwOnUnauthorised = false`, y que con `true` no se envía).
- Launcher: **181 tests** en verde (sin cambios).
- Backend: **570 tests** en verde (sin cambios de servidor).

## Verificación

- APK release R8 con motor X4K inyectado (`classes3/4/5.dex`).
- Firma histórica `9593cb0d…` en la app y en el launcher.
- `VisionPlus.apk`: **97,637,875 B**, SHA-256 `ba1545e1cff5a12a4ad899fddb892a292b026937e230775b69cae91fa261a8a5`.
- `VisionPlusHome.apk`: **2,806,043 B**, SHA-256 `2afab0bc8cdb2a10a96724fb70afa42fe3a64b39832f984ba3583803b140fd2b`.

## Pendiente

- Prueba física en una tele Android 8/9 real (aceptar el aviso y reiniciar para confirmar que Vision+ Home sigue siendo el Inicio).
- Si algo falla, el botón **«Detalles»** del diálogo muestra el comando y la respuesta exacta de la tele.

Actualiza desde el aviso de la app (o Ajustes → «Buscar actualizaciones»).

— ChronosBVRX
