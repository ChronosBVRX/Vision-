#!/usr/bin/env bash
# Crea los dos Payment Links de apoyo para Vision+ (Stripe).
#
# Uso seguro:
#   1. En Stripe: Developers -> API keys -> Create restricted key
#      Permisos: Products (write), Prices (write), Payment Links (write).
#   2. Ejecuta en tu computadora (NUNCA pegues la llave en el chat):
#        export STRIPE_SECRET_KEY='rk_test_...'   # primero en modo prueba
#        bash tools/create_stripe_links.sh
#   3. Prueba los links con la tarjeta 4242 4242 4242 4242.
#   4. Repite con la llave live ('rk_live_...') y guarda las dos URLs.
#
# Variables opcionales:
#   CURRENCY=mxn        (por defecto pesos mexicanos)
#   CAFE_AMOUNT=10000   (centavos: $100.00 MXN)
#   CHELAS_AMOUNT=15000 (centavos: $150.00 MXN)

set -euo pipefail

# Si no viene por variable de entorno, se pide aquí y NO se muestra en pantalla.
# La llave se queda en la memoria de este proceso; no se escribe en ningún archivo.
if [ -z "${STRIPE_SECRET_KEY:-}" ]; then
  read -rsp "Stripe secret key (no se mostrará): " STRIPE_SECRET_KEY
  echo
fi
: "${STRIPE_SECRET_KEY:?Falta la llave de Stripe}"

CURRENCY="${CURRENCY:-mxn}"
CAFE_AMOUNT="${CAFE_AMOUNT:-10000}"
CHELAS_AMOUNT="${CHELAS_AMOUNT:-15000}"
API="https://api.stripe.com/v1"

api() {
  curl -sS -u "${STRIPE_SECRET_KEY}:" "$@"
}

json_get() {
  python3 -c 'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$1"
}

die_with() {
  echo "ERROR: $1" >&2
  exit 1
}

make_link() {
  local name="$1"
  local amount="$2"

  echo "Creando producto: ${name}..." >&2
  local product product_id
  product=$(api -X POST "${API}/products" \
    --data-urlencode "name=${name}" \
    --data-urlencode "description=Apoyo voluntario para el desarrollo de Vision+")
  product_id=$(printf '%s' "${product}" | json_get id)
  [ -n "${product_id}" ] || die_with "No se pudo crear el producto: ${product}"

  echo "Creando precio: ${amount} ${CURRENCY}..." >&2
  local price price_id
  price=$(api -X POST "${API}/prices" \
    --data-urlencode "product=${product_id}" \
    --data-urlencode "currency=${CURRENCY}" \
    --data-urlencode "unit_amount=${amount}")
  price_id=$(printf '%s' "${price}" | json_get id)
  [ -n "${price_id}" ] || die_with "No se pudo crear el precio: ${price}"

  echo "Creando Payment Link..." >&2
  local link url
  link=$(api -X POST "${API}/payment_links" \
    --data-urlencode "line_items[0][price]=${price_id}" \
    --data-urlencode "line_items[0][quantity]=1" \
    --data-urlencode "customer_creation=always" \
    --data-urlencode "custom_fields[0][key]=mensaje" \
    --data-urlencode "custom_fields[0][label][custom]=Mensaje para Vision+" \
    --data-urlencode "custom_fields[0][label][type]=custom" \
    --data-urlencode "custom_fields[0][type]=text" \
    --data-urlencode "custom_fields[0][optional]=true" \
    --data-urlencode "after_completion[type]=hosted_confirmation" \
    --data-urlencode "after_completion[hosted_confirmation][custom_message]=¡Gracias por apoyar Vision+!")
  url=$(printf '%s' "${link}" | json_get url)
  [ -n "${url}" ] || die_with "No se pudo crear el Payment Link: ${link}"

  printf '%s' "${url}"
}

CAFE_URL=$(make_link "Apoyo Vision+ · Café" "${CAFE_AMOUNT}")
CHELAS_URL=$(make_link "Apoyo Vision+ · Chelas" "${CHELAS_AMOUNT}")

echo
echo "Listo. Estas son las URLs públicas (seguras para compartir):"
echo "CAFE_URL=${CAFE_URL}"
echo "CHELAS_URL=${CHELAS_URL}"
echo
echo "Pásalas al chat y yo integro la pantalla de apoyo en Vision+."
