# Vision+ 2.5.109 — «También: …» en el buscador y el control remoto busca en todo el catálogo

Dos mejoras del buscador que aprovechan el catálogo completo del servidor (el mismo de la app original: «Guerra de Papás», «El padrino» (1972) y miles de títulos que antes no aparecían).

## Qué cambió

- **«También: …» en los resultados**: cuando el buscador encuentra una película o serie por otro de sus nombres (es-MX, inglés o regional), la tarjeta muestra debajo el nombre original. «buscando a nemo» → Finding Nemo · «También: Finding Nemo»; «vaiana» → Moana; «daddy's home» → Guerra de Papás. Así se entiende al instante por qué apareció cada resultado. Está en la búsqueda del teléfono y en la de la Smart TV.
- **El control remoto busca en TODO el catálogo**: el buscador de «Abrir en la tele» miraba solo la copia local (menos títulos); ahora consulta al servidor —el mismo buscador del Inicio— y conserva el resultado local instantáneo mientras llega la respuesta. Encontrar algo para mandarlo a la TV es tan seguro como buscarlo en la tele.
- **Compatibilidad total**: sin conexión o con el servidor lento, todo cae al catálogo local como siempre; las cachés viejas se leen igual (el campo nuevo es opcional).

## Pruebas y verificación

- **632 pruebas** de Android en verde (4 nuevas): el nombre original viaja del servidor a la tarjeta y se omite cuando es el mismo, el campo es opcional (payloads viejos), y el guardián de cableado comprueba que los buscadores de teléfono/TV pintan la línea y que el control remoto usa el servidor con respaldo local.
- **Pendiente de sentir**: buscar en el teléfono y en la tele un título conocido por otro nombre (p. ej. «buscando a nemo») y mandar a la TV un título recién añadido desde «Abrir en la tele».

— ChronosBVRX
