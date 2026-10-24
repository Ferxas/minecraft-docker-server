# LegacyWarmup

Precalienta las tablas "legacy" de Paper (`CraftLegacy`) en un hilo aparte mientras arranca el servidor.

## Por qué
QualityArmory llama a `Block#getData()` (API legacy) cuando se dispara un arma. La primera llamada de ese tipo
después de arrancar inicializa `CraftLegacy`, que convierte todos los ids viejos con DataFixerUpper: aquí tardó
más de 60 s en el hilo principal → el watchdog de Paper apagó el servidor en mitad de una partida de SkyWars
(2026-10-24, 04:51).

Con este plugin la conversión ocurre durante el arranque (en el log: `Legacy material support ready in N ms`)
y los disparos ya no congelan nada.

## Compilar
`scripts/build-legacywarmup.sh` (WSL). Deja el jar en `plugins/` del repo y del servidor (se carga al reiniciar).
