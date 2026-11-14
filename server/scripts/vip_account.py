#!/usr/bin/env python3
"""Operación de la cuenta VIP del broker: revisar, rotar y vigilar.

La cuenta es de UN solo dueño: el proceso `api` del VPS. Nadie más debe acuñar
con su SN, o la patada (`aaa100028`) viaja en ambos sentidos. Por eso aquí todo
se valida ANTES de publicar y la rotación se revierte sola si no firma.

Uso (desde `server/`):

    python scripts/vip_account.py status
    python scripts/vip_account.py check --sn <SN> --uid <userId>
    python scripts/vip_account.py apply --sn <SN> --uid <userId>
    python scripts/vip_account.py watch --max-days 5 --interval 3600

`check` acuña de verdad contra la cuenta indicada (patea la sesión vigente unos
segundos; el broker se recupera solo). `apply` corre `check`, respalda `.env`,
cambia `VIP_SN`/`VIP_USER_ID`, reinicia `api` y verifica; si la verificación
falla, restaura el respaldo y reinicia de nuevo.

Notas:
- `apply` necesita `docker compose` (correr en el VPS). Desde la PC solo `check`.
- `status`/`watch` hablan por HTTPS con `X-Admin-Key` (`CONFIG_ADMIN_KEY`).
- El caché de licencias NO se borra al rotar: lo ya firmado sigue valiendo.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API_DIR = ROOT / "api"
ENV = ROOT / ".env"
ENV_EXAMPLE = ROOT / ".env.example"
DEFAULT_URL = "https://visionplusv2.duckdns.org"

REQUIRED_QUALITY = "1080p"
MIN_DAYS = 300  # una cuenta de un año; por debajo se avisa


def die(msg: str, code: int = 1) -> "None":
    print(f"✗ {msg}", file=sys.stderr)
    raise SystemExit(code)


def read_env_file() -> dict[str, str]:
    """Lee `.env` (o su ejemplo) sin dependencias."""
    src = ENV if ENV.exists() else ENV_EXAMPLE
    values: dict[str, str] = {}
    if not src.exists():
        return values
    for raw in src.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def http_json(method: str, url: str, headers: dict[str, str], body: dict | None = None) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    for key, value in headers.items():
        req.add_header(key, value)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:300]
        raise SystemExit(f"✗ HTTP {exc.code}: {detail}")
    except Exception as exc:  # noqa: BLE001
        raise SystemExit(f"✗ sin conexión: {exc}")


# --------------------------------------------------------------------- check

def check_account(sn: str, uid: str, quiet: bool = False) -> dict:
    """Valida SN/userId de verdad: acuña, lee días y firma el título de sonda."""
    sn, uid = sn.strip(), uid.strip()
    if not sn or not uid:
        die("hacen falta --sn y --uid")
    if len(sn) < 16:
        die(f"SN sospechosamente corto: {sn!r}")
    sys.path.insert(0, str(API_DIR))
    try:
        import vip  # noqa: PLC0415 - se importa aquí para fijar SN/UID limpios
    except Exception as exc:  # noqa: BLE001
        die(f"no pude importar el broker ({API_DIR}): {exc}")
    vip.SN, vip.USER_ID = sn, uid
    vip.log.disabled = quiet

    try:
        result = vip.probe()
    except Exception as exc:  # noqa: BLE001
        die(f"la cuenta no firma: {type(exc).__name__}: {exc}")
    finally:
        vip.log.disabled = False

    quality = result.get("quality") or []
    problems: list[str] = []
    if result.get("mismatch"):
        problems.append(
            f"el userId del portal ({result.get('userId')}) no es el --uid recibido ({uid})"
        )
    if not quality:
        problems.append("el título de sonda no devolvió ninguna pista")
    if quality and REQUIRED_QUALITY not in quality:
        problems.append(f"no hay {REQUIRED_QUALITY}; pistas: {quality}")
    days = result.get("daysLeft")
    if not isinstance(days, int) or days <= 0:
        problems.append(f"la cuenta no reporta días (remainingDays={days})")

    if not quiet:
        expires = result.get("accountExpiresAt") or 0
        exp_txt = (
            datetime.fromtimestamp(expires, timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
            if expires
            else "desconocido"
        )
        print(f"  SN:          {result.get('sn')}")
        print(f"  userId:      {result.get('userId')}")
        print(f"  días:        {days}")
        print(f"  vence:       {exp_txt}")
        print(f"  pistas:      {quality or 'ninguna'}")
        if days is not None and isinstance(days, int) and 0 < days < MIN_DAYS:
            print(f"  aviso:       {days} días; para distribución anual se esperan >= {MIN_DAYS}")

    if problems:
        if not quiet:
            for item in problems:
                print(f"✗ {item}")
        raise SystemExit(2)

    result["ok"] = True
    return result


# --------------------------------------------------------------------- apply

def compose(*args: str) -> None:
    cmd = ["docker", "compose", *args]
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    if proc.returncode != 0:
        die(f"{' '.join(cmd)} falló: {(proc.stderr or proc.stdout).strip()[:400]}")


def upsert_env(sn: str, uid: str) -> None:
    """Reescribe solo las dos variables; respeta el resto del archivo."""
    lines = ENV.read_text(encoding="utf-8").splitlines() if ENV.exists() else []
    wanted = {"VIP_SN": sn, "VIP_USER_ID": uid}
    seen: set[str] = set()
    out: list[str] = []
    for raw in lines:
        key = raw.split("=", 1)[0].strip() if "=" in raw and not raw.strip().startswith("#") else ""
        if key in wanted:
            out.append(f"{key}={wanted[key]}")
            seen.add(key)
        else:
            out.append(raw)
    for key, value in wanted.items():
        if key not in seen:
            out.append(f"{key}={value}")
    ENV.write_text("\n".join(out).rstrip() + "\n", encoding="utf-8")


def wait_probe(url: str, headers: dict[str, str], tries: int = 10) -> dict | None:
    """Espera a que el broker reiniciado firme; None si no lo logra."""
    for attempt in range(tries):
        try:
            data = http_json("POST", f"{url}/v1/vip/probe", headers)
            if data.get("ok"):
                return data
        except SystemExit:
            pass
        time.sleep(3 + attempt)
    return None


def apply_account(sn: str, uid: str, url: str) -> None:
    if not shutil.which("docker"):
        die("`apply` necesita docker: córrelo en el VPS (aquí solo `check`)")
    if not ENV.exists():
        die(f"no existe {ENV}; cópialo de .env.example y configura API_KEY")
    env = read_env_file()
    admin = env.get("CONFIG_ADMIN_KEY", "").strip()
    if not admin:
        die("falta CONFIG_ADMIN_KEY en .env (lo necesita la verificación)")
    headers = {"X-Admin-Key": admin}

    print("1/5 validando la cuenta candidata (patea la sesión actual unos segundos)")
    check_account(sn, uid)

    print("2/5 respaldando .env")
    backup = ENV.with_suffix(f".bak-{datetime.now().strftime('%Y%m%d-%H%M%S')}")
    shutil.copy2(ENV, backup)
    print(f"    respaldo: {backup.name}")

    print("3/5 escribiendo credenciales y reiniciando api")
    upsert_env(sn, uid)
    compose("up", "-d", "api")

    print("4/5 verificando el despliegue")
    probe = wait_probe(url, headers)
    if probe and REQUIRED_QUALITY in (probe.get("quality") or []):
        print(f"✓ broker firmando {probe.get('quality')} con userId {probe.get('userId')}")
        print(f"  días restantes: {probe.get('daysLeft')}")
        print("  El caché anterior se conserva: lo ya firmado sigue reproduciendo.")
        return

    print("✗ la verificación falló; restaurando respaldo")
    shutil.copy2(backup, ENV)
    compose("up", "-d", "api")
    status = None
    try:
        status = http_json("GET", f"{url}/v1/vip/status", headers)
    except SystemExit:
        pass
    if status:
        print(f"  estado tras restaurar: days={status.get('daysLeft')} user={status.get('userId')}")
    die("rotación revertida; revisa los logs de `api`")


# --------------------------------------------------------------- status/watch

def print_status(url: str, headers: dict[str, str]) -> dict:
    data = http_json("GET", f"{url}/v1/vip/status", headers)
    expires = data.get("accountExpiresAt") or 0
    exp_txt = (
        datetime.fromtimestamp(expires, timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
        if expires
        else "desconocido"
    )
    print(f"user={data.get('userId')} días={data.get('daysLeft')} vence={exp_txt}")
    print(
        f"sesión={data.get('sessionAge')}s acuñados={data.get('mints')} "
        f"patadas={data.get('kicks')} caché={data.get('cacheValid')}/{data.get('cacheEntries')}"
    )
    if data.get("mismatch"):
        print("✗ VIP_SN y VIP_USER_ID no son de la misma cuenta")
    if data.get("lastError"):
        print(f"último error: {data['lastError']}")
    if data.get("alarm"):
        print("⚠ ALARMA: días bajos o cuenta mal configurada")
    else:
        print("✓ sin alarmas")
    return data


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:  # noqa: BLE001 - consola ajena
            pass
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("status", "check", "apply", "watch"))
    parser.add_argument("--sn", default=os.environ.get("VIP_SN", ""))
    parser.add_argument("--uid", default=os.environ.get("VIP_USER_ID", ""))
    parser.add_argument("--url", default=os.environ.get("VPS_URL", DEFAULT_URL))
    parser.add_argument("--max-days", type=int, default=5)
    parser.add_argument("--interval", type=int, default=3600)
    args = parser.parse_args()

    ENV_SIDE = read_env_file()
    headers = {"X-Admin-Key": ENV_SIDE.get("CONFIG_ADMIN_KEY", "").strip() or os.environ.get("CONFIG_ADMIN_KEY", "")}
    if args.command in ("status", "watch") and not headers["X-Admin-Key"]:
        die("falta CONFIG_ADMIN_KEY (en .env o en el entorno)")

    if args.command == "status":
        print_status(args.url, headers)
    elif args.command == "check":
        check_account(args.sn, args.uid)
        print("✓ cuenta apta")
    elif args.command == "apply":
        apply_account(args.sn, args.uid, args.url)
    else:
        while True:
            stamp = datetime.now().strftime("%H:%M:%S")
            try:
                data = print_status(args.url, headers)
                days = data.get("daysLeft")
                if isinstance(days, int) and 0 <= days < args.max_days:
                    print(f"ALARMA {stamp}: quedan {days} días; recarga ya")
            except SystemExit as exc:
                print(f"ALARMA {stamp}: {exc}")
            print("-" * 40, flush=True)
            time.sleep(max(60, args.interval))


if __name__ == "__main__":
    main()
