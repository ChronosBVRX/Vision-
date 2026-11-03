#!/usr/bin/env python3
"""Empaqueta Vision+ para Roku con el manifest en la raíz del ZIP.

Uso:
    python3 roku/pack.py                 # valida con brighterscript y empaqueta
    python3 roku/pack.py --skip-validate # solo empaqueta

El ZIP resultante se instala desde el navegador del Roku en modo desarrollador
(Developer Settings -> Install channel) o con el uploader de Roku.
"""
from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "VisionPlus"
DIST = ROOT / "dist"
INCLUDE = ["manifest", "source", "components", "images"]


def manifest_version() -> str:
    values: dict[str, str] = {}
    for line in (APP / "manifest").read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return ".".join(
        values.get(key, "0")
        for key in ("major_version", "minor_version", "build_version")
    )


def validate() -> None:
    bsc = shutil.which("bsc")
    if not bsc:
        local = Path("/tmp/opencode/roku-tools/node_modules/.bin/bsc")
        if local.exists():
            bsc = str(local)
    if not bsc:
        print("AVISO: brighterscript no está instalado; se omite la validación.", file=sys.stderr)
        return
    config = APP / "bsconfig.json"
    print(f"Validando con {bsc} ...")
    subprocess.run([bsc, "--project", str(config)], check=True)


def build() -> Path:
    version = manifest_version()
    DIST.mkdir(parents=True, exist_ok=True)
    out = DIST / f"VisionPlus-roku-{version}.zip"
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name in INCLUDE:
            path = APP / name
            if path.is_file():
                z.write(path, name)
                continue
            for child in sorted(path.rglob("*")):
                if child.is_file() and ".build" not in child.parts:
                    z.write(child, child.relative_to(APP).as_posix())
    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    print(f"ZIP: {out}")
    print(f"Bytes: {out.stat().st_size}")
    print(f"SHA-256: {digest}")
    with zipfile.ZipFile(out) as z:
        if "manifest" not in z.namelist():
            raise SystemExit("ERROR: manifest no está en la raíz del ZIP")
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-validate", action="store_true")
    args = parser.parse_args()
    if not args.skip_validate:
        validate()
    build()


if __name__ == "__main__":
    main()
