Guía Electrónica de Programación (EPG) en vivo 100% funcional y conectada al backend VPS:

- Guía EPG real y completa: Integración con el servidor VPS (`https://la20.com.mx/catalogo/v1/epg`) mediante canalización en segundo plano de feeds XMLTV dedicados para México y Latinoamérica (Canal 5, Azteca 7, Las Estrellas, Azteca Uno, TUDN, Fox Sports, ESPN, Claro Sports, Win Sports, Directv Sports, HBO, Cinemax, Warner, Sony, Universal TV, Cartoon Network, Discovery, etc.).
- Sin sobrecarga en el dispositivo ni en la app: El servidor VPS descarga, normaliza y almacena la programación con más de 65,000 emisiones en Postgres, entregando respuestas ultrarrápidas (<50 ms) y desahogando al teléfono o Smart TV.
- Programación en Banner OSD: El banner de televisión por cable ahora muestra el programa en curso («AHORA») y el que sigue («DESPUÉS») con horarios exactos y cálculo dinámico del porcentaje transcurrido en la barra de progreso azul `#2D7DF6`.
- Ficha de Información detallada (`ℹ Información`): Modal interactivo que presenta la sinopsis completa del programa actual junto a la programación próxima del día («A CONTINUACIÓN»), horarios y detalles de emisión.
- Caché inteligente en memoria: Almacenamiento local con TTL de 5 minutos para evitar consultas redundantes al hacer zapping continuo. Fallback transparente al portal del proveedor si el canal no está en la guía.
