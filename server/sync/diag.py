"""Triage de diagnóstico de campo (Fase 1).

Loop propio (como sports/epg): revisa los reportes que la app envía a `/v1/diag` y
  - huella nueva  -> alerta (webhook genérico o Telegram; si no hay config, log),
  - huella sin reportes en RESOLVE_HOURS -> "resuelta" (autocierre).

No toca el índice ni su versión.
"""

import asyncio
import json
import logging
import os
import urllib.request

log = logging.getLogger("diag")

INTERVAL = int(os.environ.get("DIAG_INTERVAL_SECONDS", "300"))
RESOLVE_HOURS = int(os.environ.get("DIAG_RESOLVE_HOURS", "48"))
WEBHOOK = os.environ.get("DIAG_ALERT_WEBHOOK", "").strip()
TG_TOKEN = os.environ.get("DIAG_TELEGRAM_TOKEN", "").strip()
TG_CHAT = os.environ.get("DIAG_TELEGRAM_CHAT", "").strip()

_KIND_ICON = {
    "crash": "💥 Crash",
    "anr": "🧊 ANR",
    "anomaly": "⚠️ Anomalía",
    "feedback": "📝 Reporte",
}


def alert_text(row) -> str:
    """Mensaje legible de una huella nueva (pura, testeable)."""
    kind = _KIND_ICON.get(row["kind"], str(row["kind"]))
    code = row["code"] or "(sin código)"
    return (
        f"{kind} nuevo en Vision+\n"
        f"{code}\n"
        f"v{row['latest_version']} · {row['devices']} dispositivo(s) · {row['hits']} vez(ces)\n"
        f"huella: {row['fingerprint']}"
    )


def _post(url: str, payload: dict) -> None:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        resp.read()


def send_alert(text: str) -> None:
    """Telegram si hay token/chat; si no, webhook genérico; si no, al log."""
    try:
        if TG_TOKEN and TG_CHAT:
            _post(f"https://api.telegram.org/bot{TG_TOKEN}/sendMessage",
                  {"chat_id": TG_CHAT, "text": text, "disable_web_page_preview": True})
        elif WEBHOOK:
            _post(WEBHOOK, {"text": text})
        else:
            log.warning("alerta de diagnóstico (sin webhook configurado): %s", text.replace("\n", " | "))
    except Exception:  # noqa: BLE001 - una alerta caída no debe tumbar el triage
        log.exception("no se pudo enviar la alerta de diagnóstico")


async def triage(pool) -> int:
    """Marca huellas nuevas (alerta) y resueltas (sin reportes en RESOLVE_HOURS)."""
    sent = 0
    async with pool.acquire() as conn:
        new_rows = await conn.fetch(
            """
            select r.fingerprint,
                   (array_agg(r.kind order by r.created_at desc))[1] as kind,
                   (array_agg(r.code order by r.created_at desc))[1] as code,
                   (array_agg(r.app_version order by r.created_at desc))[1] as latest_version,
                   count(*) as hits,
                   count(distinct r.device) as devices
            from diag_reports r
            left join diag_alert_state s on s.fingerprint = r.fingerprint
            where s.fingerprint is null
            group by r.fingerprint
            order by max(r.created_at) desc
            """
        )
        for row in new_rows:
            await conn.execute(
                "insert into diag_alert_state (fingerprint) values ($1) on conflict do nothing",
                row["fingerprint"],
            )
            await asyncio.to_thread(send_alert, alert_text(row))
            sent += 1
        resolved = await conn.fetch(
            """
            update diag_alert_state s set resolved_at = now()
            where s.resolved_at is null
              and not exists (
                  select 1 from diag_reports r
                  where r.fingerprint = s.fingerprint
                    and r.created_at > now() - make_interval(hours => $1)
              )
            returning s.fingerprint
            """,
            RESOLVE_HOURS,
        )
        for row in resolved:
            await asyncio.to_thread(
                send_alert,
                f"✅ Resuelto: {row['fingerprint']} (sin reportes en {RESOLVE_HOURS} h)",
            )
            sent += 1
    return sent


async def loop(pool) -> None:
    while True:
        try:
            await triage(pool)
        except Exception:  # noqa: BLE001 - el triage no tumba el worker
            log.exception("triage de diagnóstico falló")
        await asyncio.sleep(INTERVAL)
