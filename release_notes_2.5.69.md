# Vision+ v2.5.69 — El foco vuelve exactamente a la serie/película que abriste

## Lo que faltaba
Con la v2.5.68 el mando ya no se quedaba muerto, pero al volver de una ficha el recuadro de foco
no siempre quedaba **donde tenías la selección**: aparecía en el héroe de la pantalla o en la
primera tarjeta de la fila.

## El arreglo
- **La fila se desplaza sola hasta tu tarjeta.** Al volver, cada fila del catálogo (Inicio,
  Series/Películas/Anime/Novelas, Categorías, Mi lista y las cuadrículas) comprueba si tu tarjeta
  quedó fuera de la ventana (por ejemplo porque el desplazamiento no se restauró) y se mueve hasta
  dejarla a la vista antes de pedir el foco. Si ya estaba visible, no toca nada.
- **El héroe ya no puede robar el foco.** El foco automático de la pantalla (el botón del héroe) no
  lo eligió nadie: la app sigue intentando tu tarjeta exacta hasta que aparezca. En cuanto tú te
  mueves con el mando, deja de intervenir (nunca pelea contigo).
- Igual en las cuadrículas («Todo · …», Mi lista) que ahora recuerdan y revelan tu tarjeta.

## Verificación
- Pruebas nuevas del regreso a la **misma tarjeta**, incluida la variante con la serie al final de
  la fila (desplazamiento horizontal): el foco vuelve a esa tarjeta, no a la primera.
- **286 pruebas Android en verde** (suite completa).

Instalación OTA: la app se actualiza sola (Ajustes → Actualizar), o descarga directa:
`https://github.com/ChronosBVRX/Vision-/releases/latest/download/VisionPlus.apk`
