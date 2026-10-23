# Vision+ 2.5.123

**Estabilidad del reproductor**
- El motor se copia ahora de forma atómica y con sello (versión + tamaño de cada DEX). Si una copia quedó a medias, se vuelve a copiar sola; si una clase no verifica, se borra el motor extraído y se estrena una vez. Esto cierra el `VerifyError` que dejó una tele sin poder reproducir nada.
- La sesión del portal ya no entra en bucle cuando el portal la rechaza: tras un intento fallido hay 60 s de espera. Antes, cada llamada del Inicio volvía a pedir sesión (121 avisos en 5 minutos desde un solo equipo).

**Calidad medible**
- La app manda un latido por apertura y lo une a cada crash: el servidor calcula el **% de aperturas sin crash** y avisa si baja del 99 % con muestra suficiente.
- Las anomalías repetidas se cuentan y se reportan una vez por minuto; los latidos van en su propia cola y nunca desalojan un crash pendiente.

**Mi lista**
- Pestaña «Historial» con el avance de cada título y «Continuar en mm:ss». Los terminados quedan como «Visto» y ya no se mezclan con «Seguir viendo».

**Servidor (ya aplicado en el VPS)**
- `/v1/diag/health` y vigilancia del objetivo de calidad en el triage.

Verificado: 816 tests Android + 333 backend en verde; release R8 con la firma histórica.

— ChronosBVRX
