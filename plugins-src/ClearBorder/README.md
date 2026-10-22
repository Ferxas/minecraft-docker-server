# ClearBorder

Fixes client world-border desync after leaving worlds with a tight border (e.g. `lainWorld` at 172).

## Cause

[Paper#13876](https://github.com/PaperMC/Paper/issues/13876): when Multiverse teleports on join (`join-destination` → SakuraSpawn), Paper can send the **previous** world's border packets to the client. Server `worldborder get` looks fine; the client still draws lainWorld's wall.

## Fix

Plugin `ClearBorder` calls `Player#setWorldBorder(world.getWorldBorder())` then `null` after join / cross-world teleport / world change, so the client receives the correct `InitializeBorder` for the current world.

Does **not** change lainWorld's 172 border. No hub border nudging.

## Commands

```
/clearborder          # resync self
/clearborder <player> # resync other
```

## Install

JAR: `plugins/ClearBorder.jar` (source: `plugins-src/ClearBorder/`). Loaded on server start.
