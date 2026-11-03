# Plan aprobado — Descargas portátiles por USB con PIN de 4 dígitos

Estado: **aprobado e implementado en beta1 (2.6.0-beta1), pendiente de pruebas con USB física en dos TVs**.
Fecha: 2026-11-03.
Alcance: app Android de Vision+ (teléfono y Smart TV). No toca webOS, Roku ni PC.

## 1. Decisiones cerradas

1. **Un solo PIN por USB.** Todas las descargas portátiles de esa memoria comparten el mismo PIN.
2. **PIN de 4 dígitos.** Se genera en la TV que descarga y se muestra al terminar la primera descarga portátil.
3. **Solo descargas nuevas.** Las `.vpack` actuales siguen atadas a su TV y no se convierten en esta fase.
4. **La TV de la cabaña es hipotética.** Se probará con una segunda TV/TV box o un equipo de prueba sin red.
5. **Solo versiones beta** hasta que el usuario autorice expresamente la estable. La estable publicada (2.5.245) no se toca.

## 2. Advertencia de seguridad del PIN de 4 dígitos

Un PIN de 4 dígitos tiene 10,000 combinaciones. Si alguien se lleva la USB y tiene la app, puede intentar abrirla por fuerza bruta. No es seguridad fuerte contra un atacante decidido; es una protección de acceso casual.

Medidas que se implementarán:

- Derivación PBKDF2-HMAC-SHA256 con sal aleatoria e iteraciones altas (objetivo: ~600,000; se ajustará según el rendimiento real de la TV).
- Cifrado AES-256-GCM para envolver la llave de la biblioteca y las llaves de cada descarga.
- Bloqueo progresivo tras intentos fallidos: 5 intentos, luego espera de 30 s, 2 min, 10 min y 30 min. El contador se guarda en la app y en la USB; un atacante puede copiar/resetear la USB, así que esto solo frena el intento casual.
- El PIN nunca se guarda en texto plano en la USB. En la TV de origen se guarda cifrado con la llave del chip para poder mostrarlo después.
- El video nunca se guarda descifrado en la USB ni en archivos temporales.
- Sin internet no se puede limitar el número de TVs ni revocar una llave ya importada. El PIN + la USB son la llave.

Si en el futuro se quiere seguridad real, hay que subir a 8–12 caracteres o validar en línea. Eso rompería el caso de la cabaña sin internet.

## 3. Flujo de usuario

### TV A — con internet, descarga a USB

1. El usuario elige una película o capítulo y descarga a la USB, como hoy.
2. La app escribe la descarga en formato portátil `.pvpack` dentro de `vision_vault/portable/`.
3. Al completarse la primera descarga portátil de esa USB, la app genera:
   - una llave de biblioteca aleatoria de 32 bytes;
   - un PIN aleatorio de 4 dígitos;
   - el archivo `library.vkey` con la llave de biblioteca envuelta por el PIN.
4. La app muestra el PIN en pantalla y lo guarda cifrado en su registro para poder consultarlo después.
5. Las siguientes descargas portátiles de la misma USB reutilizan la misma llave de biblioteca y el mismo PIN.
6. En Descargas aparece la acción **Ver PIN** y el estado **Portátil · PIN de esta USB**.

### TV B — sin internet, con la USB

1. El usuario conecta la USB y abre Vision+.
2. La app detecta los volúmenes extraíbles y busca `vision_vault/portable/`.
3. Si hay contenido portátil, muestra la sección **Biblioteca portátil** en Descargas, también en modo sin conexión.
4. Cada título se muestra con póster y metadatos leídos de la USB, sin pedir red.
5. Al abrir uno, la app pide el PIN de 4 dígitos.
6. La app deriva la llave, desenvuelve la llave de biblioteca y comprueba la etiqueta GCM. Si el PIN es incorrecto, falla limpiamente y aplica el bloqueo progresivo.
7. Si es correcto, la TV B guarda la llave de biblioteca cifrada con la llave de su propio chip.
8. El título se reproduce desde la USB con el reproductor offline existente, sin red y con `FLAG_SECURE`.
9. Si se saca la USB, el título desaparece de la lista; al reinsertarla en la misma TV, ya no se pide el PIN.
10. Si se añaden más descargas portátiles a la misma USB, la TV B las abre sin volver a pedir el PIN porque ya tiene la llave de biblioteca.

## 4. Formato y archivos

```
vision_vault/
  portable/
    library.vkey
    <downloadId>.pvpack
    <downloadId>.pvkey
    <downloadId>.pvmeta
    <downloadId>.poster.jpg
    <downloadId>.subs/...
```

- `library.vkey`: versión, `libraryId`, sal, iteraciones, nonce, llave de biblioteca envuelta con el PIN, fecha de creación y estado de bloqueo.
- `.pvpack`: contenedor nuevo, magic `VPPACK02`, versión, BaseIV, tamaño plano, llave de contenido envuelta con la llave del dispositivo de origen y payload AES-256-CTR.
- `.pvkey`: llave de contenido envuelta con la llave de biblioteca; identifica la descarga y permite validar integridad.
- `.pvmeta`: título, subtítulo, póster, duración, calidad, contenedor, tamaño, serie/temporada/capítulo, versión de app y fecha.
- `.poster.jpg`: póster descargado en la TV A para que la TV B lo muestre sin internet.
- `.subs/`: subtítulos descargados, si existen, para que el modo offline sea completo.

## 5. Compatibilidad y límites

- El `.vpack` actual no se modifica. Las descargas viejas siguen reproduciéndose en su TV como hoy.
- Solo las descargas nuevas hechas con la función activa serán portátiles.
- Si el usuario quiere llevar una descarga vieja, debe borrarla y volver a descargarla. La UI lo explicará.
- La TV B necesita la versión de app que entiende `.pvpack`. Si está en la cabaña sin internet, hay que actualizarla antes con el APK por USB o llevarla ya actualizada.
- La USB debe ser almacenamiento extraíble normal (FAT32/exFAT), no almacenamiento interno adoptado.
- Si la TV B monta la USB como solo lectura, se puede reproducir igual; la llave se guarda en la memoria interna de la TV B.
- No hay streaming por LAN en esta fase.
- No hay código en la nube en esta fase.
- No se convierten descargas viejas en esta fase.
- No se limita el número de TVs sin conexión.

## 6. Fases y betas

### Fase 0 — Prueba de concepto (sin cambios visibles)

- Crear un `.pvpack` de prueba con llave por descarga y PIN.
- Validar en una segunda TV sin red: detección de USB, PIN, reproducción, pausa y seek.
- Medir PBKDF2 y AES en TV de gama baja.
- Confirmar que la carpeta de la app en la USB se lee igual en dos TVs.
- Salida: informe go/no-go antes de tocar la app publicada.

### Fase 1 — Escritura portátil en TV A

- Módulo de cifrado portátil separado del `.vpack`.
- Integración con el flujo de descarga actual sin tocar el motor nativo.
- `library.vkey`, `.pvkey`, `.pvmeta`, póster y subtítulos.
- Generación y almacenamiento cifrado del PIN.
- Pantalla “PIN para llevar” y “Ver PIN” en Descargas.
- Pruebas de escritura atómica, USB llena, USB retirada y archivo incompleto.

### Fase 2 — Lectura e importación en TV B

- Escáner de volúmenes extraíbles al abrir, al entrar a Descargas y al conectar USB.
- Sección “Biblioteca portátil” en Descargas y en modo sin conexión.
- Teclado de PIN con D-pad.
- Validación local, bloqueo progresivo e importación de la llave.
- Reproductor para `.pvpack` con seek, pausa y `FLAG_SECURE`.
- Manejo de USB retirada, archivo corrupto, PIN incorrecto y versión incompatible.
- Pruebas con TV A apagada y TV B sin Wi-Fi.

### Fase 3 — Beta y pulido

- Beta 1: flujo mínimo TV A → USB → TV B.
- Beta 2: varias películas, póster/subtítulos, errores y bloqueo.
- Beta 3: rendimiento, compatibilidad de USB y pruebas de regresión.
- Beta N hasta autorización expresa de la estable.

### Fase 4 — Estable (solo con autorización)

- Activar el flag remoto para todos.
- Publicar notas y actualizar AGENTS.md.
- No se publica nada estable antes de la autorización.

## 7. Versionado y publicación beta

- Propuesta: `2.6.0-beta1`, `2.6.0-beta2`, etc., con `versionCode` incremental.
- Se publica solo en el canal/tag beta, nunca en `/releases/latest`.
- La estable 2.5.245 permanece intacta.
- Cada beta incluye: notas de cambios, lista de pruebas ejecutadas, dispositivos usados y problemas conocidos.
- El usuario instala la beta en TV A y en el equipo de prueba (TV B) y reporta resultados.
- No se promueve a estable sin la frase explícita del usuario.

## 8. Pruebas mínimas

- Descarga de una película a USB en TV A y apertura en TV B sin red.
- Varias películas en la misma USB con el mismo PIN.
- Serie: un capítulo descargado y reproducido en TV B.
- PIN correcto, PIN incorrecto, 5 intentos fallidos y bloqueo.
- TV A apagada; TV B en modo avión.
- USB retirada durante la reproducción y reinsertada.
- Reinicio de la app en TV B; no vuelve a pedir PIN en la misma TV.
- Borrado de datos de la app en TV B; vuelve a pedir PIN.
- USB llena, USB desmontada a mitad de descarga y archivo incompleto.
- `.vpack` viejo ignorado por la biblioteca portátil.
- Sin peticiones de red en TV B durante la apertura y reproducción.
- Sin video en claro en la USB ni en temporales.
- Suite Android completa en verde y build release sin errores.

## 9. Criterios de aceptación

- La TV B sin internet detecta la USB y muestra la biblioteca portátil.
- El PIN correcto abre el contenido; el incorrecto no.
- La TV A puede estar apagada.
- La reproducción funciona con pausa, seek y reanudación.
- Al reinsertar la USB en la misma TV B no se pide PIN de nuevo.
- Las descargas y la reproducción actuales no cambian con el flag apagado.
- No hay video en claro ni PIN en texto plano en la USB.
- La beta se publica solo en el canal beta.

## 10. Pendiente inmediato

Antes de escribir código:

1. Crear un checkpoint del estado actual (2.5.245) en una rama o worktree aislado para no mezclar los cambios locales.
2. Ejecutar la Fase 0 con una USB real y dos equipos.
3. Entregar la primera beta solo cuando el flujo mínimo funcione.
