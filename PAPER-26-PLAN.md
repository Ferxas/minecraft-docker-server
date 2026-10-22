# Plan de migración a Paper 26.x

Estado (2026-10-22): el servidor corre **Paper 1.21.11** sobre Java 25 (`mc-paper-jcef:1.21.11`).
Paper publica 26.1.x, 26.2 y 26.3 (`https://fill.papermc.io/v3/projects/paper`). El aviso de consola
"Paper 26.x disponible" es solo informativo: **no conviene actualizar todavía** sin hacer esto antes.

La actualización de mundos es **de un solo sentido**: un mundo abierto en 26.x ya no abre en 1.21.11.

## 1. Riesgo por plugin

**Bloqueantes: hay que bajar la build de 26.x (las actuales llevan "1.21.11" en el nombre)**
- `WIT-1.9.1-mc.1.21.11`
- `emotecraft-paper-for-MC1.21.11`
- `feathermorph-2.8.1-1.21.11`
- `mclogs-bukkit-1.21.11`

**Usan NMS o paquetes: necesitan versión que declare soporte 26.x**
- LibsDisguises
- QualityArmory + QualityArmoryVehicles
- Oraxen
- FancyNpcs
- GrimAC + packetevents
- ProtocolLib
- ImageFrame
- MapEngine / MapBrowser
- InteractiveChat (+ addon DiscordSRV)
- Geyser / Floodgate
- ViaVersion / ViaBackwards / ViaRewind
- SkinsRestorer
- CommandAPI
- Skript
- DeluxeMenus
- MythicMobs
- BlueMap
- Plan
- spark
- TAB
- WorldGuard / WorldGuardExtraFlagsPlus
- FAWE
- Multiverse

**Parcheados por bytecode aquí: al actualizarlos se pierde el parche, hay que re-aplicarlo o confirmar que ya no hace falta**

| Jar | Parche |
|---|---|
| BattleRoyaleX | tablas `BRLeaderboard`/`BRStats` |
| PartyGames | `PGFix` (NPE en empate a 0) |
| Hungergames | `SpectateGuiListener`, `DisplayStatsHandler` |
| Replay (AdvancedReplay) | índice PlayerInfo |
| xPinataParty | `setInvulnerable(false)` en `PinataManager` (si es `true`, Paper descarta los golpes antes del evento y la piñata no recibe hits) |

Originales y parcheados: `~/servers/minecraft/claude-restart-20261022/old-jars/`.

**Plugins viejos o poco mantenidos: probar a mano (pueden funcionar solo con API Bukkit)**
- Skywars 10.4
- TNTRun_reloaded
- CSCinematic
- ChatBubble
- LagMap
- SignManager
- VoidWorld
- WelcomeAds 2.1 + WelcomeAdsBridge
- ClearBorder (código propio, en `plugins-src/`)
- ExtraGreetings
- GMusic / NoteBlockAPI
- AutoBackup
- PackManagerPro

## 2. Procedimiento
1. **Respaldo completo:** el respaldo de mundos de `offen/docker-volume-backup` más `scripts/backup-offdisk.sh`. Comprueba que se puede restaurar con `scripts/restore-test.sh`.
2. **Copia de prueba:** clona `stack/` en otra carpeta, con otro nombre de proyecto compose y otro puerto (p. ej. 25566), restaurando desde ese backup. Nunca pruebes sobre el stack vivo.
3. **Imagen:** en la copia, pon `VERSION: "26.x"` y reconstruye la imagen `mc-paper-jcef` con la nueva etiqueta.
4. **Primer arranque:** sustituye los jars bloqueantes y arranca. Desactiva en `plugins/_disabled_jars/` todo lo que falle al cargar.
5. **Revisa la consola:**
   ```bash
   docker logs <mc> | grep -E 'WARN|ERROR'
   ```
6. **Pruebas con bots:** ejecuta `tests/bot/` en la copia:
   - `crawl.js` con todos los menús;
   - `flow.js` con una partida de cada minijuego (SkyWars en los 4 mapas, HG en los 3, Party Games, TNTRun, BRX) y la piñata;
   - `bedrock.js` para Geyser.
7. **Gamerules:** compruébalas en cada mundo de minijuegos. Los nombres en snake_case (`spawn_mobs`, `advance_time`…) ya son los de 1.21.11, pero hay que confirmar que siguen igual.
8. **Paso a producción:** solo cuando todo lo anterior quede limpio. Copia los jars y la configuración a `plugins/` y `plugin-configs/` del repo y del stack, cambia `VERSION` en el compose vivo y reinicia.

## 3. Criterio para hacerlo
Espera a que **todos** los de la lista de NMS publiquen build estable para la misma 26.x. Los parcheados y los
viejos se validan en la copia. Si alguno imprescindible (SkyWars, BRX, PartyGames, HG) no funciona en 26.x,
quédate en 1.21.11 y mantén ViaVersion actualizado para que entren clientes más nuevos.
