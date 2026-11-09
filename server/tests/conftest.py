"""Harness de tests del servidor: importa los módulos reales (sync/ y api/) sin
Docker ni red. Solo fija variables de entorno tontas; la DB nunca se toca
(los tests usan dicts/rows sintéticos y lógica pura)."""

import os
import sys

import pytest

os.environ.setdefault("DATABASE_URL", "postgresql://test:test@localhost:5432/test")
os.environ.setdefault("API_KEY", "test-key")
# Los tests de API no deben chocar con el rate limiter global.
os.environ.setdefault("RATE_LIMIT_ENABLED", "0")
# Los tests no tocan la API pública de Fox One; el EPG básico empaquetado sí
# se lee (es local) para probar la regla Pachuca-Tigres → AYM.
os.environ.setdefault("FOX_SCHEDULE_OFFLINE", "1")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for sub in ("sync", "api"):
    path = os.path.join(ROOT, sub)
    if path not in sys.path:
        sys.path.insert(0, path)


@pytest.fixture(autouse=True)
def _clear_api_caches():
    """Las cachés TTL del API no deben filtrar respuestas entre pruebas."""
    try:
        import app as api_app
    except Exception:  # pragma: no cover - módulos que no importan el API
        yield
        return

    def clear():
        for name in ("_INDEX_CACHE", "_INDEX_LAST_GOOD", "_GEE_EVENTS_CACHE", "_GEE_SPORTS_CACHE"):
            cache = getattr(api_app, name, None)
            if isinstance(cache, dict):
                cache.clear()
        locks = getattr(api_app, "_FILL_LOCKS", None)
        if isinstance(locks, dict):
            locks.clear()

    clear()
    yield
    clear()
