"""Rate limiting del API por IP y grupo de endpoints.

El API key viaja en el APK, así que no sirve como autenticación. Este límite no
detiene a un atacante decidido con muchas IPs, pero sí evita que una sola IP
descargue el índice en bucle, martille /v1/search o sature /v1/diag.

El API corre con un solo worker de uvicorn, así que un contador en memoria es
suficiente y no añade Redis ni dependencias.
"""

from __future__ import annotations

import threading
import time
from collections import deque
from typing import Mapping

WINDOW_SECONDS = 60.0

# Grupos por endpoint. Los valores por defecto se pueden sobreescribir desde
# app.py con variables de entorno.
DEFAULT_LIMITS = {
    "index": 60,
    "search": 120,
    "gee38": 120,
    "epg": 120,
    "diag": 60,
    "config": 300,
    "default": 600,
}


def group_for_path(path: str) -> str:
    """Agrupa la ruta para que /v1/index no consuma el cupo de /v1/search."""
    if path.startswith("/v1/index"):
        return "index"
    if path.startswith("/v1/search"):
        return "search"
    if path.startswith("/v1/gee38"):
        return "gee38"
    if path.startswith("/v1/epg"):
        return "epg"
    if path.startswith("/v1/diag"):
        return "diag"
    if path.startswith("/v1/config"):
        return "config"
    return "default"


def client_ip(
    forwarded_for: str | None,
    real_ip: str | None,
    fallback: str | None,
) -> str:
    """IP real detrás de Caddy.

    Caddy añade la IP del cliente al final de X-Forwarded-For. Se usa la última
    entrada para que un cliente no pueda falsificar la primera. Si no hay
    cabeceras, se usa la IP de la conexión (p. ej. pruebas o acceso local).
    """
    if forwarded_for:
        parts = [part.strip() for part in forwarded_for.split(",") if part.strip()]
        if parts:
            return parts[-1]
    if real_ip:
        value = real_ip.strip()
        if value:
            return value
    return (fallback or "unknown").strip() or "unknown"


class RateLimiter:
    """Ventana deslizante de 60 s por (grupo, IP)."""

    def __init__(
        self,
        limits: Mapping[str, int] | None = None,
        default: int = 300,
        enabled: bool = True,
    ) -> None:
        self.limits = dict(DEFAULT_LIMITS if limits is None else limits)
        self.default = max(1, int(default))
        self.enabled = bool(enabled)
        self._events: dict[str, deque[float]] = {}
        self._lock = threading.Lock()
        self._calls = 0

    def limit_for(self, group: str) -> int:
        return max(1, int(self.limits.get(group, self.default)))

    def allow(
        self,
        ip: str,
        group: str = "default",
        now: float | None = None,
    ) -> tuple[bool, int]:
        """Devuelve (permitido, segundos_para_reintentar)."""
        if not self.enabled:
            return True, 0
        current = time.monotonic() if now is None else now
        bucket = f"{group}:{ip}"
        limit = self.limit_for(group)
        with self._lock:
            events = self._events.get(bucket)
            if events is None:
                events = deque()
                self._events[bucket] = events
            cutoff = current - WINDOW_SECONDS
            while events and events[0] <= cutoff:
                events.popleft()
            if len(events) >= limit:
                retry = max(1, int(WINDOW_SECONDS - (current - events[0])) + 1)
                return False, retry
            events.append(current)
            self._calls += 1
            if self._calls % 10_000 == 0:
                self._cleanup(cutoff)
            return True, 0

    def _cleanup(self, cutoff: float) -> None:
        """Olvida las IPs que ya no tienen eventos en la ventana."""
        empty = []
        for bucket, events in self._events.items():
            while events and events[0] <= cutoff:
                events.popleft()
            if not events:
                empty.append(bucket)
        for bucket in empty:
            self._events.pop(bucket, None)
