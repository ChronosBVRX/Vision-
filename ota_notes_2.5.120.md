# Vision+ 2.5.120 — los logos de los canales en vivo ya no se estiran

- **El logo vuelve a su tamaño de logo.** Antes se agrandaba hasta ocupar toda la tarjeta (3–4× su tamaño real): se veía borroso, gigante, y los canales cuyo logo ya viene recortado por el proveedor parecían tenerlo roto. Ahora se dibuja a su tamaño natural, con un máximo de 2.5× (nunca más que la tarjeta), centrado y con aire alrededor.
- **Por qué pasaba.** Los logos que manda el proveedor miden **90×60 píxeles** y la app los pedía al tamaño de la tarjeta (480×270), es decir, los inventaba. Ya no: se decodifican a su tamaño y se muestran tal cual.
- **Dónde**: la rejilla de **TV en vivo** del móvil, la **fila de canales** de la Smart TV y su **panel de vista previa** (ahí se permite algo más de tamaño porque el panel es grande).
- **Iniciales solo de respaldo.** Las letras del canal aparecen únicamente mientras carga el logo o si ese canal no tiene ninguno; antes se veían detrás del logo y ensuciaban la tarjeta.
- **Nada más cambia.** Mismo catálogo, mismos filtros, mismos favoritos, misma reproducción.
- Guardianes: **758 pruebas Android + 300 del servidor** en verde (incluye pruebas del tamaño calculado y de que las dos pantallas usan la pieza compartida); release R8 con la firma histórica.

— ChronosBVRX
