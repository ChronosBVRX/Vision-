# Calendario global de rotación editorial 2026-2027

> Análisis al **4 de noviembre de 2026**. Este documento parte de las colecciones
> temáticas que ya existen en `server/sync/themes.py` y agrega una capa de
> **semanas especiales** atadas a eventos relevantes del calendario mundial,
> deportivo, cultural y de entretenimiento.

## 1. Lectura ejecutiva

La base actual ya resuelve las temporadas clásicas: Halloween, Día de Muertos,
Navidad, San Valentín, Madres, Padres, Fiestas Patrias, Vacaciones, Regreso a
clases y Año Nuevo. El hueco editorial está en dos lugares:

1. **Del 5 al 30 de noviembre** no hay una campaña fuerte: solo queda «Para reír».
2. El **hero de la pantalla principal** no es estacional; `/v1/banners` elige por
   tendencia semanal, no por fecha.

La propuesta es conservar la base y agregar una capa de **micro-campañas de 7 a
20 días** alrededor de eventos concretos. Reglas:

- **Un solo hero principal** a la vez; las demás campañas activas se muestran como filas.
- **Máximo 3 filas temáticas** en el Inicio para no saturar.
- Una campaña solo se publica si tiene **mínimo 8 títulos reproducibles** (4 en
  Día de Muertos, por la escasez real de obras de la fiesta).
- Si no hay catálogo suficiente, **entra el fallback** («Tendencias» / «Para reír»);
  no se inventan filas.
- Las fechas recurrentes se guardan por **mes/día**; los eventos con fecha variable
  se guardan como **evento de una sola vez**.

## 2. Base existente que se conserva

| ID | Campaña | Ventana | Prioridad | Superficie |
|---|---|---|---:|---|
| `tema-dia-de-muertos` | Día de Muertos | 24 oct – 4 nov | 95 | Hero + fila |
| `tema-halloween` | Halloween | 1 – 31 oct | 85 | Hero + fila |
| `tema-navidad` | Navidad | 1 – 31 dic | 90 | Hero + fila |
| `tema-ano-nuevo` | Año Nuevo | 28 dic – 10 ene | 85 | Hero + fila |
| `tema-san-valentin` | San Valentín | 1 – 15 feb | 70 | Fila + hero secundario |
| `tema-dia-de-las-madres` | Día de las Madres | 1 – 12 may | 75 | Fila + hero |
| `tema-dia-del-padre` | Día del Padre | 1 – 21 jun | 60 | Fila |
| `tema-fiestas-patrias` | Fiestas Patrias | 10 – 20 sep | 80 | Hero + fila |
| `tema-vacaciones` | Vacaciones | 1 jul – 20 ago | 55 | Fila |
| `tema-regreso-a-clases` | Regreso a clases | 1 ago – 10 sep | 55 | Fila |
| `tema-para-reir` | Para reír | Todo el año | 10 | Fila |
| `tema-tendencias-mundo` | Tendencias del mundo | Todo el año | 5 | Solo Categorías |

### Huecos que conviene cubrir con la capa nueva

| Ventana | Situación actual | Campaña propuesta |
|---|---|---|
| 5 – 30 nov | Solo «Para reír» | Otoño / COP31 / Gratitud |
| 11 nov – 31 ene | Sin campaña fuerte después de Año Nuevo | Temporada de premios |
| 15 mar – 30 abr | Sin campaña fuerte | Mujeres / Semana Santa / Tierra |
| 1 may – 31 may | Madres y Padre, pero sin eventos globales | Star Wars / Festivales |
| 1 jun – 30 jun | Sin campaña fuerte | Orgullo / Mundial |
| 15 jul – 15 ago | Vacaciones, pero sin eventos | Mundial / Comic-Con / Eclipse |
| 15 sep – 30 sep | Fiestas Patrias termina el 20 | Herencia hispana |
| 15 oct – 23 oct | Sin campaña fuerte | Ciencia / Rugby-Cricket |

## 3. Radar de coyuntura 2026-2027

| Fecha | Evento | Por qué importa | Campaña |
|---|---|---|---|
| 9 – 20 nov 2026 | **COP31**, Antalya | La conversación climática global del año | Planeta en juego |
| 12 nov 2026 | **Latin Grammy** | Música latina, audiencia directa de Vision+ | Música latina |
| 26 nov 2026 | **Thanksgiving** | Ventana familiar y de maratones en EE. UU. | Familia y gratitud |
| 18 dic 2026 | **Dune: Parte 3 + Avengers: Doomsday** | Choque de blockbusters y conversación global | Semana de sagas |
| 10 ene 2027 | **Golden Globes** | Arranca la temporada de premios | Temporada de premios |
| 6 feb 2027 | **Año Nuevo Lunar** | Fiesta global; año de la Cabra de Fuego | Año Nuevo Lunar |
| 7 feb 2027 | **Grammy** | Música global | Música |
| 14 feb 2027 | **Super Bowl LXI** | Uno de los eventos más vistos del planeta | Super Domingo |
| 14 mar 2027 | **Oscars 99** | Cine y conversación global | Noche de premios |
| 6 jun 2027 | **Elección federal en México** | Relevancia local fuerte | Semana cívica (opcional) |
| 24 jun – 25 jul 2027 | **Mundial Femenil, Brasil** | El evento deportivo global de 2027 | Mundial femenil |
| 19 jun – 17 jul 2027 | **Copa Africana de Naciones** | Fútbol de selecciones, audiencia global | África en juego |
| 23 jul – 8 ago 2027 | **Juegos Panamericanos, Lima** | Ventana latinoamericana | Juegos Panamericanos |
| 2 ago 2027 | **Eclipse total de Sol** | Fenómeno global; España, África y Medio Oriente | Semana del eclipse |
| 1 oct – 21 nov 2027 | **Rugby y Cricket World Cup** | Deportes globales; opcional para LatAm | Deporte global |
| nov 2027 | **COP32**, Etiopía | Segunda conversación climática | Planeta en juego II |

## 4. Calendario propuesto: semanas especiales 2026

| Ventana | Campaña | Prioridad | Superficie | Contenido / fuente |
|---|---|---:|---|---|
| 5 – 30 nov 2026 | **Cine de otoño** | 40 | Hero + fila | Puente entre Día de Muertos y las campañas de fin de año |
| 5 – 10 nov 2026 | **Festival de la luz (Diwali)** | 45 | Fila | Cine de India, familia, color; opcional si hay catálogo |
| 9 – 20 nov 2026 | **Planeta en juego (COP31)** | 72 | Hero + fila | Clima, naturaleza, desastres, ciencia ficción ecológica |
| 9 – 15 nov 2026 | **Música latina (Latin Grammy)** | 58 | Fila | Biopics musicales, conciertos, danza, cine latino |
| 23 – 29 nov 2026 | **Familia y gratitud (Thanksgiving)** | 78 | Hero + fila | Comedia familiar, comida, dramas de gratitud |
| 27 – 30 nov 2026 | **Maratón de sagas (Black Friday)** | 50 | Fila | Franquicias, colecciones y secuelas |
| 14 – 20 dic 2026 | **Semana de sagas** | 92 | Hero + fila | Ciencia ficción, fantasía y franquicias; coincide con Dune 3 / Avengers |
| 21 – 27 dic 2026 | **Navidad** | 90 | Hero + fila | Base existente |
| 28 dic 2026 – 3 ene 2027 | **Fin de año** | 85 | Hero + fila | Base existente (`tema-ano-nuevo`) |

## 5. Calendario propuesto: semanas especiales 2027

| Ventana | Campaña | Prioridad | Superficie | Contenido / fuente |
|---|---|---:|---|---|
| 4 – 6 ene 2027 | **Magia y regalos (Reyes)** | 50 | Fila | Fantasía familiar, magia, cine infantil |
| 8 – 10 ene 2027 | **Temporada de premios (Globos)** | 82 | Hero + fila | Cine de prestigio, biopics, dramas |
| 15 – 18 ene 2027 | **Historia y justicia (MLK)** | 55 | Fila | Historia, derechos civiles, biopics |
| 1 – 7 feb 2027 | **Año Nuevo Lunar** | 86 | Hero + fila | Cine asiático, artes marciales, animación, familia |
| 5 – 7 feb 2027 | **Música (Grammy)** | 65 | Fila | Biopics musicales, documentales, conciertos |
| 8 – 14 feb 2027 | **Super Domingo (Super Bowl LXI)** | 90 | Hero + fila | Deporte, superación, competencia, música |
| 12 – 16 feb 2027 | **Carnaval** | 60 | Fila | Color, música, danza, comedia |
| 1 – 8 mar 2027 | **Mujeres en pantalla** | 75 | Hero + fila | Protagonistas y directoras; Día de la Mujer |
| 8 – 14 mar 2027 | **Noche de premios (Oscars)** | 90 | Hero + fila | Cine galardonado, dramas, cine de autor |
| 22 – 28 mar 2027 | **Semana Santa** | 80 | Hero + fila | Épica, bíblicas, familia, clásicos |
| 19 – 25 abr 2027 | **Planeta (Día de la Tierra)** | 60 | Fila | Naturaleza, documentales, animación ecológica |
| 26 abr – 2 may 2027 | **Infancia (Día del Niño)** | 55 | Fila | Animación, aventuras, cine familiar |
| 1 – 8 may 2027 | **Semana galáctica (Star Wars Day)** | 72 | Hero + fila | Ciencia ficción, espacio, aventura |
| 1 – 10 may 2027 | **Día de las Madres** | 75 | Fila + hero | Base existente |
| 10 – 22 may 2027 | **Mayo de festivales (Eurovisión + Cannes)** | 80 | Hero + fila | Música, cine europeo, glamour, videoclips |
| 1 – 7 jun 2027 | **Semana cívica (elección México)** | 55 | Fila | Política, historia, documental; opcional |
| 1 – 30 jun 2027 | **Orgullo** | 65 | Fila + hero primera semana | Cine LGBTQ+, historias de identidad |
| 14 – 20 jun 2027 | **Día del Padre** | 60 | Fila | Base existente |
| 19 jun – 17 jul 2027 | **África en juego (AFCON)** | 70 | Fila | Fútbol, selecciones, historias africanas |
| 24 jun – 25 jul 2027 | **Mundial femenil** | 95 | Hero + fila | Fútbol femenil, superación, selecciones |
| 19 – 25 jul 2027 | **Cultura pop (Comic-Con)** | 78 | Hero + fila | Superhéroes, fantasía, ciencia ficción |
| 23 jul – 8 ago 2027 | **Juegos Panamericanos** | 65 | Fila | Deporte latinoamericano, historias de superación |
| 30 jul – 8 ago 2027 | **Semana del eclipse** | 85 | Hero + fila | Espacio, astronomía, ciencia ficción |
| 13 – 20 ago 2027 | **Cine mexicano** | 72 | Hero + fila | Cine nacional, Día del Cine Mexicano |
| 10 – 20 sep 2027 | **Fiestas Patrias** | 80 | Hero + fila | Base existente |
| 15 sep – 15 oct 2027 | **Herencia hispana** | 65 | Fila | Cine latino, música, historia |
| 4 – 11 oct 2027 | **Ciencia (Nobel)** | 55 | Fila | Ciencia, biopics, documentales, ciencia ficción |
| 1 oct – 21 nov 2027 | **Deporte global (Rugby + Cricket)** | 45 | Fila | Opcional; solo si hay catálogo suficiente |
| 1 – 31 oct 2027 | **Halloween** | 85 | Hero + fila | Base existente |
| 24 oct – 4 nov 2027 | **Día de Muertos** | 95 | Hero + fila | Base existente |
| 22 – 28 nov 2027 | **Familia y gratitud (Thanksgiving)** | 78 | Hero + fila | Comedia familiar, gratitud |
| 13 – 19 dic 2027 | **Semana de sagas (Secret Wars)** | 90 | Hero + fila | Franquicias, ciencia ficción, fantasía |
| 1 – 31 dic 2027 | **Navidad** | 90 | Hero + fila | Base existente |
| 28 dic 2027 – 3 ene 2028 | **Fin de año** | 85 | Hero + fila | Base existente |

## 6. Reglas de prioridad y composición

1. **Hero único**: solo la campaña activa con mayor prioridad ocupa el hero.
   Si hay empate, gana la ventana más corta y específica.
2. **Filas**: se muestran hasta 3 campañas activas, ordenadas por prioridad.
   Las de prioridad menor a 50 solo aparecen si sobran espacios.
3. **Mínimos**: 3 títulos reproducibles para hero; 8 para fila (4 en Día de Muertos).
4. **Calidad**: póster, `play_ref` y `poster_url` obligatorios; una tarjeta por
   `tmdb_id`; nada de tráilers ni títulos sin match TMDB.
5. **Fallback**: si no hay campaña activa o no alcanza el mínimo, se usa
   «Tendencias» y, si tampoco hay, «Para reír». El Inicio nunca queda vacío.
6. **No repetir**: una misma obra no debe aparecer en el hero y en dos filas a la vez.
7. **No saturar**: durante el Mundial femenil, el eclipse y Comic-Con coinciden;
   el hero rota por prioridad y las otras dos campañas quedan como filas.

## 7. Implementación sobre la base actual

### Servidor

- Mantener `themes.py` para las temporadas base.
- Agregar `server/api/calendar.py` (o una tabla `content_calendar`) con:
  - `id`, `title`, `start`, `end`, `priority`, `theme_id`, `hero`, `row`,
    `min_titles`, `recurring` (anual) o `event` (fecha exacta).
- `themes.build()` consume el calendario y crea las categorías activas.
- `GET /v1/calendar` devuelve `active`, `next`, `endsAt` y `startsAt`.
- `banners.py`:
  - toma la campaña activa de mayor prioridad;
  - pone 3 títulos de su fila al frente del hero;
  - completa con los banners normales;
  - devuelve `nextRefreshAt = min(now + 20 h, fin de campaña)`.
- `themes.build()` debe revisar el cambio de campaña cada 15–30 min, no solo
  cada 6 h, pero **sin volver a consultar TMDB** en cada revisión.

### Clientes

- **Smart TV y Vision+ Home**: ya consumen `/v1/banners`; no necesitan OTA.
- **Móvil**: hoy el hero sale de `Popularity.homeCurated` (Top 10). Necesita
  una OTA pequeña para preferir la campaña activa de `/v1/calendar`.
- **webOS y PC**: consumen el mismo backend; solo hay que mapear la campaña
  activa en su capa de Inicio.

### Datos

- Las campañas de temporada se llenan con TMDB (keywords oficiales) o con
  palabras/frases específicas, como ya hace `themes.py`.
- Las campañas de evento (COP31, Mundial, eclipse) deben usar **keywords
  temáticas y títulos del catálogo**, no listas fijas de películas.
- Si un evento no tiene catálogo suficiente, se marca `row only` o se omite.

## 8. Recomendación de arranque

Para los próximos 60 días, implementar en este orden:

1. **Planeta en juego (COP31)**: 9–20 nov 2026.
2. **Música latina (Latin Grammy)**: 9–15 nov 2026, solo fila.
3. **Familia y gratitud (Thanksgiving)**: 23–29 nov 2026.
4. **Semana de sagas**: 14–20 dic 2026.
5. **Navidad / Fin de año**: base existente.
6. **Temporada de premios**: 8 ene – 14 mar 2027, con micro-heroes para Globos,
   Grammys y Oscars.

Con eso se cubre el hueco de noviembre, se aprovecha el evento cinematográfico
más grande de diciembre y se llega a 2027 con una campaña continua.

## 9. Límites honestos

- **Cricket y rugby** tienen poca presencia en el catálogo latinoamericano;
  se dejan como fila opcional, no como hero.
- **COP32** (Etiopía, noviembre 2027) aún no tiene fechas exactas confirmadas;
  se agenda como ventana tentativa y se ajusta cuando la UNFCCC las publique.
- **Nobel 2027** se anuncia tradicionalmente en la primera quincena de octubre;
  la fecha exacta se confirma cada año.
- **Elección mexicana 2027** es relevante para México, pero es una campaña
  sensible: se propone solo fila y contenido histórico/documental, nunca
  propaganda.
- Las fechas de estrenos cinematográficos pueden moverse; la semana de sagas
  debe validarse contra el calendario de estrenos antes de publicarse.

## 10. Estado de implementación (2026-11-04)

Ya quedó cableado, desplegado y publicado en la beta **2.6.0-beta4 (vc489)** del 2026-11-04. El VPS corre `api` + `sync` con respaldos `*.bak-calendar-20261104-082725`; `/v1/calendar` sirve la campaña activa y `/v1/banners` ya entrega el hero de Día de Muertos.

- `server/sync/themes.py`: temporadas base + `EVENTS` con prioridad, superficie
  (hero/fila), ventanas recurrentes y eventos de fecha exacta. Los eventos usan
  IDs `tema-evento-*` para que las apps y el launcher **actuales** los pinten como
  filas temáticas sin necesitar una OTA.
- `server/sync/editorial_calendar.py`: publica las definiciones en la tabla
  `content_calendar` y calcula la firma de la campaña activa.
- `server/sync/main.py`: `calendar_watch` revisa cada 15 min; al cambiar la
  campaña reconstruye solo las filas `tema-*`/`tema-evento-*` y sube la versión
  del índice.
- `server/sync/schema.sql`: tabla `content_calendar` (temporadas, eventos y
  estado activo).
- `server/api/calendar_feed.py`: decide la campaña activa, el hero y el próximo
  cambio; tolera tabla ausente o base caída.
- `server/api/app.py`: nuevo `GET /v1/calendar`.
- `server/api/banners.py`: el hero de `/v1/banners` se llena con la fila de la
  campaña activa y `nextRefreshAt` se recorta al fin de la temporada.
- Android TV: refresca la copia de banners cada 5 min mientras el Inicio está
  abierto. El launcher ya lo hace en su ciclo de 5 min.
- Android móvil: el hero del Inicio prefiere la primera fila `tema-*`/`tema-evento-*`
  activa (la que el servidor ordena por prioridad).
- webOS/PC: consumen el mismo `/v1/banners`, así que heredan el hero estacional
  sin cambios adicionales.

Pruebas: **692 backend** y suite Android de `:app:testNormalDebugUnitTest` en
verde, con guardianes de fechas, solapamientos, prioridad, eventos exactos y
cambio de campaña en banners.

## 11. Activación

En el VPS, al desplegar el árbol:

```bash
cd /opt/streaming-backend
docker compose up -d --build api sync
```

El worker aplica `schema.sql`, publica `content_calendar` y reconstruye las filas
de la campaña activa. Comprobación:

```bash
curl -H "X-Api-Key: $API_KEY" https://visionplusv2.duckdns.org/v1/calendar
curl -H "X-Api-Key: $API_KEY" https://visionplusv2.duckdns.org/v1/banners
```

La tele y Vision+ Home no necesitan OTA: el hero llega por `/v1/banners` y las
filas `tema-evento-*` se pintan como temáticas. El móvil necesita una OTA solo
para que el hero del Inicio prefiera la campaña activa; las filas también le
llegan sin OTA.

## 12. Fuentes principales

- COP31: <https://www.un.org/en/climatechange/cop31>
- Mundial Femenil 2027: <https://www.fifa.com/en/tournaments/womens/womensworldcup/brazil-2027>
- Oscars 99: <https://press.oscars.org/news/academy-and-abc-announce-show-dates-99th-and-100th-oscarsr>
- Golden Globes: <https://goldenglobes.com/>
- Grammy 2027: <https://www.grammy.com/news/2027-grammys-show-air-date-nominations-announced/>
- Super Bowl LXI: <https://www.nfl.com/news/jay-z-to-headline-super-bowl-lxi-at-sofi-stadium-in-inglewood>
- Cannes 2027: <https://www.festival-cannes.com/en/>
- Eurovisión 2027: <https://www.eurovision.com/eurovision-song-contest/burgas-2027/>
- Comic-Con 2027: <https://www.comic-con.org/cc/plan-your-visit/>
- Eclipse 2027: <https://nso.edu/eclipse-map-2027-aug2/>
- AFCON 2027: <https://www.cafonline.com/afcon2025/news/road-to-east-africa-caf-announces-kick-off-final-and-qualifiers-dates-for-the-totalenergies-caf-africa-cup-of-nations-pamoja-2027/>
- Rugby 2027: <https://www.rugbyworldcup.com/2027/en>
- Cricket 2027: <https://www.icc-cricket.com/tournaments/icc-cricket-world-cup-2027/matches>
- Panamericanos Lima 2027: <https://www.panamsports.org/en/news-sport/official-sports-schedule-for-the-lima-2027-pan-american-games-confirmed/>
- Thanksgiving: <https://www.timeanddate.com/holidays/us/thanksgiving-day>
- Año Nuevo Lunar 2027: <https://www.rmg.co.uk/stories/time/lunar-new-year-dates-animals-zodiac>
- Latin Grammy 2026: <https://www.latingrammy.com/en/awards/calendar/>
