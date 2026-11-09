# Vision+ 2.6.2 — estado que se puede usar

Esta versión no cambia catálogo ni reproductor. Acomoda lo que pesaba más de la experiencia:
el estado de consumo pasa a ser **real, editable y consistente** entre móvil, TV, ficha, launcher
y notificaciones.

## Seguir viendo e Historial con acciones

- **OK largo / mantener presionado** en una tarjeta de «Seguir viendo» o Historial abre el menú de estado:
  - **Reanudar** o **Volver a ver**.
  - **Desde el inicio**.
  - **Siguiente capítulo**, cuando existe.
  - **Marcar como visto / no visto**.
  - **Quitar de Seguir viendo** (solo esa tarjeta; no borra favoritos ni ficha).
- «Marcar como visto» ya no depende de que el motor reporte el último cuadro; es una marca
  explícita del usuario y se sincroniza.
- El **Historial** permite borrar una entrada suelta; el «vaciar historial completo» sigue en Ajustes.

## Siguiente capítulo que no se pierde

- Al terminar el último capítulo de una temporada, la ficha **aterriza ya en la temporada nueva**;
  no vuelve a decir «Continuar en T1 · C1».
- La fila «Seguir viendo», la ficha, el launcher **Vision+ Home** y el canal Watch Next de Android TV
  usan la **misma lógica** para el capítulo siguiente.
- Si el último capítulo quedó a medias, la ficha se queda en su temporada para ofrecerte
  «Continuar donde te quedaste»; no te adelanta de temporada sin querer.

## Mis series

- Nueva pestaña **«Mis series»** dentro de Mi lista en móvil y TV:
  - Series que sigues, con su póster y ficha.
  - Puedes **dejar de seguir** sin entrar a la ficha (móvil: pulsación larga; TV: OK largo).
- Si el catálogo todavía no carga la serie, no desaparece: mantiene el póster/título que guardó
  al seguirla.

## En vivo con memoria

- **Canales recientes** por perfil:
  - En móvil: fila **«Recientes»** arriba.
  - En TV: botón **«Seguir viendo»** en la cabecera de En vivo.
- El zapping y el primer canal actualizan la memoria.

## Notificaciones que te llevan directo

- Aviso de capítulo nuevo → abre la ficha de la serie.
- Aviso de partido → abre la pantalla de Deportes en ese evento.
- Sin link → abre la app como antes.
- Cada notificación ya no pisa a la anterior al abrir.

## Limpieza

- Las regresiones en Deportes en vivo ya no dependen de la agenda real del VPS; los correos de
  pruebas son estables.
- Se agregan guardianes de regresión para estos estados (Android, launcher, backend).

—
Vision+ · por ChronosBVRX
