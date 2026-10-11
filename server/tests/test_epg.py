"""Tests unitarios para la lógica de normalización EPG."""

import app
import epg
from datetime import datetime


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
    ]
    for s in samples:
        assert epg.canonical_channel(s) == app.canonical_channel(s)


def test_parse_xmltv_date():
    dt = epg.parse_xmltv_date("20261010001500 -0500")
    assert dt is not None
    assert dt.year == 2026
    assert dt.month == 10
    assert dt.day == 10
    assert dt.hour == 0
    assert dt.minute == 15
    assert dt.utcoffset().total_seconds() == -5 * 3600
