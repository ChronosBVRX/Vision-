# Compose ya trae sus consumer rules; aquí solo se fija lo que R8 no puede adivinar.
# El contrato se serializa con org.json (reflexión nativa del framework), así que sus DTOs
# deben conservarse con sus nombres tal cual los lee el launcher.
-keep class com.chronosbvrx.visionplus.home.contract.** { *; }
