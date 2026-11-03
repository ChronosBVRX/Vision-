# Fiesta — arte premium (ComfyUI / SDXL)

**Modelo:** `juggernautXL_v9Rundiffusion.safetensors`  
**GPU:** RTX 3060 12 GB local  
**Pipeline:** `tools/comfy_fiesta_premium.py`  
**Instalador:** `tools/install_fiesta_premium.py`  
**Filtro:** `tools/fiesta_quality_gate.py`  
**Reporte:** `../QUALITY_REPORT.md`

## Selección aprobada

| Juego | Plató | Presentador | Semilla plató | Semilla host |
|---|---|---|---:|---:|
| La Última Palabra | estudio de concurso cálido, oro/naranja, candelabro | Nora Vela | 811000 | 811100 |
| Vox Populi | noticiero nocturno cian, ciudad lluviosa, pantallas | Don Chisme | 812014 | 812100 |
| Fuera de Época | cámara temporal violeta, anillo/reloj, portal | Profesor Tic Tac | 813007 | 813100 |
| Texto Roto | muro de burbujas esmeralda, sala industrial | La Máquina | 814000 | 814100 |
| Franela Fest | pasarela rosa/dorada, cortinas, flashes | El Crítico | 815007 | 815100 |
| El Tasador | casa de subastas, terciopelo rojo, oro, mesa de objetos | El Tasador | 816000 | 816100 |

Además:

| Asset | Archivo | Semilla |
|---|---|---:|
| Fondo del lobby de selección | `approved/lobby.png` → `fiesta_lobby_bg.webp` | 819000 |
| Tarjeta de selección de cada juego | `approved/<juego>_selector.png` → `game_<juego>_poster.webp` | 811300…816300 |

Los teléfonos se recortan del plató aprobado (misma identidad visual) y se
oscurecen en la mitad inferior para que los controles se lean.

## Criterios de aprobación

Un candidato solo pasa a `approved/` si:

1. se ve como una producción: composición intencional, luz volumétrica,
   materiales ricos y utilería propia del juego;
2. no tiene texto legible, logos, marcas de agua ni UI;
3. no tiene manos, caras o anatomías deformes;
4. el plató deja espacio para la UI (centro y esquinas);
5. pasa el filtro objetivo de resolución, peso, entropía, bordes y colorfulness.

El filtro objetivo no decide si una imagen es bonita. La selección de arriba se
hizo revisando los candidatos uno por uno.

## Regenerar

```bash
# Candidatos nuevos (no toca el APK)
python3 tools/comfy_fiesta_premium.py --only stage --variants 2
python3 tools/comfy_fiesta_premium.py --only host --variants 1
python3 tools/comfy_fiesta_premium.py --contact

# Copiar los elegidos a premium/approved/<juego>_<tipo>.png
# Instalar WebP en app/src/main/res/drawable-nodpi/
python3 tools/install_fiesta_premium.py

# Verificar
python3 tools/fiesta_quality_gate.py
python3 tools/fiesta_quality_gate.py --premium
```

## Nota legal

Las imágenes se generaron localmente con un modelo abierto. No se usaron assets,
personajes, logotipos, preguntas ni arte de Jackbox. La dirección de arte y los
personajes son originales de Vision+.
