# Vision+ 2.5.107 — El mando que ya no se pierde

Una pasada de blindaje sobre TODA la navegación con el mando (Smart TV): la red de seguridad del foco ahora comprueba que de verdad puede aterrizar antes de usar una tecla, cada pantalla y cada modo tiene un destino compuesto, y los diálogos, las teclas sostenidas y el control remoto dejan de poder matar el mando.

## El rescate del foco ya verifica el aterrizaje

- **Fin de la tecla «comida» en silencio**: cuando el foco quedaba vacío y la red de seguridad saltaba, antes podía pedir el foco a un elemento que ya no existía y, aun así, consumir la tecla — la pantalla parecía muerta hasta Atrás. Ahora el rescate prueba los destinos **de verdad**: si el primero no puede tomar el foco, sigue con el siguiente y, si ninguno puede, **la tecla sigue su curso normal**.
- **La bandera de «estoy compuesto» ya es real**: cada elemento de aterrizaje avisa cuándo entra y cuándo sale de pantalla; el rescate jamás intenta un destino que no está dibujado (el riel de Inicio, por ejemplo, ya no registra su destino en las pantallas que no lo tienen).
- **Telemetría nueva**: si algún día una pantalla se quedara sin destino, queda registrado («el rescate no pudo aterrizar») con su pantalla, para corregirlo sin adivinar.

## Cada pantalla y cada modo tiene dónde aterrizar

- **Deportes en vivo**: con la agenda aún vacía (al entrar en frío) o con un filtro sin eventos, la pantalla se quedaba sin nada enfocado. Ahora el aterrizaje cae en el primer chip (o en «Canales») y, **cuando los resultados llegan, el foco salta solo** al mosaico.
- **Secciones (Películas/Series/…), modo cuadrícula**: al pulsar OK en un chip, la fila de chips se rehace y el foco se perdía. Ahora el modo cuadrícula aterriza en «Volver a Todo» y, al volver a destacados, otra vez en el héroe.
- **Buscador**: al volver de una ficha, el foco regresa a la tarjeta que abriste (o a la primera de los resultados reales de tu búsqueda), no al teclado.
- **Inicio**: el orden de las filas ahora es **una sola fuente** para pintar y para el regreso del foco — agregar una fila nueva ya no puede des-incronizar el regreso (el fallo que dejó el mando muerto al llegar «Populares de la semana»).

## Reproductores y control remoto

- **Teclas sostenidas**: sostener play/pausa ya no alterna reproducción en cadena; sostener ±/canal ya no recorre la lista de canales ni salta varios capítulos; el zapping vivo con flecha sostenida queda a un canal por toque.
- **Un mando que no manda el «soltar»**: ya no puede dejar una tecla muerta — a los ~1.2 s el estado se libera solo.
- **Controles del reproductor**: el foco del menú se reintenta un par de veces si la petición se perdió mientras el motor arrancaba (menos «tuve que volver a picar»).
- **Control remoto (teléfono → tele)**: si la tele ya está en el reproductor, la tecla viaja directo al reproductor — jamás se aplica a un menú invisible.
- **Diálogos**: todos avisan al sistema de foco mientras están abiertos; un guardián automático impide que un diálogo nuevo se olvide.

## Pruebas y verificación

- **619 pruebas** de Android en verde (7 nuevas): el rescate que salta destinos muertos y aterriza en uno real, el chip que entra a la cuadrícula sin morir, Deportes con el mando siempre vivo, el orden único de filas del Inicio, el vencimiento del «trago» de teclas, el guardián de diálogos y el ruteo del control remoto.
- **Robolectric (la app TV real compuesta)**: regreso de ficha/sección/categoría a la MISMA tarjeta, regreso con contenido tardío, plausos de cada pantalla con flechas, y el caso nuevo del chip → cuadrícula → vuelta.
- **Pendiente de sentir en la tele**: la batería física con el mando (Inicio → sección → chip → cuadrícula → Atrás; Deportes en frío; volver de ficha/serie; abrir/cerrar cada modal; reproductor; control remoto) — mismos flujos que ya cubre la suite.

— ChronosBVRX
