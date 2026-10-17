# Vision+ 2.5.98 — Control remoto: tu teléfono como mando de la tele

Desde el teléfono ya manejas la tele de la sala: basta con que las dos estén en la misma red Wi-Fi y que la tele tenga Vision+ abierto.

## Cómo se empareja (una sola vez)
- La app **encuentra la tele sola** en la red (no hay que escribir IPs ni códigos raros).
- La primera vez, la tele muestra un **PIN de 4 dígitos** y el teléfono lo escribe una vez; a partir de ahí entra **directo y en silencio**, para siempre.
- El PIN solo aparece en la tele **cuando un teléfono lo pide**, y cada tele guarda su propio permiso.

## El mando, completo
- **D-pad** (arriba/abajo/izquierda/derecha/OK), **Atrás**, **Menú** e **Información**.
- **Reproducción**: play/pausa, retroceder y adelantar 10 segundos.
- **Canales en vivo**: canal siguiente y anterior.
- **Volumen**: subir, bajar y silenciar.
- La pantalla del teléfono te dice **qué está viendo la tele** en ese momento.

## Abrir contenido en la tele desde el teléfono
- **Buscador «Abrir en la tele»** en la propia pantalla del control: escribes la película, serie o canal y se abre en la tele al instante.
- En la ficha de cualquier título del teléfono aparece **«Ver en la TV»** cuando hay una tele conectada (nunca un botón muerto): lo que estabas viendo se pone en la tele de un toque.
- En canales en vivo la orden entra directo al canal, sin bucear con el mando.

## Seguridad y calma
- Solo viajan las teclas permitidas y las órdenes de abrir contenido; **ningún acceso al sistema** de la tele.
- El transporte es directo entre tus dispositivos en la misma red: nada sale a internet ni pasa por servidores.

## Ámbito
- Es una función **solo para el teléfono**: la tele escucha y obedece; nada cambia en la interfaz ni en el reproductor de la tele.
- Todo lo existente sigue igual (reproductores, descargas, Deportes en vivo).

## Pruebas
- 528 pruebas unitarias en verde (nuevas: protocolo y llaves permitidas, emparejamiento y PIN, descubrimiento, apertura de contenido, el buscador del control y una prueba de punta a punta del servidor de la tele sobre sockets reales).

## Verificado
- Emulador con la tele simulada en el PC: emparejamiento con PIN, reconexión silenciosa con el permiso guardado, todas las teclas llegando a la pantalla y la orden de abrir un título con su referencia, título y póster correctos.
- Prueba física teléfono + tele en la red de casa pendiente de confirmar en campo (el flujo es el mismo verificado en emulador).

— ChronosBVRX
