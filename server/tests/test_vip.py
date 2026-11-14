"""Broker VIP: selección de pistas, expiración y semáforo de la cuenta (sin red)."""

import time

import vip
from vip import _license_expired, _parse_epoch, _read_entitlement, best_medias


def media(code: str, codec: str, expired: int = 200, tag: str = "free") -> dict:
    return {
        "contentId": code,
        "encodeFormat": codec,
        "videoFormat": "ts",
        "audioInfo": "spa",
        "licenseList": [
            {
                "license": (
                    f"app_id=magmob&tag={tag}&scheme=md5-01&media_code={code}"
                    f"&expired={expired}&token=ABC"
                )
            }
        ],
    }


def test_best_media_prefiere_1080_y_h265() -> None:
    payload = {
        "episodeList": [
            {
                "totalMovieList": [
                    {"quality": "480p", "movieList": [media("sd", "h264")]},
                    {
                        "quality": "1080p",
                        "movieList": [media("fhd-h264", "h264"), media("fhd-h265", "h265")],
                    },
                ]
            }
        ]
    }
    picked = best_medias(payload)
    assert picked[0]["n"] == "fhd-h265"
    assert picked[0]["q"] == "1080p"
    assert picked[1]["n"] == "fhd-h264"
    assert picked[-1]["q"] == "480p"


def test_best_media_descarta_pistas_sin_licencia() -> None:
    payload = {
        "episodeList": [
            {
                "totalMovieList": [
                    {"quality": "1080p", "movieList": [{"contentId": "sin-licencia"}]},
                    {"quality": "720p", "movieList": [media("con-licencia", "h264")]},
                ]
            }
        ]
    }
    assert [m["n"] for m in best_medias(payload)] == ["con-licencia"]


def test_expiracion_de_la_licencia() -> None:
    lic = "app_id=magmob&tag=free&media_code=x&expired=1794692340&token=ABC"
    assert _license_expired(lic) == 1794692340
    assert _license_expired("sin-campo") == 0


def test_parse_epoch_acepta_epoch_fecha_y_basura() -> None:
    assert _parse_epoch(1794692340) == 1794692340
    assert _parse_epoch(1794692340000) == 1794692340
    assert _parse_epoch("2026-11-16 01:46:17") > 1794692340
    assert _parse_epoch("") == 0
    assert _parse_epoch("mañana") == 0


def test_semaforo_toma_el_vencimiento_mas_lejano(monkeypatch) -> None:
    now = int(time.time())
    monkeypatch.setattr(vip, "_DAYS", None)
    monkeypatch.setattr(vip, "_DAYS_AT", 0.0)
    monkeypatch.setattr(vip, "_ENTITLED", None)
    monkeypatch.setattr(vip, "_ACCOUNT_EXPIRES", 0)

    active = {
        "authInfoList": [
            {"status": "1", "invalidTime": "2020-01-01 00:00:00"},
            {"status": "1", "invalidTime": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(now + 30 * 86400))},
        ]
    }
    _read_entitlement(active, "tok", "uid")
    assert vip._ENTITLED is True
    assert vip._DAYS == 30
    assert vip._ACCOUNT_EXPIRES > now


def test_semaforo_cuenta_vencida(monkeypatch) -> None:
    monkeypatch.setattr(vip, "_DAYS", None)
    monkeypatch.setattr(vip, "_DAYS_AT", 0.0)
    monkeypatch.setattr(vip, "_ENTITLED", None)
    monkeypatch.setattr(vip, "_ACCOUNT_EXPIRES", 0)

    active = {"authInfoList": [{"status": "1", "invalidTime": "2020-01-01 00:00:00"}]}
    _read_entitlement(active, "tok", "uid")
    assert vip._ENTITLED is False
    assert vip._DAYS == 0


def test_status_no_revienta_sin_sesion() -> None:
    data = vip.status()
    assert "daysLeft" in data and "mismatch" in data and "cacheValid" in data
