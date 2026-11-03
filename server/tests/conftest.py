"""Harness de tests del servidor: importa los módulos reales (sync/ y api/) sin
Docker ni red. Solo fija variables de entorno tontas; la DB nunca se toca
(los tests usan dicts/rows sintéticos y lógica pura)."""

import os
import sys

os.environ.setdefault("DATABASE_URL", "postgresql://test:test@localhost:5432/test")
os.environ.setdefault("API_KEY", "test-key")
# Los tests no tocan la API pública de Fox One; el EPG básico empaquetado sí
# se lee (es local) para probar la regla Pachuca-Tigres → AYM.
os.environ.setdefault("FOX_SCHEDULE_OFFLINE", "1")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for sub in ("sync", "api"):
    path = os.path.join(ROOT, sub)
    if path not in sys.path:
        sys.path.insert(0, path)
