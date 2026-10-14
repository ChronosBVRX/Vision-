# Vision+ v2.5.68 — Fix definitivo: el D-pad ya no se pierde al volver de una serie en «Recomendado para ti»

## El caso (reportado en la TV)
Abrir una serie desde la fila **«Recomendado para ti»** de una sección del catálogo (por ejemplo
**Series**), ver su selector de temporadas y presionar **Atrás**: el mando quedaba muerto.

## La causa exacta
Esa fila —y también «Mejor valoradas» y «Novedades»— se dibujaba **sin objetivo de regreso**: al
volver, el sistema de foco no tenía ninguna tarjeta a la que volver y caía al botón del héroe; si
además venías con la vista desplazada (el héroe ya no está dibujado), **no quedaba nada enfocado** y
las flechas del control dejaban de responder.

## El arreglo
- **Todas** las filas del catálogo (secciones, categorías y la cuadrícula) ahora registran su
  tarjeta de regreso, no solo algunas: el foco vuelve a la **misma ficha** que abriste.
- Rescate nuevo: si la tarjeta exacta ya no existe (el índice se refrescó) o su fila quedó fuera de
  pantalla, el foco cae a la **primera tarjeta visible** de cualquier fila dibujada. El mando nunca
  se queda sin nada enfocado.
- El primer intento del regreso es incondicional (no depende de señales de foco que puedan venir
  tarde en la TV).
- **Nuevo diagnóstico**: si alguna vez el foco no puede caer en ningún lado, la app lo reporta al
  servidor (`FOCUS_LOST` con pantalla, intentos y contexto) para encontrarlo sin adivinar.
- Blindaje con pruebas: 3 pruebas nuevas del regreso (sección «Recomendado para ti», fila lejana con
  scroll horizontal, cuadrículas). Con el código anterior la prueba del caso reportado **falla** y
  reproduce el mando muerto; con el arreglo pasa. **285 pruebas Android en verde.**

Instalación OTA: la app se actualiza sola (Ajustes → Actualizar), o descarga directa:
`https://github.com/ChronosBVRX/Vision-/releases/latest/download/VisionPlus.apk`
