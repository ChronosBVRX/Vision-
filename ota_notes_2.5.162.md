# Vision+ 2.5.162 (beta) — el zoom del hero ya no se sale del banner

- **Arreglado en la TLC (1920×1080, Android 9)**: al agrandarse el arte con el Ken Burns, la imagen se dibujaba fuera de la caja del hero y dejaba una franja inferior sin el degradado oscuro (se veía el color original sobre la fila siguiente). Ahora el hero se **recorta a su caja** (`clipToBounds`), así que el zoom queda siempre dentro del banner y el filtro oscuro cubre todo el arte.
- **Vision+ Home 1.2.4**: incluye este recorte; antes/después verificado en la TLC con el launcher real y el zoom al máximo.
- **Mantiene** el hero completo al subir (launcher 1.2.3 / app 2.5.161): al enfocar sus botones la lista se ancla al tope y ya no se queda a medio scroll.
- **Mantiene** los banners con coste cero (2.5.160): `nextRefreshAt` del VPS, una sola descarga, enfriamiento si falla, arte reutilizado por ruta y un solo refresco diario en el VPS con candado.
- **Mantiene** el selector de Deportes solo con fuentes de GeeSports (2.5.159).
- La app no cambia de comportamiento; la actualización trae el launcher nuevo.

Actualízalo desde el aviso de la app si estás en el canal de pruebas, o desde Vision+ → Ajustes → «Canal de pruebas (beta)».
