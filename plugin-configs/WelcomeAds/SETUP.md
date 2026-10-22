# WelcomeAds v2.1 (Oraxen stack)

Pantalla de bienvenida ([MCModels / Tinysx](https://mcmodels.net/products/17000/welcomeads)).

## Cómo funciona (3 backends)

WelcomeAds trae **tres carpetas** en la compra / JAR. Solo usas **una** según tu plugin de pack:

| Carpeta vendor | Plugin | Placeholders en `inventory.yml` |
|----------------|--------|----------------------------------|
| `Oraxen/` | **Oraxen** (este server) | `%oraxen_welcomeads_page_1%`, `%oraxen_welcomeads_background%`, … |
| `ItemsAdder/` | ItemsAdder | `%img_welcomeads_page_1%`, `%img_welcomeads_background%`, … |
| `NexO/` | NexO | glyphs NexO + mismas texturas bajo su pack |

En el repo todo queda archivado en `plugin-configs/WelcomeAds/packs/` (copia de las carpetas de Downloads). **En producción solo Oraxen:**

| Qué | Repo → live |
|-----|-------------|
| Glyphs | `plugin-configs/Oraxen/glyphs/welcomeads/welcomeads.yml` → `plugins/Oraxen/glyphs/...` |
| Texturas | `plugin-configs/Oraxen/pack/textures/welcomeads/*.png` → `plugins/Oraxen/pack/textures/...` |
| WelcomeAds YAML | `plugin-configs/WelcomeAds/*.yml` → `plugins/WelcomeAds/` |
| Packs vendor | `plugin-configs/WelcomeAds/packs/**` → `plugins/WelcomeAds/packs/**` (evita errores en `/welcomeads reload`) |

Re-sync desde tus ZIP de Downloads:

```bash
python scripts/sync-welcomeads-vendor-oraxen.py
```

## Texturas (`shift.png` / `background.png`)

- `welcome_page_1/2/3.png` → **256×256**, arte visible en el título del inventario.
- `shift.png` y `background.png` → **1×1 px** en el pack oficial (espaciadores Oraxen). Ver `plugin-configs/Oraxen/pack/textures/welcomeads/README.md`.

## Permisos (LuckPerms)

```
/lp group default permission set welcomeads.font true
/lp group default permission set welcomeads.use true
/lp group default permission set welcomeads.open true
```

## Navegación entre páginas (clics)

El YAML vendor usa `[console]welcomeads open welcome-2 <player>`; aquí los clics usan **Skript** `/welcomeadspage` (`skript/welcomeads-nav.sk`) → `[player]welcomeadspage welcome-2`.

## Comandos

```
/welcomeads open welcome-1 <jugador>
/welcomeadspage welcome-2    # jugador, vía Skript
/welcomeads reload
/oraxen reload all
```

## Despliegue live (WSL)

```bash
bash scripts/deploy-welcomeads.sh
```

Tras cambiar PNG/glyphs: reconectar al server para aplicar el pack de Oraxen.

## Si sale el cofre normal con símbolos raros en el título

Significa que el cliente **no tiene** el pack de Oraxen aplicado (el título cae a Unifont). La consola
muestra `[PackStatus] <jugador> <estado>` (`skript/oraxen-pack-return.sk`):

| Estado | Causa conocida | Arreglo |
|--------|----------------|---------|
| ninguno | PackManagerPro con `pack-url: ''` en el mundo → `setResourcePack` vacío borra el pack de Oraxen | Solo mundos con URL real en `PackManagerPro/config.yml` |
| `failed reload` | Falta alguna textura de glyph en `minecraft:default` → cliente: "Default font failed to load" y descarta el pack | `python scripts/fetch-oraxen-required-textures.py` + `bash scripts/deploy-pack-delivery.sh` |
| `successfully loaded` | Pack OK | `/welcomeadspage welcome-1` o `/welcomeadsopen welcome-1 <jugador>` |

**Reabrir el menú:** no uses `/welcomeads open …` a mano. WelcomeAds devuelve la hotbar 1 tick después de cerrar; si abres otra vez al instante, ves el cofre feo otra vez. Usa **`/welcomeadspage welcome-1`** (jugador) o **`/welcomeadsopen welcome-1 ferxas`** (op) — el plugin **WelcomeAdsBridge** espera unos ticks y limpia el estado antes de abrir.

Log del cliente (Fabric): `%APPDATA%\.minecraft\instances\mc-fabric-1.21.11\logs\latest.log`, buscar
`Failed to load builder` / `Default font failed to load`. Inspección offline del pack generado:
`python3 scripts/_inspect-oraxen-pack.py /tmp/oxp/pack.zip` (tras `docker cp mc:/data/plugins/Oraxen/pack/pack.zip /tmp/oxp/`).
