# Vision+ 2.5.171 + Home 1.3.1 — un launcher que se arma a tu manera

## Lo nuevo de esta versión

- **Vision+ Home — pestañas a tu gusto**:
  - Nuevo gestor **Personalizar Inicio → Pestañas**: con el mando cambias el **orden** de la barra (Arriba/Abajo) y **ocultas** las que no uses (el ojo). **Inicio nunca se oculta ni se mueve**: es la puerta de vuelta.
  - El orden y las pestañas ocultas **se guardan por perfil**. Las pestañas nuevas que traiga una versión futura aparecen al final, sin desaparecer nada de lo que ya tenías.
  - La pestaña **Entradas** sigue apareciendo **solo si el equipo reporta sintonizador o HDMI**; en una TV box o en una PC no se muestra (ni se ofrece como pestaña inicial).

- **Vision+ Home — plantillas de Inicio**:
  - Nuevo **Personalizar Inicio → Plantillas**: **TV primero**, **Cine**, **Solo lo mío** y **Niños**. Cada una deja en un toque el orden de las filas y de las pestañas, y oculta lo que sobra:
    - **TV primero**: Seguir viendo, Mis canales, TV en vivo y Entradas al frente.
    - **Cine**: Top 10, Estrenos y el catálogo primero; la TV en vivo se aparta.
    - **Solo lo mío**: Seguir viendo, Mis canales y tus apps, sin catálogo de relleno.
    - **Niños**: Infantil y anime primero, con las secciones de adultos fuera.
  - **No toca tus favoritos, tus canales ocultos ni la pestaña inicial**, y se deshace con **Restablecer personalización**.

- **Limpieza del reproductor**:
  - Se retiró el diálogo **«Corregir intro y outro»** que solo aparentaba corregir los marcadores (el reproductor nunca lo usaba). Los **marcadores reales** (Omitir intro / resumen / créditos de las bases comunitarias) funcionan exactamente igual; no hay trabajo manual.

## Todo lo que llega desde la estable anterior

- Se mantiene **todo lo de la 2.5.170**: TV en vivo **se ve al primer intento** (sin cambiar de canal), el canal conserva el menú desde el que lo abriste (zapping y «Canales» dentro de esa fila), y todo lo de la 2.5.169 (D-pad completo en la tele y en el launcher, «Omitir intro» y elegir minuto desde el móvil, perfiles, continuidad entre equipos, subtítulos del proveedor).
- **Vision+ Home 1.3.1** incluye además lo de la 1.3.0: **sus propios Ajustes** (Inicio, Entradas, Sistema, Actualizaciones, Acerca de), la pestaña y la fila **Entradas** con el sintonizador y los HDMI reales de la tele, y la nota honesta cuando el equipo no tiene entradas.

## Pruebas

**994 pruebas Android + 153 del launcher** en verde. Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3); launcher 1.3.1 firmado con la misma llave. Guardianes nuevos: pestañas (orden, ocultar, Inicio fijo, JSON por perfil), plantillas (filas/pestañas de cada una y que no toquen favoritos), y que el diálogo placebo ya no exista.

Actualiza desde el aviso de la app (o desde Ajustes → «Buscar actualizaciones»). El launcher se actualiza desde su Centro de control o desde Ajustes → Sistema → Vision+ Home.

— ChronosBVRX
