# Vision+ v2.5.67 — Fix: el D-pad ya no pierde el foco al volver de una serie o película (Smart TV)

## El problema
En la TV, al abrir una serie (o película) desde el Inicio y presionar **Atrás**, el control remoto
quedaba **muerto**: se podía ver el Inicio pero las flechas y el OK ya no movían nada, hasta
reiniciar la app.

## La causa
Desde la v2.5.60 el Inicio arma su contenido (héroe, Top 10, Novedades y las filas propias) **en
segundo plano**, porque el índice trae decenas de miles de títulos. En las TVs de gama baja ese
curado tarda más que los reintentos que restauraban el foco al regresar (menos de 1 segundo) y que
el destino de la pantalla (3 segundos). Y el rescate de «llegó el contenido y nadie tiene el foco»
quedaba bloqueado por el id de retorno, que no se limpia durante la sesión. Resultado: cuando las
filas por fin aparecían, **nadie volvía a pedir el foco** y el mando se quedaba sin nada enfocado.

## El arreglo
- Nuevo guardián compartido del regreso: vuelve a pedir la **tarjeta exacta** que abrió la ficha
  mientras la pantalla siga sin foco, y **se re-arma cada vez que llega contenido nuevo** (las filas
  se pintan en segundo plano).
- Si la tarjeta no aparece (fila fuera de pantalla, contenido que ya no existe), el foco cae al
  destino de la pantalla (el botón del héroe): el mando **nunca** se queda muerto.
- Mismo blindaje en Categorías, secciones del riel (Películas/Series/Novelas/Anime), Buscar y
  Mi lista, que tenían el mismo patrón frágil.
- El regreso solo aplica cuando de verdad se llegó abriendo una ficha (no cuando se navega con el
  riel, que tiene su propio destino de foco).

## Verificación
- 2 pruebas nuevas del regreso: el flujo real (Inicio → ficha de serie → Atrás → navegar) y una que
  simula la TV real (el contenido llega **después** del regreso). Con el código anterior esa prueba
  falla y reproduce el mando muerto; con el arreglo pasa.
- **282 pruebas Android en verde** (suite completa).

Instalación OTA: la app se actualiza sola (Ajustes → Actualizar), o descarga directa:
`https://github.com/ChronosBVRX/Vision-/releases/latest/download/VisionPlus.apk`
