# Vision+ 2.5.117 — continuidad real entre el teléfono y la tele, y el reloj móvil ya no estorba

- **«Ver en la TV» te deja en el minuto exacto.** Si estabas viendo una película o un capítulo en el teléfono, al mandarlo a la tele continúa donde ibas, no desde el principio ni desde el minuto que tenía guardado la tele. La orden del teléfono manda.
- **Series: capítulo exacto.** El selector de temporada y capítulo sigue apareciendo, y el capítulo elegido viaja con **su** minuto y con el contexto de su serie para que el «siguiente» funcione sin volver al teléfono.
- **El vivo no se reanuda** (no tendría sentido): canales y deportes abren en directo como siempre.
- **Continuidad sin cuentas.** En Ajustes → **Continuidad entre equipos** generas un código de 8 caracteres y lo compartes una vez con tu tele (o se lo mandas desde el control remoto si ya están emparejados). No hay registro, ni correo, ni contraseña.
- **Qué se sincroniza:** «Seguir viendo», Favoritos, Ver más tarde y los capítulos marcados como vistos. Se une lo de ambos equipos y gana el cambio más reciente; una serie se queda con su capítulo más nuevo, no con uno por cada equipo.
- **Los borrados también viajan.** Si quitas un favorito o vacías el historial en un equipo, desaparece en el otro. No vuelven a aparecer «fantasmas».
- **Se refresca solo.** Al volver a la app se empuja lo nuevo y se trae lo del otro equipo; la fila «Seguir viendo» del Inicio se actualiza en cuanto termina la sincronización, sin tener que cerrar y abrir.
- **Si no hay red, nada se bloquea.** La reproducción y el menú siguen igual; el siguiente intento reconcilia. Y si no configuras ningún código, la app funciona exactamente como antes.
- **El reloj del móvil es discreto.** La hora ya no flota en una píldora sobre la barra ni sobre el arte de las fichas: ahora es texto pequeño en la barra superior, sin fondo y sin «CDMX». La tele conserva su píldora de siempre.
- **Compatibilidad hacia atrás.** Una tele con una versión anterior ignora el minuto y usa su propio historial; un teléfono anterior simplemente no lo manda. Nada se rompe.
- **Servidor listo y reforzado.** La sincronización corre en nuestro VPS por HTTPS, el código se guarda **hasheado** (nunca en claro) y el índice quedó acotado para no castigar la memoria del servidor.
- Guardianes: **746 pruebas Android + 294 del servidor** en verde; release R8 con la firma histórica, 101.1 MB.

— ChronosBVRX
