# Vision+ 2.5.198 — cierre de producción Vision+ Play v2

**Fecha:** 30 de octubre de 2026  
**Versión:** 2.5.198 / vc438  
**Launcher:** 1.3.15 / vc38

## Qué cambia

### Launcher 1.3.15 — experiencia cinematográfica, accesos y modo persistente

- **Fondo a pantalla completa:** el arte de la diapositiva activa del carrusel se pinta una sola vez detrás de toda la interfaz, con degradados de lectura, viñeta y sin duplicados ni costuras.
- **Hero más limpio:** una sola píldora EN VIVO + `Evento • Temporada`, escudos transparentes, hora tabular y botones rectangulares con borde degradado (primario blanco, secundario de vidrio).
- **Deportes sin portada:** fotos de referencia empaquetadas para motor, fútbol, básquet, tenis, americano, béisbol, boxeo, voleibol, golf y hockey; el emblema de la liga/equipo se superpone como marca de agua.
- **Foco 10-foot:** escala 1.06 con 200 ms, anillo degradado blanco/cian, sombra ambiental y tarjetas hermanas al 75 %.
- **Tarjetas:** 16:9 para canales y «Seguir viendo», 2:3 para pósters, barra de progreso cian de 3 px al borde inferior y velo oscuro homogéneo.
- **Barra superior:** imagotipo oficial con transparencia, `HOME` en gris regular, pestaña activa con línea fina, reloj tabular y avatar con marco circular; el icono y el banner del launcher también son oficiales.
- **Apps y TV:** las apps suben al primer vistazo con iconos a sangre; «Mis canales» manda y la fila genérica de TV en vivo desaparece cuando hay favoritos. Las entradas de TV viven en una fila compacta bajo «Seguir viendo», con iconos propios y sin scroll horizontal.
- **TV en vivo sin sintonizador:** si el equipo no tiene sintonizador físico, aparece una baldosa TV en vivo que abre el último canal visto desde el launcher o, si no hay memoria, el primer canal de México.
- **Modo persistente del Inicio:** nuevo servicio de accesibilidad que devuelve Vision+ Home al frente si Google TV abre su launcher de fábrica. Se activa desde Ajustes → Inicio → Modo persistente, o desde el aviso que aparece tras elegir Vision+ Home como Inicio. Solo escucha cambios de ventana, no lee pantalla ni contenido.

### Reglas v2

- **Trivia 2.0:** ataques con racha, parásito gratis sin cadenas, castigo de cables, ráfaga de clasificación y asalto final.
- **Apuesta 2.0:** banca de 1,000, pozo, pistas privadas en el teléfono, apuesta lateral de 200 (+400 si el rival falla).
- **Charadas 2.0:** director, saboteador, timbre de 7 s con cooldown, ayudas contra el premio de la ronda.
- **Impostor 2.0:** cómplice, detective y coartada rápida.
- **Dibujo 2.0:** tinta limitada, adivinanza temprana con 2 intentos, fichas de crítico y linterna.
- **Subasta 2.0:** 6 lotes, carta secreta, 1 informante en salas pequeñas y 2 en grandes, falsa alarma, lote maldito y escudo reflector.
- **Fibbage 2.0:** veneno 150/300, escáner 1/2 según sala, voto de certeza exclusivo y ronda final compartida.

### Assets y audio

- 5 SFX nuevos: timbre, cristal, latido, caja fuerte y fanfarria.
- 60 voces Qwen3-TTS v2: 20 momentos × 3 variantes.
- 7 tableros de tutorial de 1280×720.
- Avatares reducidos a 256×256 WebP.
- Partículas reducidas a 128×128 WebP.
- Transiciones Compose nuevas: fase, lienzo interpolado con linterna, reveal 3D y partículas en un solo Canvas.

### Correcciones de la auditoría

- Parásito sin bucle ni focus fire.
- Pistas de Apuesta privadas.
- Timbre con cooldown y saboteador camuflado.
- Adivinanza temprana limitada.
- Informantes escalados por tamaño.
- Escáner y certeza exclusivos.
- Validación de saldos en acciones de costo.

## Pruebas

- Backend: **570 tests** en verde.
- Android: **1,148 tests** en verde.
- Launcher: **183 tests** en verde.

## Verificación

- Backend desplegado en el VPS y smoke v2 OK.
- APK release R8 con motor X4K inyectado (`classes3/4/5.dex`).
- Firma histórica `9593cb0d…` en la app y en el launcher.
- `VisionPlus.apk`: **97,637,875 B**, SHA-256 `0f147db0eb129601c4c8539c8ce029537912bd375da3b88c7a05916a7bc32452`.
- `VisionPlusHome.apk` (1.3.15): **2,824,059 B**, SHA-256 `caebd556c1a7c2cc11354b53f69a2a08a8e80242389821f83a029dcada0e3ba9`.
- Launcher 1.3.14 verificado en el Chromecast HD: fondo continuo sin duplicado, Mis canales → Apps → Top 10, Entradas compactas y TV en vivo abriendo CANAL 5 HD cuando no hay sintonizador.
- Instalado en Chromecast HD; Inicio y selector de juegos abren sin crash.

## Pendiente

- Prueba física con 2–3 teléfonos.
- UI del cazador de Fibbage (pospuesta).
- Interrogatorio libre/radar del Impostor.
- Relevo de artistas, sindicato de subasta y doble hueco final.
