# Vision+ 2.5.246 — Versión puente de distribución

**Esta versión solo cambia el canal de actualizaciones.** No hay cambios de interfaz ni de
reproducción. Sirve para que la OTA siga funcionando cuando el repositorio de código pase a
privado.

## Qué cambia

- La app ahora busca actualizaciones en el repositorio público **Vision-Releases**.
- El launcher (Vision+ Home 1.3.17) también busca sus actualizaciones ahí.
- El APK y el launcher siguen firmados con la **misma llave histórica**, así que se instalan
  encima de la versión actual sin perder datos, perfiles, favoritos, historial ni descargas.

## Cómo actualizar

1. Abre Vision+ y espera el aviso de actualización.
2. Si no aparece, descarga e instala este APK manualmente desde
   `https://github.com/ChronosBVRX/Vision-Releases/releases/latest`.
3. En la tele, si usas Vision+ Home, deja que el launcher se actualice solo o instala
   `VisionPlusHome.apk` del mismo release.

## Notas

- Versión: **2.5.246** (versionCode **486**).
- Launcher: **1.3.17** (versionCode **40**).
- Firma: histórica `9593cb0d…`.
- El repositorio anterior queda como fuente privada de código; las descargas y la OTA viven
  únicamente en `Vision-Releases`.

— ChronosBVRX
