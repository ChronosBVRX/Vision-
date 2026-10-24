# Vision+ 2.5.132

**Dar Play en la tele se siente más rápido**
- El proceso del reproductor (`:x4k`) ya **no inicializa lo que solo usa la interfaz**: canal de avisos, modo niños y la config remota con su hilo de red se quedan en el proceso principal. Todo lo que el motor necesita (registro offline, identidad del portal, diagnósticos) sigue igual en ambos.
- La **sesión/SLB y la ficha del título se piden en paralelo**, no una tras otra. En una caja que paga 0.2–0.7 s por llamada, era una ida y vuelta regalada en cada reproducción.
- El **sha256 del motor ya no se lee en cada arranque** del reproductor (eran 17 MB, hasta ~1 s en una tele lenta): ahora se calcula **mientras se copia** y solo se vuelve a verificar cuando hubo un fallo previo. En régimen sano no cuesta nada.

**Para poder medirlo (sin adivinar)**
- `PLAYBACK_PREP`: solo si preparar el play pasa de 2.5 s, con el desglose `slbMs / metaMs / licenseMs`.
- `PLAYER_START`: solo si el primer cuadro pasa de 3 s, con `firstFrameMs` y `bootMs` (0 = motor ya caliente y reutilizado).

Con eso, en la próxima reproducción lenta de la tele sabremos si el tiempo se va en el portal, en el arranque del motor o en la señal.

**Sin cambios** en el reproductor nativo, en la extracción por generaciones ni en la reproducción en sí: es arranque y red.

Verificado: suite Android en verde; release R8 con la firma histórica.

— ChronosBVRX
