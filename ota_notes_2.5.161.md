# Vision+ 2.5.161 (beta) — el hero vuelve completo al subir

- **Fin del banner recortado**: al bajar por el menú y regresar arriba, el foco caía en los botones del hero y la lista se quedaba a medio scroll (se veían el botón y un pedazo del arte, sin título ni sinopsis). Ahora, al enfocar cualquier acción del hero, la lista **se ancla al tope** y el banner se ve completo, tanto en **Vision+ Home** como en **Vision TV**.
- Se respeta el regreso a una ficha: si vienes de abrir un título, el anclaje no se dispara y el foco sigue cayendo en la **misma tarjeta**.
- **Banners con coste cero** (lo de 2.5.160): la tele pide `/v1/banners` solo cuando el VPS anuncia la siguiente foto (`nextRefreshAt`, 20 h) + 5 min; si el VPS está refrescando, reintenta en 15 min; una sola descarga a la vez y 15 min de enfriamiento si falla. El VPS refresca una vez con candado de Postgres y hace 5 llamadas a TMDB al día como máximo. El arte se reutiliza por su ruta estable, así que el `?v=` diario ya no vuelve a bajar la misma imagen.
- **Deportes solo GeeSports** (lo de 2.5.159): el selector del partido ya no muestra «Canales posibles» ni respaldo automático; solo las señales de GeeSports con URL real.
- **Vision+ Home 1.2.3** con el mismo anclaje del hero.

Actualízalo desde el aviso de la app si estás en el canal de pruebas, o desde Vision+ → Ajustes → «Canal de pruebas (beta)».
