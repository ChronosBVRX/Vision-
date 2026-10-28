"""Tests unitarios para la lógica de normalización y entrega de la guía EPG."""

from datetime import datetime, timedelta, timezone
from pathlib import Path

import app
import epg
import epg_channels


def test_canonical_channel_normalization():
    # Canales nacionales mexicanos
    assert app.canonical_channel("Canal 5 HD") == "canal5"
    assert app.canonical_channel("Canal 5 de México (XHGC)") == "canal5"
    assert app.canonical_channel("Azteca 7 HD") == "azteca7"
    assert app.canonical_channel("Azteca 7 (XHLAT) Nuevo Laredo MX") == "azteca7"
    assert app.canonical_channel("Las Estrellas Mex HD") == "lasestrellas"
    assert app.canonical_channel("Canal 2 de México (Canal Las Estrellas - XEW)") == "lasestrellas"
    assert app.canonical_channel("Azteca 1 HD") == "azteca1"
    assert app.canonical_channel("Canal Azteca Uno") == "azteca1"

    # Canales deportivos clave
    assert app.canonical_channel("TUDN MX HD") == "tudn"
    assert app.canonical_channel("Canal TUDN (México)") == "tudn"
    assert app.canonical_channel("Fox Sports 1 MX") == "foxsports"
    assert app.canonical_channel("Fox Sports Central HD") == "foxsports"
    assert app.canonical_channel("FOX SPORTS 2") == "foxsports2"
    assert app.canonical_channel("Fox Sports 2 Central HD") == "foxsports2"
    assert app.canonical_channel("FOX SPORTS 3") == "foxsports3"
    assert app.canonical_channel("FOX SPORTS PREMIUM") == "foxsportspremium"
    assert app.canonical_channel("Claro Sports 1 HD") == "clarosports1"
    assert app.canonical_channel("WIN Sports HD") == "winsports"

    # Cine y series
    assert app.canonical_channel("HBO HD") == "hbo"
    assert app.canonical_channel("HBO PLUS HD") == "hboplus"
    assert app.canonical_channel("HBO FAMILY HD") == "hbofamily"
    assert app.canonical_channel("Canal Cinemax (México)") == "cinemax"
    assert app.canonical_channel("Cinemax HD") == "cinemax"
    assert app.canonical_channel("WARNER HD") == "warner"
    assert app.canonical_channel("SONY HD") == "sony"


def test_canonical_channel_never_merges_different_brands():
    """La regla vieja «Canal … 2» mandaba TODO a Las Estrellas: se cerró."""
    assert app.canonical_channel("Canal ESPN 2 (Chile)") == "espn2"
    assert app.canonical_channel("Canal ESPN 2 (Bolivia)") == "espn2"
    assert app.canonical_channel("Canal Fox Sports 2 (México)") == "foxsports2"
    assert app.canonical_channel("Canal HBO 2 Latinoamérica") == "hbo2"
    assert app.canonical_channel("Canal History 2 (México)") == "history2"
    assert app.canonical_channel("Canal Golden Premier 2") == "goldenpremier2"
    assert app.canonical_channel("ESPN 6 HD") == "espn6"
    assert app.canonical_channel("ESPN 7 HD") == "espn7"
    assert app.canonical_channel("HBO ZONE USA HD") == "hbozone"
    assert app.canonical_channel("Canal USA Network (México)") == "usanetwork"
    assert app.canonical_channel("Canal Investigation Discovery (México)") == "id"
    assert app.canonical_channel("Canal Elgourmet") == "elgourmet"
    assert app.canonical_channel("Canal History Channel") == "history"
    assert app.canonical_channel("Claro Cinema") == "clarocinema"
    assert app.canonical_channel("Discovery H&H") == "discoveryhomehealth"
    assert app.canonical_channel("AZ Corazon HD") == "aztezacorazon"
    assert app.canonical_channel("America TV Peru") == "america"
    assert app.canonical_channel("ADN MX HD") == "adn40"
    assert app.canonical_channel("3ABN Latino HD") == "3abn"
    assert app.canonical_channel("Canal NTN24 (Nuestra Tele Noticias 24 Horas)") == "ntn24"
    assert app.canonical_channel("Win Sports+") == "winsportsplus"
    assert app.canonical_channel("WIN Sports HD") == "winsports"


def test_canonical_channel_keeps_numbers_and_timeshifts_apart():
    assert app.canonical_channel("Canal 24 Horas (Chile)") == "24horas"
    assert app.canonical_channel("24 HORAS HD") == "24horas"
    assert app.canonical_channel("Canal 13 de Chile") == "canal13"
    assert app.canonical_channel("Canal 13 de Argentina (El Trece)") == "canal13"
    assert app.canonical_channel("Canal Azteca Uno -2 Horas") == "azteca1m2"
    assert app.canonical_channel("Canal de las Estrellas -1 Hora - México (XEW)") == "lasestrellasm1"


def test_channel_country_from_name_and_code():
    assert app.channel_country("CANAL 5 HN HD") == "hn"
    assert app.channel_country("CANAL 2 NIC HD") == "ni"
    assert app.channel_country("CANAL 2 SV HD") == "sv"
    assert app.channel_country("CANAL 11 CR HD") == "cr"
    assert app.channel_country("CANAL 13 CHILE HD") == "cl"
    assert app.channel_country("Canal ESPN 2 (Chile)") == "cl"
    assert app.channel_country("TUDN MX HD") == "mx"
    assert app.channel_country("TUDN USA HD") == "us"
    assert app.channel_country("FOX SPORTS 1 USA HD") == "us"
    assert app.channel_country("HBO HD") == ""
    assert app.channel_country("Canal 24 Horas (TVE)") == "es"
    assert app.channel_country("cyx-ElNueve") == "ar"


def test_epg_norms_for_never_crosses_countries():
    # Un Canal 5 hondureño no puede recibir la guía del Canal 5 mexicano.
    assert app.epg_norms_for("CANAL 5 HN HD") == ["hn:canal5"]
    assert app.epg_norms_for("CANAL 9 NIC HD") == ["ni:canal9"]
    assert app.epg_norms_for("CANAL 13 NI HD") == ["ni:canal13"]
    assert app.epg_norms_for("CANAL 11 CR HD") == ["cr:canal11"]
    assert app.epg_norms_for("FOX SPORTS 1 USA HD") == ["us:foxsports"]
    # México (o sin país) usa primero la señal mexicana; las internacionales
    # pueden caer a un feed latino si México no tiene esa cadena.
    assert app.epg_norms_for("Canal 5 HD") == ["mx:canal5"]
    assert app.epg_norms_for("TUDN MX HD")[0] == "mx:tudn"
    assert app.epg_norms_for("HBO HD")[0] == "mx:hbo"
    # El código es una pista: «cyx-ElNueve» es el Canal 9 argentino.
    assert app.epg_norms_for("CANAL 9 HD", "cyx-ElNueve") == ["ar:canal9"]
    assert app.epg_norms_for("") == []


def test_epg_worker_canonical_matches_api():
    samples = [
        "Canal 5 HD",
        "Azteca 7 HD",
        "Las Estrellas Mex HD",
        "TUDN MX HD",
        "Fox Sports 1 MX",
        "HBO HD",
        "Cinemax HD",
        "WIN Sports HD",
        "Canal ESPN 2 (Chile)",
        "Canal Fox Sports 2 (México)",
        "Canal 24 Horas (Chile)",
        "Canal 13 de Chile",
    ]
    for s in samples:
        assert epg.canonical_channel(s) == app.canonical_channel(s)
        assert epg.channel_country(s) == app.channel_country(s)
        assert epg.epg_norms_for(s) == app.epg_norms_for(s)


def test_epg_channels_copies_are_identical():
    root = Path(__file__).resolve().parent.parent
    assert (root / "sync" / "epg_channels.py").read_bytes() == (
        root / "api" / "epg_channels.py"
    ).read_bytes()


def test_every_feed_has_a_known_country():
    assert len(epg.FEEDS) >= 8
    for url in epg.FEEDS:
        assert epg.feed_country(url) in {"mx", "ar", "co", "cl", "us", "pe", "ec"}


def test_upsert_sql_carries_the_country():
    sql = " ".join(epg.UPSERT_SQL.split())
    assert "country" in sql
    assert "values ($1, $2, $3, $4, $5, $6, $7, $8, $9, now())" in sql


def test_parse_xmltv_date():
    dt = epg.parse_xmltv_date("20261010001500 -0500")
    assert dt is not None
    assert dt.year == 2026
    assert dt.month == 10
    assert dt.day == 10
    assert dt.hour == 0
    assert dt.minute == 15
    assert dt.utcoffset().total_seconds() == -5 * 3600


def _row(title, start, end, norm="mx:tudn"):
    return {
        "norm_name": norm,
        "title": title,
        "description": "",
        "start_time": start,
        "end_time": end,
    }


def test_epg_pick_uses_the_first_norm_with_rows():
    now = datetime(2026, 10, 24, 18, 0, tzinfo=timezone.utc)
    rows = [
        _row("A", now - timedelta(hours=1), now + timedelta(hours=1), "ar:tudn"),
        _row("B", now + timedelta(hours=1), now + timedelta(hours=2), "ar:tudn"),
        _row("C", now - timedelta(hours=1), now + timedelta(hours=1), "mx:tudn"),
        _row("C", now - timedelta(hours=1), now + timedelta(hours=1), "mx:tudn"),
    ]
    norm, chosen = app._epg_pick(rows, ["mx:tudn", "ar:tudn"])
    assert norm == "mx:tudn"
    assert [r["title"] for r in chosen] == ["C"]


def test_epg_unique_removes_duplicate_signals():
    now = datetime(2026, 10, 24, 18, 0, tzinfo=timezone.utc)
    rows = [
        _row("A", now, now + timedelta(hours=1)),
        _row("A distinto", now, now + timedelta(hours=1)),
        _row("B", now + timedelta(hours=1), now + timedelta(hours=2)),
    ]
    assert [r["title"] for r in app._epg_unique(rows)] == ["A", "B"]


def test_epg_window_finds_now_and_next():
    now = datetime(2026, 10, 24, 18, 0, tzinfo=timezone.utc)
    rows = [
        _row("A", now - timedelta(hours=1), now + timedelta(hours=1)),
        _row("B", now + timedelta(hours=1), now + timedelta(hours=2)),
    ]
    now_item, next_item = app._epg_window(rows, int(now.timestamp() * 1000))
    assert now_item["name"] == "A"
    assert next_item["name"] == "B"
    assert now_item["startMs"] < now_item["endMs"]


def test_epg_brief_is_json_ready():
    now = datetime(2026, 10, 24, 18, 0, tzinfo=timezone.utc)
    brief = app._epg_brief(_row("A", now, now + timedelta(hours=1)))
    assert brief["name"] == "A"
    assert brief["startMs"] == int(now.timestamp() * 1000)
    assert app._epg_brief(None) is None
