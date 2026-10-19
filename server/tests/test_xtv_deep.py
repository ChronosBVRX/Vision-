"""Pasada profunda de colecciones: cuándo toca y cuándo se aplaza (disco).

Contexto medido el 2026-10-18: la pasada trae los clásicos que no caben en el tope
de 10.000 títulos del "All" ni en las 3 páginas de cada colección («Guerra de Papás»
en Comedia página 73). Al terminarla, el volumen de Docker del VPS (15 GB, donde
vive Postgres) se llenó y Postgres entró en crash-loop por el WAL: por eso la pasada
se aplaza sola cuando queda poco disco.
"""

import asyncio
import types
from datetime import datetime, timedelta, timezone

import xtv


class _FakeConn:
    def __init__(self, row):
        self.row = row

    async def fetchrow(self, *args, **kwargs):
        return self.row


class _FakeAcquire:
    def __init__(self, conn):
        self.conn = conn

    async def __aenter__(self):
        return self.conn

    async def __aexit__(self, *exc):
        return False


class _FakePool:
    def __init__(self, row):
        self.row = row

    def acquire(self):
        return _FakeAcquire(_FakeConn(self.row))


def _disk(free_gb: float):
    return types.SimpleNamespace(free=int(free_gb * (1 << 30)))


def test_deep_pass_runs_when_there_is_no_mark(monkeypatch):
    monkeypatch.setattr(xtv.shutil, "disk_usage", lambda path: _disk(10))
    assert asyncio.run(xtv._deep_due(_FakePool(None), 168)) is True


def test_deep_pass_waits_until_the_interval_passes(monkeypatch):
    monkeypatch.setattr(xtv.shutil, "disk_usage", lambda path: _disk(10))
    recent = {"updated_at": datetime.now(timezone.utc) - timedelta(hours=3)}
    old = {"updated_at": datetime.now(timezone.utc) - timedelta(hours=200)}
    assert asyncio.run(xtv._deep_due(_FakePool(recent), 168)) is False
    assert asyncio.run(xtv._deep_due(_FakePool(old), 168)) is True


def test_deep_pass_is_postponed_when_the_disk_is_tight(monkeypatch):
    # Sin disco no se toca ni la base: jamás un crash-loop por llenar el volumen.
    monkeypatch.setattr(xtv.shutil, "disk_usage", lambda path: _disk(0.5))

    class _Boom:
        def acquire(self):
            raise AssertionError("no debía consultar la base con el disco lleno")

    assert asyncio.run(xtv._deep_due(_Boom(), 168)) is False
    # 0 horas = desactivada por configuración (no importa el disco).
    assert asyncio.run(xtv._deep_due(_Boom(), 0)) is False
