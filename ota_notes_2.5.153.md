# Vision+ 2.5.153 — «Inicio en la TV»: varias teles y diagnóstico visible

**El aplicador ya no se ata a una sola tele**
- En **Ajustes → «Inicio en la TV»** hay una **lista de teles**: cada una con su estado («Vision+ Home es su Inicio», «Emparejada» o «Sin configurar»).
- Puedes emparejar todas las que quieras: **«Agregar otra tele»** repite el flujo (depuración inalámbrica + código de 6 dígitos una vez por televisor).
- Cada tele guarda **su** Inicio original y **sus** apps retiradas. El teléfono las reconoce por su identificador del sistema, no por la IP.

**Y se puede borrar una tele**
- **«Olvidar»** quita esa tele de la lista sin tocar las demás.
- Si tiene cambios hechos desde el teléfono, el aviso ofrece **«Restaurar y olvidar»** (devuelve su Inicio y sus apps antes de quitarla) o «Olvidar igual».

**Si la tele se resistía, ahora se ve por qué**
- El flujo muestra **«Detalles»** con cada paso y lo que contestó el televisor (puertos ADB, conexión, si abre el Inicio, qué app lo tapa…).
- Cada comando tiene **tope de tiempo**: la app ya no se queda «pensando» para siempre.

**Y si falta el launcher, lo instala solo**
- Antes, si el televisor no tenía Vision+ Home, el flujo se detenía para no dejarlo sin pantalla. Ahora **lo instala desde el teléfono** por ADB (el APK viaja dentro de la app) y sigue.

**Lo demás sigue igual**
- **«Aplicar»** reaplica sin pedir código y **«Restaurar»** devuelve esa tele como estaba.
- Candados: nunca desactiva Ajustes, System UI ni el instalador.

— ChronosBVRX
