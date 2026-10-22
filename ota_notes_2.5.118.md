# Vision+ 2.5.118 — arreglo urgente: la app vuelve a reproducir (2.5.117 cerraba el reproductor)

- **Qué pasaba.** En la versión de ayer (2.5.117) el reproductor se cerraba solo al abrir cualquier película o capítulo: la pantalla del título volvía al menú y no se veía nada. No era la red, ni tu cuenta, ni el portal: era un choque interno de nombres dentro del APK.
- **Por qué.** El motor de video que va dentro de la app trae sus propias clases y nosotros comprimimos el código al compilar. En 2.5.117 el compresor bautizó una clase nuestra (`IndexedValue`, de la biblioteca de Kotlin) con el mismo nombre que una del motor (`k4.x`). El motor, al empezar a reproducir, tomaba **nuestra** clase en vez de la suya y moría con `NoSuchMethodError`. Sin motor no hay video: de ahí que «no dejara reproducir nada».
- **Cómo se arregló (de raíz).** Ahora todo el código que comprimimos vive en su propio espacio de nombres (`visionplus.*`), que el motor no usa, así que no puede volver a taparlo. Esto ya había pasado una vez (v2.5.78, con otra clase): en vez de blindar una clase suelta, se cerró la puerta completa.
- **Guardián nuevo.** La compilación de release ahora **falla sola** si alguna clase comprimida vuelve a coincidir con un nombre del motor: lee los 3 DEX del motor y el mapa de R8 antes de publicar. Este tipo de error ya no puede llegar a una OTA.
- **Comprobado sobre el binario, no de palabra.** El APK de 2.5.117 definía 719 nombres que pertenecen al motor (incluido `k4.x`); el nuevo define solo 14, que son clases de librería que ya compartía la 2.5.116 (la que reproducía bien) y ninguna interviene al reproducir. `k4.x` queda libre y 682 nombres liberados: cero nombres nuevos invadidos respecto a la versión que funcionaba.
- **Nada más cambia.** Mismo catálogo, mismos ajustes, misma continuidad entre equipos, mismos deportes en vivo, mismo modo sin conexión. Solo se corrige el empaquetado del APK.
- **Si la tele o el teléfono no se actualizan solos,** abre la app: el aviso de actualización aparece en la primera pantalla y se instala sin salir de Vision+.
- Guardianes: **746 pruebas Android + 294 del servidor** en verde; release R8 con la firma histórica, 101.1 MB.

— ChronosBVRX
