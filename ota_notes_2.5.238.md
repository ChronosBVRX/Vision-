# Vision+ 2.5.238 — BETA: limpiar la tele e instalar Vision+

**Canal de pruebas.** Esta versión se publica como pre-release (tag `beta`), así que los usuarios
del canal oficial no la reciben. Para probarla en tu teléfono: **Ajustes → Actualizaciones y
Sistema → Canal de pruebas (beta)** y después **Buscar actualizaciones**.

## Limpiar la tele desde el teléfono

- **Ajustes → Instalar en TV** ahora hace el trabajo completo: limpia el bloatware, instala
  **Vision+**, instala **Vision+ Home** y deja el launcher como **Inicio** del televisor.
- **335 listas por modelo** (TCL, Hisense, Philips, Xiaomi, Google TV, Fire TV y más), portadas
  de TidyTV / CleanYourTV. La app elige la lista por modelo, placa y versión de Android.
- **Revisión antes de tocar nada**: muestra la lista probada (o si es un modelo parecido), los
  grupos y el número exacto de apps que se apagarán. Por defecto solo se marcan **anuncios,
  rastreo y apps preinstaladas**; voz, casting, actualizaciones y streaming quedan apagados.
- **Nunca desinstala**: usa `pm disable-user`. Antes de cambiar guarda una copia en el teléfono
  y escribe la lista de deshacer **en el propio televisor**.
- **Restaurar** reactiva todo lo apagado y devuelve el Inicio original.
- **Android 8/9**: nuevo botón **Buscar teles en la red** que barre el puerto 5555. También se
  puede escribir la IP a mano.
- **Android 11/12/13**: emparejamiento con el código de 6 dígitos de «Depuración inalámbrica».
- **Fire TV**: limpia e instala, pero no intenta cambiar el Inicio (Amazon no lo permite).

## Seguridad del cambio de Inicio

- No se retira un launcher marcado como `PERSISTENT`.
- No se apaga la app de configuración de Google TV sin comprobar que no sea dueña del Wi-Fi y
  que la configuración inicial haya terminado.
- El Inicio de fábrica retirado también queda anotado en la lista de deshacer del televisor.

## Cómo probarla

1. En el teléfono: **Ajustes → Actualizaciones y Sistema → Canal de pruebas (beta)**.
2. Pulsa **Buscar actualizaciones** y acepta la 2.5.238.
3. En el televisor, enciende **Depuración inalámbrica** (Android 11+) o **Depuración USB + ADB
   por red** (Android 8/9).
4. En el teléfono: **Ajustes → Instalar en TV**, conecta la tele, revisa la limpieza y pulsa
   **Limpiar e instalar**.
5. Si algo no te gusta: **Restaurar** en la misma pantalla.

La firma es la misma de siempre: se instala encima de la versión actual sin perder datos,
cuentas, descargas ni ajustes.

— ChronosBVRX
