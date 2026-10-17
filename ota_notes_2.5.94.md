# OTA v2.5.94 — Reloj del centro de México en todo el menú + agenda deportiva en horario central (+ el arreglo de fuentes de «Deportes en vivo» ya aplicado en el servidor)

## Qué trae la app
- **Reloj en todo el menú**: una píldora discreta arriba al centro con la hora del
  **centro de México** (p. ej. «07:33 CDMX»), en móvil y Smart TV. Se calcula SIEMPRE
  en la zona `America/Mexico_City`, aunque el equipo esté configurado en otra zona
  horaria (probado en un emulador en otra zona: la app mostraba la hora del centro).
- **Se oculta solo cuando debe**: en los reproductores (ahí manda el contenido) y en el
  buscador (el teclado ocupa la parte superior). No tiene foco ni toques: jamás se roba
  el D-pad ni estorba a los controles.
- **Agenda deportiva anclada al horario central**: los chips de días («Hoy/Mañana/Lun 19»)
  y las horas de los partidos se calculan en la zona del centro de México (el servidor ya
  publica los horarios en esa zona): la agenda coincide siempre, sin importar la zona del
  equipo.

## Arreglo DEFINITIVO de las fuentes de cada partido (en el servidor — ya aplicado, sin OTA)
- *Qué pasaba*: cada partido del CMS de GeeSports trae su lista de fuentes (`link_names`)
  con un archivo `pro/…` de URLs, y **el CMS publica VARIAS entradas por partido**: la del
  día puede traer 1 enlace muerto mientras la buena (visible, de otro día) trae la lista
  completa. Nuestra tarjeta usaba solo la entrada del día → «pocas opciones y no reproducen».
- *Cómo asigna GeeSports sus canales (analizado en su APK)*: su app muestra `link_names`
  del evento y al tocar una fuente reproduce `pro[índice]` resuelto fresco; su lista es la
  del **mismo cruce de equipos** (Everton-Chelsea: TNT1UK, FUBO, USA TV, beIN, SKY…).
- *El arreglo*: el worker y el API ahora **fucionan los enlaces de TODAS las entradas del
  mismo cruce** (sin duplicar por URL, vivos primero) y **sondean la lista final cada
  ciclo** (badges «EN VIVO/Caído» del momento), con una segunda pasada que cubre los
  partidos que el CMS rota fuera de su archivo.
- *Resultado verificado en vivo*: Everton-Chelsea pasó de **1 enlace muerto a 10 fuentes
  (4 EN VIVO)** — la misma lista de la app original — y Espanyol-Atlético de 0 a 8 (5
  vivas). Hoy: **50 partidos de fútbol con enlaces y 36 con al menos una señal EN VIVO**.
  Los enlaces DASH viajan con su llave ClearKey (reproducen en la app tal cual).

## Verificación
- **185 pruebas backend** (incluye `_merge_sibling_links`, clave de cruce y la segunda
  pasada del worker) + **451 pruebas Android** en verde.
- Probado en emulador: reloj visible en Inicio y Deportes, oculto en el buscador; la
  agenda y el reloj en hora del centro (emulador en otra zona horaria).
- Servidor desplegado y verificado en vivo (respaldos `*.bak-*` en el VPS).
- Firma histórica `9593cb0d…`, versionCode 330.

— ChronosBVRX
