# Vision+ 2.5.155 — «Inicio en la TV»: emparejamiento que no se cuelga

**El diálogo ya no se queda en «Emparejando…»**
- La librería ADB podía quedarse esperando aunque la tele **ya hubiera emparejado**. Ahora el emparejamiento tiene **tope real** y, si no confirma, la app **prueba a conectar igual**: si la tele emparejó, el flujo sigue.
- El diálogo se puede **cerrar o cancelar en cualquier momento** (botón «Cancelar»), y al cerrarlo se corta el trabajo en curso.

**Recuerda lo de esta tanda**
- **«Detalles»** muestra cada comando y lo que contestó la tele.
- Si falta el launcher, **se instala solo** desde el teléfono por ADB.
- **Varias teles** con Aplicar / Restaurar / Olvidar por separado.
- Se corrigió la lectura de respuestas de ADB (perdía la salida de los comandos y por eso «no aplicaba nada»).

— ChronosBVRX
