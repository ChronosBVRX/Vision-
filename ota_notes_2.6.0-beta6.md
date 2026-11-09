# Vision+ 2.6.0-beta6 — 60 personajes originales para Perfiles

**Esta es una beta de pruebas.** La estable sigue en **2.5.246** y se instala encima de la beta5 (vc490) sin borrar datos, perfiles, favoritos, historial, descargas ni ajustes.

## Nuevos avatares de personaje

- Se retiraron los pósters y recortes del catálogo. La galería de Perfiles ahora usa **60 personajes originales**, creados en local con ComfyUI + FLUX.2 Klein:
  - **20 personajes de cine**
  - **20 personajes de series**
  - **20 personajes de anime**
- Hay variedad de público: **adulto, familiar e infantil**. Conviven detectives, espías, vaqueros, reinas, hackers, doctores, brujas, superheroínas, robots, dragones, piratas, hadas, samuráis, ninjas, pilotos de mecha, chicas mágicas, entrenadores de monstruos, idols y luchadores.
- Son retratos de personajes, no portadas ni recortes: cara centrada, fondo cinematográfico y sin texto.
- Las imágenes viajan dentro del APK como WebP de 256×256 (~830 KB en total), así que el selector funciona sin internet y en una tele.
- Las pestañas **Cine / Series / Anime** muestran 20 opciones cada una. El selector mantiene el foco: Arriba vuelve a las pestañas, Abajo llega a Guardar y el contenido se puede desplazar.
- Los perfiles que tenían uno de los avatares anteriores vuelven a la inicial; solo hay que elegir uno de los nuevos.

## Lo que se conserva de las betas anteriores

- **Teclado en pantalla** para escribir nombres con el mando y opción **Teclado del sistema**.
- **Capítulos vistos al 85 %**, OK largo para marcar visto/no visto y barra de porcentaje.
- **Reproductor**: título visible, degradados hasta los bordes, Idioma/Subtítulos siempre visibles y botones discretos.
- **Descargas portátiles USB con PIN**.
- La OTA sigue apuntando a **ChronosBVRX/Vision-Releases**.

## Cómo probar esta beta

1. Instala el APK encima de la beta5.
2. Entra a **Ajustes → Perfiles** y edita un perfil.
3. Recorre Cine, Series y Anime: deben aparecer 20 personajes por pestaña, con caras/personajes y sin pósters con texto.
4. Elige uno, baja hasta Guardar y comprueba que puedes volver arriba con el mando.
5. Prueba un personaje adulto, uno familiar y uno infantil.

## Límites de esta beta

- Los personajes son **originales**, inspirados en géneros y arquetipos populares, no caricaturas exactas de personajes con copyright.
- Sigue pendiente la prueba física en la TCL del selector con 60 imágenes y de la memoria del equipo.
- La prueba de USB en dos TVs y la del teclado en pantalla continúan abiertas.

## Estado técnico

- 1,312 pruebas Android en verde (3 omitidas).
- Versión **2.6.0-beta6** (versionCode **491**), firma histórica `9593cb0d…` y motor X4K dentro del APK.
- Generador reproducible: `tools/generate_profile_avatars.py` (ComfyUI + FLUX.2 Klein, 4 pasos).
- La estable sigue en **2.5.246**.

— ChronosBVRX
