"""Broker VIP: selección de pistas, expiración y semáforo de la cuenta (sin red)."""

import hashlib
import time

import pytest

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
            {"status": "1", "invalidTime": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(now + 30 * 86400 + 120))},
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


def test_semaforo_no_confunde_cortesia_vencida(monkeypatch) -> None:
    """`hasFreeAuth=1` sigue vivo tras el vencimiento; no debe contar como días."""
    monkeypatch.setattr(vip, "_DAYS", None)
    monkeypatch.setattr(vip, "_DAYS_AT", 0.0)
    monkeypatch.setattr(vip, "_ENTITLED", None)
    monkeypatch.setattr(vip, "_ACCOUNT_EXPIRES", 0)

    active = {
        "authInfoList": [{"status": "1", "invalidTime": "2026-11-16 01:46:17"}],
        "hasFreeAuth": "1",
        "getFreeAuthFlag": "no",
        "remainingDays": 0,
        "hasPay": "0",
    }
    _read_entitlement(active, "tok", "uid")
    assert vip._ENTITLED is False


def test_play_prueba_columnas_de_respaldo_si_la_primera_falla(monkeypatch) -> None:
    monkeypatch.setattr(vip, "_ENTITLED", True)
    monkeypatch.setattr(vip, "_CACHE", {})
    monkeypatch.setattr(vip, "_session", lambda force=False: ("tok", "uid"))
    monkeypatch.setattr(vip, "FALLBACK_COLUMNS", (1,))

    calls: list[int] = []

    def fake_post(path, spec, timeout=vip.REQUEST_TIMEOUT):
        calls.append(int(spec.get("columnId") or 0))
        if spec.get("columnId") == 99:
            raise RuntimeError("portal100002 栏目信息不存在")
        return {
            "episodeList": [
                {
                    "totalMovieList": [
                        {
                            "quality": "1080p",
                            "movieList": [
                                {
                                    "contentId": "ok",
                                    "encodeFormat": "h265",
                                    "videoFormat": "ts",
                                    "audioInfo": "spa",
                                    "licenseList": [{"license": "expired=200&token=x"}],
                                }
                            ],
                        }
                    ]
                }
            ]
        }

    monkeypatch.setattr(vip, "_post", fake_post)
    result = vip._play("cid", "", 99, "0", [1])
    assert calls == [99, 1]
    assert result["medias"][0]["q"] == "1080p"


def test_play_cuenta_vencida_no_toca_el_portal(monkeypatch) -> None:
    monkeypatch.setattr(vip, "_ENTITLED", False)
    monkeypatch.setattr(vip, "_CACHE", {})

    def boom(*args, **kwargs):
        raise AssertionError("no debe llamar al portal con la cuenta vencida")

    monkeypatch.setattr(vip, "_post", boom)
    with pytest.raises(vip.AccountNoDays):
        vip._play("cid", "", 0, "0", [1])


def test_combine_password_tiene_el_formato_de_la_app() -> None:
    password = "clave-de-prueba"
    combined = vip._combine_password(password)
    expected_md5 = hashlib.md5((password + vip.LOGIN_SALT).encode()).hexdigest()
    assert combined.startswith(expected_md5)
    # MD5 (32) + AES-ECB con padding (24 en Base64) = 56.
    assert len(combined) == 56


def test_email_login_usa_la_clave_hasheada(monkeypatch) -> None:
    monkeypatch.setattr(vip, "VIP_EMAIL", "cuenta@example.com")
    monkeypatch.setattr(vip, "VIP_PASSWORD", "secreta")
    monkeypatch.setattr(vip, "VIP_PASSWORD_HASH", "")
    captured = {}

    def fake_post(path, spec, timeout=vip.REQUEST_TIMEOUT):
        captured["path"] = path
        captured["spec"] = spec
        future = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(time.time() + 30 * 86400))
        return {
            "userToken": "token-email",
            "userId": "42",
            "hasPay": "1",
            "remainingDays": 30,
            "authInfoList": [{"status": "1", "invalidTime": future}],
        }

    monkeypatch.setattr(vip, "_post", fake_post)
    token, uid = vip._mint()
    assert (token, uid) == ("token-email", "42")
    assert captured["path"] == "/api/portalCore/v8/login"
    assert captured["spec"]["accountType"] == "2"
    assert captured["spec"]["userName"] == "cuenta@example.com"
    assert captured["spec"]["password"] == vip._combine_password("secreta")
    assert vip._DAYS == 30
    assert vip._ENTITLED is True


def test_status_no_revienta_sin_sesion() -> None:
    data = vip.status()
    assert "daysLeft" in data and "mismatch" in data and "cacheValid" in data
