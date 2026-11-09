# Arte visual de Vision+ Party

## Bases canónicas

- **14 portadas 600×800** en `party/assets/covers/`:
  - 13 juegos + `default.webp`.
  - Copias idénticas en `app/src/main/res/drawable-nodpi/party_*.webp`.
  - Formato WebP RGB, sin texto añadido por código.
- **Arte de juego raster**:
  - Dulce puntería: pradera, personajes, objetivos y utilería.
  - Sombra fugitiva: ciudad de día/noche, tickets y vehículos.
  - Hexagonia: atlas de acciones y iconos SVG.
  - Baraja brava: símbolos de palo, atlas de Symbol Hunt e iconos.
- **Arte de juego dibujado por código**:
  - Garabato mental y Palabra maldita usan escenas Phaser.
  - Héroes del sótano usa SVG generado en el host.
- **Ambiente de host**:
  - 7 placas 1344×768 para Mímica en llamas, Cifras salvajes, El Infiltrado,
    Banterbolt, El Comisionado Merk, ¿Le atinaste? Bien. y Héroes del sótano.
  - Se aplican como capa de fondo oscurecida/suavizada; no cambian el layout,
    los hitboxes ni los símbolos.

## Reglas para no romper nada

1. No regenerar el **atlas de Symbol Hunt** ni sus 91 iconos: la lógica depende
   de símbolos exactos y de una correspondencia única.
2. No cambiar la geometría de **Dulce puntería** sin revisar los hitboxes.
3. No mover estaciones, rutas ni tickets de **Sombra fugitiva**.
4. No sustituir el tablero 3D ni el atlas de **Hexagonia** por imágenes planas.
5. No añadir texto, logos ni marcas de agua a las portadas o fondos.
6. Los fondos ambientales deben quedar por debajo del 90 % de opacidad para no
   reducir el contraste del texto.

## Generar ambiente con ComfyUI

El generador vive fuera del repo, en el flujo local de ComfyUI. La receta es:

- SDXL `juggernautXL_v9Rundiffusion.safetensors`.
- 1344×768, 34 pasos, DPM++ 2M Karras, CFG 6.
- Prompt de plato escénico, sin personas, sin texto, sin UI.
- Salida WebP calidad 84.

## Auditar

```bash
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
"$PY" party/tools/audit_party_art.py --json /tmp/opencode/art-audit.json
```

La auditoría revisa:

- 14 portadas y sus copias de Android sincronizadas por SHA-256.
- Dimensiones, formato, peso y duplicados.
- 7 fondos ambientales 1344×768.
- Que el arte raster existente abra y tenga resolución suficiente.

Resultado actual: **41 archivos revisados, 0 fallas**.
