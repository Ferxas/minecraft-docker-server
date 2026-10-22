# Resource packs — notas (1.21.11)

## Qué causaba `Resource reload failed` al entrar

1. **Oraxen** forzaba su pack en cada join (`send_on_join` + `mandatory`). Ese ZIP tenía:
   - modelos rotos (`weed_leaf` → textura `default/weed/leaf` inexistente)
   - **core shaders** (`rendertype_text*`) incompatibles con 1.21.11
2. **QualityArmory** también auto-enviaba otro pack → pelea de packs.
3. El pack base de **PackManagerPro** en mc-packs (`f2b7ea…`) devolvió **404** (expiró).

## Qué quedó aplicado

- Oraxen: `send_on_join/reload=false`, `mandatory=false`, `protection=false`
- QualityArmory: `useDefaultResourcepack=false`, `Auto-Detect-Resourcepack=false`
- PackManagerPro: URLs `f2b7ea` vacías + `force-pack: false`; Bunker/KROTHOLE siguen en GitHub

## Pendiente (texturas Ferxas en hub)

Re-subir el ZIP base del servidor a mc-packs o GitHub Releases y poner la URL en `PackManagerPro` para `SakuraSpawn` / lobbies (`force-pack: true` si querés obligarlo).
