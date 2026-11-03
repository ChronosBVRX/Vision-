# Vision+ 2.6.0-beta1 — Descargas portátiles con PIN

**Esta es una beta de pruebas.** No cambia la versión estable: `/releases/latest` sigue en 2.5.245 y la app se puede volver a instalar encima sin perder datos.

## Lo nuevo

- Las descargas nuevas pueden viajar en una **memoria USB** y abrirse en otra TV con Vision+ aunque esa TV no tenga internet.
- La primera descarga portátil de una USB genera un **PIN de 4 dígitos** para esa memoria. Las siguientes descargas de la misma USB reutilizan el mismo PIN.
- En la TV que descarga, el PIN se puede volver a ver desde **Descargas**: en la tarjeta de la película aparece el sello **PORTÁTIL** y un botón con candado.
- En la TV sin internet, al conectar la USB aparece la sección **Biblioteca portátil · PIN requerido**. Al abrir un título se pide el PIN y, si es correcto, se reproduce desde la USB.
- La TV que descargó puede estar apagada. La TV de destino no necesita internet para validar el PIN.
- La TV de destino recuerda la llave de esa USB: si desconectas y vuelves a conectar la memoria en la misma TV, ya no pide el PIN.

## Cómo probarla

1. Instala esta beta en la TV que tiene internet y en la TV de prueba.
2. En la TV con internet, conecta una USB y descarga una película o un capítulo.
3. Al terminar, entra a **Mi lista → Descargas**. En la tarjeta debe aparecer **PORTÁTIL**. Pulsa el candado para ver el PIN y anótalo.
4. Lleva la USB a la TV sin internet (o ponla en modo avión) y abre **Mi lista → Descargas**.
5. Debe aparecer **Biblioteca portátil · PIN requerido**. Abre el título, escribe el PIN y reproduce.
6. Prueba pausa, adelantar/atrasar, salir y volver a entrar, y desconectar/conectar la USB.

## Límites de esta beta

- Solo aplica a **descargas nuevas**. Las descargas `.vpack` que ya existían siguen atadas a su TV; para llevarlas habría que borrarlas y volver a descargarlas.
- La TV de destino debe tener esta misma beta o una versión posterior. Si está sin internet, actualízala antes con el APK por USB.
- El PIN de 4 dígitos es una protección de acceso casual. Si alguien se lleva la USB y tiene la app, podría intentar abrirla; el contenido sigue cifrado y no hay video en claro.
- La USB debe ser almacenamiento extraíble normal (FAT32/exFAT), no almacenamiento interno adoptado.
- Si se pierde el PIN, se puede volver a la TV de origen para generar uno nuevo mientras la biblioteca siga en la USB.

## Estado técnico

- 1,295 pruebas Android en verde (3 omitidas), incluidas las nuevas de cifrado portátil, PIN, escaneo, importación y reproducción desde `.pvpack`.
- Probado en Chromecast HD: detección de la biblioteca, PIN, desbloqueo y reproducción de video/audio.
- Pendiente de probar con una USB física en dos TVs y con varias películas. Por eso es beta.

— ChronosBVRX
