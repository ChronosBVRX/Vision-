# Vision+ 2.5.152 — HOTFIX (arregla el arranque de la 2.5.151)

**La app vuelve a abrir**
- La 2.5.151 no arrancaba: al meter el motor de video en el APK se sobrescribió un archivo de código de la propia app. Esta versión lo conserva y el motor ahora se suma aparte.
- Si vienes de la 2.5.151, instala esta versión **encima** (misma firma, no se pierde nada).

**Incluye lo de la 2.5.151**

**El Inicio de la tele se configura desde el teléfono, sin PC**
- Ajustes (teléfono) → **Inicio en la TV**: activa la depuración inalámbrica en la tele, escribe el código de 6 dígitos **una sola vez** y la app hace el resto.
- Empareja por ADB, deja **Vision+ Home** como Inicio y, si el televisor sigue abriendo su launcher de fábrica, lo retira.
- **«Aplicar otra vez»** reaplica sin pedir código y **«Restaurar la tele»** devuelve todo como estaba.
- En televisores Android 9/10 se usa el ADB de red: acepta el aviso de depuración en la tele.

**Vision+ Home 1.0.14: el estado del Inicio ya no miente**
- El centro de control dice **quién abre la tele de verdad** («Ahora abre: …»).
- «Poner Vision+ Home como Inicio» abre la **pantalla real del sistema**; antes podía quedarse en nada.
- Si el televisor no tiene selector de Inicio, aparece una **guía de 3 pasos** (con «Borrar predeterminados») y un atajo a los ajustes del launcher actual.

— ChronosBVRX
