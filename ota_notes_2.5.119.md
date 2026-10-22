# Vision+ 2.5.119 — blindaje definitivo del motor y un servidor que frena solo un build roto

- **El motor de video ya no puede quedar tapado.** La app trae dentro su motor (las clases que abren el video) y lo carga en un espacio aparte. Hasta ahora, si una clase nuestra se llamaba igual que una del motor, la tapaba y el reproductor moría (fue el apagón de la 2.5.117). Ahora el motor **siempre usa sus propias clases primero**: aunque en el futuro una librería vuelva a ocupar un nombre suyo, el video arranca igual.
- **Autocomprobación al abrir el reproductor.** Cada vez que el motor carga, la app verifica que sus clases las sirva él y no nuestro código. Si algo así pasara, se reporta como `ENGINE_NAMESPACE_LEAK` en el diagnóstico (con el detalle) en vez de dejarte sin video y sin explicación.
- **Interruptor remoto.** Si en algún equipo el cambio se portara raro, el servidor puede devolver el comportamiento anterior con `engine_child_first: false`, **sin publicar otra actualización**.
- **El servidor frena solo un build roto.** El vigilante de diagnósticos ahora revisa la versión más nueva: si salió hace menos de 48 h y acumula fallos de reproductor en varios equipos, **pausa la oferta de actualizaciones** (para que nadie más se la lleve) y avisa por Telegram con el detalle. Se rearma solo: al ver una versión más nueva o a las 12 h.
- **Nada cambia en lo que ves.** Mismo catálogo, mismos ajustes, misma continuidad entre equipos, mismos deportes, mismo modo sin conexión.
- Guardianes: **753 pruebas Android + 300 del servidor** en verde; release R8 con la firma histórica y verificación automática de nombres del motor (0 colisiones).

— ChronosBVRX
