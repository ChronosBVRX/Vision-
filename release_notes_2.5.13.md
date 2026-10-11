Detección inteligente de hardware y Modo Ligero automático para Smart TV y Chromecast antiguos.

- Detección automática de capacidades: identifica equipos con ≤2.2 GB de RAM o CPUs modestas (Chromecast con Google TV, Fire TV Stick, TV boxes con chipsets de entrada).
- Modo ligero adaptativo: elimina la animación infinita de deriva en el fondo (cero sobrecarga de GPU en reposo), desactiva el zoom en tarjetas al enfocar manteniendo el borde nítido instantáneo, y eleva el asentado del foco a 320 ms para navegación ultra-fluida con el D-pad sin tirones.
- Optimización de shaders y memoria: gradientes estáticos fuera de Crossfade para evitar redibujados dobles de pantalla completa, y decodificación optimizada de imágenes en Coil (RGB_565 con caché de RAM reducida para evitar congelamientos por recolección de basura).
- Selector en Ajustes: nueva opción interactiva en Ajustes ▸ Pantalla («Rendimiento de interfaz: Automático / Ligero / Máxima calidad») con guardado persistente.
- Verificado sin regresiones con tests unitarios automatizados.
