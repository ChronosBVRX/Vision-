#!/usr/bin/env python3
"""
Mete los DEX del motor X4K DENTRO del APK, como `classesN.dex` (lo que hace Crystal).

¿Por qué? El motor viajaba en `assets/x4k/*.dex` y se cargaba con `DexClassLoader` en runtime:
17 MB de copia, verificación de bytecode en el equipo (el bug de ART que dejó muda a una ZTE) y
un arranque en frío más lento. Como `classesN.dex` del APK, Android los deja verificados al
instalar y la app los carga con su propio classloader: cero extracción.

IMPORTANTE: el motor entra DESPUÉS de los DEX propios de la app. Si la app ya ocupa
`classes2.dex` (le pasa al crecer con librerías nuevas), el motor va a `classes3/4/5`. Escribir
siempre en `classes2.dex` borraba el DEX de la app y el APK no abría (bug de la 2.5.151).

Los assets NO se quitan: son el respaldo si el APK llega sin las clases (rollback).
Se ejecuta DESPUÉS del build y ANTES de `zipalign` + `apksigner` (el script de publicación).

Uso: inject_engine_dex.py <in.apk> <out.apk> <assets_dir>
"""
import struct
import sys
import zipfile

ENGINE_DEX = ("x4k/x4k-sdk-1.dex", "x4k/x4k-sdk-2.dex", "x4k/x4k-sdk-3.dex")
# `classes.dex` siempre es nuestro; el motor entra a partir del primer índice libre.
BASE_INDEX = 2


def target_names(existing: set) -> "list[str]":
    """Los nombres libres para el motor, justo después de los DEX propios de la app."""
    index = BASE_INDEX
    while f"classes{index}.dex" in existing:
        index += 1
    return [f"classes{index + offset}.dex" for offset in range(len(ENGINE_DEX))]


def valid_dex(name: str, blob: bytes) -> None:
    if len(blob) < 112 or blob[:4] != b"dex\n":
        sys.exit(f"[inject] {name} no es un DEX válido (magic)")
    declared = struct.unpack("<I", blob[32:36])[0]
    if declared != len(blob):
        sys.exit(f"[inject] {name} está cortado: header dice {declared} B, hay {len(blob)} B")


def main() -> None:
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    src, dst, assets = sys.argv[1:4]

    payload = []
    for rel in ENGINE_DEX:
        path = f"{assets}/{rel}"
        try:
            with open(path, "rb") as handle:
                blob = handle.read()
        except OSError as error:
            sys.exit(f"[inject] no pude leer {path}: {error}")
        valid_dex(rel, blob)
        payload.append((rel, blob))

    written = []
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        existing = set(zin.namelist())
        names = target_names(existing)
        own_dex = sorted(name for name in existing if name.startswith("classes") and name.endswith(".dex"))
        for item in zin.infolist():
            if item.filename in names:
                continue  # nunca duplicar classesN.dex (dex2oat se queja)
            zout.writestr(item, zin.read(item.filename))
        for name, (_, blob) in zip(names, payload):
            if name in existing:
                sys.exit(f"[inject] {name} ya existía: no se puede pisar un DEX de la app")
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED  # ART los mapea sin descomprimir
            info.external_attr = 0o644 << 16
            zout.writestr(info, blob)
            written.append(f"{name}={len(blob)}")

    print("[inject] DEX propios conservados: " + ", ".join(own_dex))
    print("[inject] motor dentro del APK: " + ", ".join(written))


if __name__ == "__main__":
    main()
