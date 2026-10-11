# Descripción

<!-- ¿Qué cambia y por qué? -->

## Checklist anti-regresiones

- [ ] `./gradlew :app:testNormalDebugUnitTest` en verde (o CI en verde)
- [ ] Pantallas tocadas verificadas en **móvil** (ficha, reproductor, atrás)
- [ ] Pantallas tocadas verificadas en **TV con D-pad** (foco visible, OK/atrás)
- [ ] Si toqué fútbol: agenda, canales por partido y "Ver en vivo"
- [ ] Si toqué catálogo/búsqueda: Home, Categorías y Búsqueda con y sin red
- [ ] Si toqué el reproductor: VOD + en vivo + zapping + salir (proceso `:x4k` muere)
- [ ] Si es fix de un bug: agregué/ajusté un unit test que lo cubre
- [ ] `AGENTS.md` actualizado si cambió estado, versión o pendientes
