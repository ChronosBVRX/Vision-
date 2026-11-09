# Donaciones con Stripe (Vision+)

## Regla de seguridad

**La llave secreta de Stripe (`sk_live_...`, `sk_test_...`) no va al APK, al repo,
a GitHub ni al chat.** Solo se usa en el script local o en el `.env` del VPS si
algún día se crea un backend de Checkout. Los **Payment Links** públicos
(`https://buy.stripe.com/...`) sí se pueden compartir.

## Crear los dos links

Opción rápida, en el Dashboard:

1. **Stripe → Payment Links → Create payment link**.
2. `Apoyo Vision+ · Café`, **$100 MXN**, pago único.
3. `Apoyo Vision+ · Chelas`, **$150 MXN**, pago único.
4. Copiar las dos URLs `https://buy.stripe.com/...`.

Opción con script (la llave se queda en tu terminal):

```bash
cd /home/chronos/Escritorio/Vision+/repo
export STRIPE_SECRET_KEY='rk_test_...'   # primero en modo prueba
bash tools/create_stripe_links.sh
```

El script imprime `CAFE_URL=...` y `CHELAS_URL=...`.

## Publicar los links en el VPS

Editar `/opt/streaming-backend/.env`:

```env
STRIPE_CAFE_URL=https://buy.stripe.com/...
STRIPE_CHELAS_URL=https://buy.stripe.com/...
```

Reiniciar solo la API:

```bash
cd /opt/streaming-backend
docker compose up -d api
```

La página pública queda en:

```text
https://visionplusv2.duckdns.org/apoyar
```

Si los links están vacíos, la página muestra **«Pagos en configuración»** y no
rompe nada.

## Encender el aviso en la app

La app trae el aviso de inicio, el QR de las pantallas de carga y el botón de
«Acerca de» **apagados por defecto** (`donation_prompt=false`). Se encienden
desde la configuración remota cuando los links ya funcionan:

```bash
curl -X POST "https://visionplusv2.duckdns.org/v1/config" \
  -H "X-Admin-Key: $CONFIG_ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"donation_prompt": true}'
```

La app refresca la configuración al arrancar (y cada 6 h). No hace falta otra
OTA para encenderlo.

Para apagarlo:

```bash
curl -X POST "https://visionplusv2.duckdns.org/v1/config" \
  -H "X-Admin-Key: $CONFIG_ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"donation_prompt": false}'
```

## Comportamiento

- **Inicio en TV**: un diálogo con QR grande, mensaje personal, una vez al día.
  **No abre navegador ni enlaces**: todo se hace en el teléfono.
- **Inicio en móvil**: el mismo mensaje, sin QR, con botón directo
  «Apoyar Vision+».
- **Carga del reproductor**: QR en la tele; en el teléfono, un texto
  «Apoyar Vision+ · toca aquí» que abre la página.
- **Acerca de en TV**: «Apoyar Vision+» abre el diálogo con QR, nunca el navegador.
- **Acerca de en móvil**: abre la página directamente.
- **Página `/apoyar`**: dos botones de Stripe; se actualiza sin OTA.
