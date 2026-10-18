# Vision+ 2.5.103 — Deportes en vivo: la lista COMPLETA de cada partido (como la app original) y cada canal con su respaldo automático

Una investigación a fondo del caso del **León vs América** dejó al descubierto por qué un partido podía salir con menos fuentes que la app original — y quedó arreglado.

## Qué pasaba (verificado, no adivinado)
- **La fuente de datos (GeeSports) está caída para todos**: su sitio quedó bloqueado por Cloudflare por un aviso legal (DMCA). Nuestro espejo dejó de refrescar (y la app original tampoco puede descargar: en el emulador muestra «sin conexión»). Lo que ves en la app original es su copia en memoria.
- Para este partido la fuente **declara 8 canales** (XTREME + FHD, TUDN US, TUDN US 2, UNIVISION HD, TUDN MEXICO FHD, CANAL MX FHD, HD QUALITY, SD QUALITY) pero **solo 2 traían dirección**: los otros seis se perdían por el camino y la app no los enseñaba.
- Los dos que sí traían dirección (los canales lineales de la fuente) pueden estar emitiendo **otro** partido: lo comprobé en pantalla — «TUDN MEXICO FHD» estaba con Toluca–San Luis.
- Y la parrilla oficial (ESPN) para ese partido decía «Fox Sports · Claro Sports», cuando el canal que lo transmitía era **FOX One** — que sí tenemos, y lo verifiqué en pantalla: **FOX ONE estaba con el León–América**.
- De paso cayó un error nuestro: el respaldo de «Claro Sports» podía caer en «CANAL CLARO HD» (el canal general) en vez del deportivo. Arreglado.

## Qué queda arreglado
- **La lista del partido sale COMPLETA**, con los mismos nombres que enseña la app original: los que la fuente ya sirve, y los que aún declara sin dirección (con su aviso «La fuente aún no publica su señal · respaldo automático»).
- **Cada uno de esos canales se resuelve solo**: al tocar «TUDN US» o «UNIVISION HD» (que la fuente no puede servir), se reproduce el canal **100 % confirmado** de tu catálogo con ese mismo nombre (verificado en el emulador: «TUDN US» abrió nuestro TUDN USA). La fuente sigue mandando: si ella lo sirve, se usa lo suyo.
- **El respaldo es más listo**: los feeds nacionales del mercado hispano cuentan como la misma cadena («Univision East HD» cubre «UNIVISION HD»), y una marca deportiva exige su palabra («Claro Sports» ya no cae en «CANAL CLARO HD»).
- **El arreglo del espejo** (del lote anterior) sigue puesto: si la fuente rota un partido fuera de su archivo, su lista se vuelve a leer.

## Verificado en el emulador
- El selector del León vs América enseña ahora **los 8 canales** con sus estados, y al tocar «TUDN US» abrió solo nuestro canal TUDN USA en el reproductor de TV en vivo.
- FOX ONE (en TV en vivo) **está transmitiendo el León–América** (comprobado en pantalla).

## Pruebas
- **570 pruebas** de Android en verde (nuevas: los nombres declarados sin dirección se listan y al tocarlos se usa su respaldo; los feeds nacionales cuentan como la misma cadena; «Claro Sports» no cae en el canal general).
- **194 pruebas** del servidor en verde (nueva: la tarjeta viaja con los nombres declarados, sin duplicar y sin perder los que sí tienen dirección).

## Nota honesta
Mientras el sitio de la fuente siga bloqueado, su lista no se renueva: lo que se enseña es su última copia buena. El espejo reintenta solo cada ciclo y, en cuanto la fuente publique otro dominio (su Remote Config lo avisa), se cambia automáticamente.

— ChronosBVRX
