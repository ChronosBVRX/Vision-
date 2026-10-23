# Vision+ 2.5.125

**Perfiles: una persona por perfil**
- Hasta 5 perfiles por equipo. Cada uno guarda su «Seguir viendo», su historial, Mi lista y las series que sigue.
- El perfil **Principal** conserva todo lo que ya tenías: actualizar no borra nada.
- Cambiar de perfil recarga el Inicio, Mi lista y la ficha; en la tele, la fila «Continuar viendo» del launcher se actualiza también.
- Los ajustes, las descargas, el modo niños y el control remoto son del equipo (no del perfil).
- Cada perfil tiene su propio código en «Continuidad entre equipos»: así dos personas no mezclan su historial.
- En la tele: **Ajustes → Perfiles**. Se crean con nombre automático (el teclado está en el teléfono) y se puede borrar el perfil activo; el Principal no se borra.

**Debajo**
- Los almacenes personales (Mi lista, historial, series seguidas y continuidad) resuelven su archivo según el perfil activo, incluido el XML que lee el reproductor y el launcher.

**Corrección**
- El rótulo del perfil activo ya no repite la palabra: «Perfil 2 · 2 de 5 en este equipo».

Verificado: 829 tests Android + 333 backend en verde; probado en un equipo limpio (crear un perfil, cambiar a él y volver al Principal, sin cierres); release R8 con la firma histórica.

— ChronosBVRX
