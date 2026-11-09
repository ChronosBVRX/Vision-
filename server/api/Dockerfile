FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py banners.py calendar_feed.py markers.py slots.py wire.py portal.py episodes.py sync.py epg_channels.py sports_map.py sports_channels.json fox_basic_epg.json fox_one_schedule.json rate_limit.py ./

RUN useradd --system --uid 10001 --create-home app
USER app

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--no-server-header"]
