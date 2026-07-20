# Battle Royale X — notas de integración

## Skript (`skript/brx-lobby.sk`)

- `/brxplay`, `/brxleave`, `/brxfix`
- Teleport a `BattleRoyaleLobby` antes de `brx join`
- Al salir con Quit → Confirm (cristal verde), devuelve a `SakuraSpawn`
- Bloquea colocar vidrio verde/rojo de menús BRX en el suelo

## Multiverse-Inventories

Grupo `battleroyalex` en `plugin-configs/Multiverse-Inventories/groups.yml` desactiva inventario/armadura en mundos BRX para que el plugin asigne la hotbar sin que MV la borre.

Tras desplegar: `mvinv reload`

## Multiverse-Core (aliases)

BRX busca mundos por nombre legacy (`BattleRoyaleLobby`, etc.). En Paper 1.21+ los mundos suelen registrarse como `minecraft:battleroyalelobby`. En `worlds.yml` cada mundo BRX debe tener `alias:` y `legacy-world-name:` (ver `server-data` del servidor live).

## Config clave (`config.yml`)

- `Load-Delay-Ticks: 100`
- `Lobby: BattleRoyaleLobby, -167.5, 31, -84.5, 270.2597, -3.6114385`
- Hologramas DecentHolograms comentados si abortan la carga del lobby
