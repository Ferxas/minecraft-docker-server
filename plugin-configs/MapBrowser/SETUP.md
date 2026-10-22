# MapBrowser + MapEngine

In-game Chromium browser rendered on Minecraft maps ([MapBrowser](https://github.com/Steve3184/MapBrowser)).

## Whatâ€™s installed

| Piece | Version | Role |
|---|---|---|
| [MapEngine](https://hangar.papermc.io/Minceraft/MapEngine) | 1.8.12 | Map render API (required) |
| MapBrowser | 1.0-beta3 | Browser screens via JCEF |

JARs live in repo `plugins/`:
- `MapEngine-1.8.12-all.jar`
- `MapBrowser-1.0-beta3-all.jar`

Config seed: `plugin-configs/MapBrowser/config.yml` (Docker-friendly CEF flags).

## Docker notes

- Image: `mc-paper-jcef`
- Headless Docker: starts **Xvfb :99** + Mesa software GL (JogAmp needs a display even for OSR). (`docker/mc-jcef/Dockerfile`) = itzg Paper + Chromium/NSS/ALSA libs for JCEF.
- `shm_size: 1gb` and `LD_LIBRARY_PATH` for `libjawt.so`.
- JogAmp under Mesa+Xvfb/Xdummy: `glXQueryDrawable` is a NULL dri2 hook → JVM SIGSEGV on `/mb create` (not a corrupt world, not MC version, not “must look at a wall”).
- Fix: `libglx_query_shim.so` via `LD_PRELOAD` intercepts `dlsym("glXGetProcAddress")` and returns a safe stub for `glXQueryDrawable`; plus Xorg dummy display.
- `ulimits.core: 0` so a native crash cannot dump multi-GB cores into `server-data/`.
- First boot downloads a **JCEF bundle** into `plugins/MapBrowser/jcef-bundle/` (already present after first attempt).
- CEF flags: `--no-sandbox`, swiftshader GL, `--disable-dev-shm-usage`.
- Supported CEF hosts: Linux amd64/arm64, Windows amd64/i386 ([MapBrowser](https://github.com/Steve3184/MapBrowser)).

## Commands (in-game, OP / luckperms)

| Command | What |
|---|---|
| `/mb create <x> <y> <z> <url> [width] [height]` | Create screen at block coords (width/height = maps, default often 1x1) |
| `/mb list` | Active screens |
| `/mb near` | Nearest screen id |
| `/mb remove <id>` | Remove screen |
| `/mb modify <id> url <url>` | Change page |
| `/mb modify <id> refresh` | Reload |
| `/mb modify <id> size <w> <h>` | Resize |
| `/mb modify <id> scale <n>` | Scale |
| `/mb input <id> <text>` | Type into focused field |
| `/mb keys <id> <key> <pressDown\|pressUp\|click>` | Key events |

Permission root: `mapbrowser.command.*` (create/list/remove/modify/input/keys/executejs/near).

## Quick test (in-game)

Server must show MapBrowser enabled (`MapBrowser has been enabled!` / `JCEF Initialized`).

**Important:** `/mb create` does **not** open a Chrome window. It spawns a **wall of maps** (MapEngine client-side item frames) in the world. Displays are **in-memory only** — a container recreate wipes them.

**Console / RCON:** MapBrowser rejects `mb create` from the console (`This command can only be used by a player.`). Use **`skript/mapbrowser-boot.sk`** (auto-restore when someone with `mapbrowser.command.create` is online) or:

```bash
# With at least one player online (first name from /list):
execute in SakuraSpawn as <player> run mb create 117 -49 -53 https://www.google.com 2 2
```

Or:

```bash
bash scripts/mapbrowser-restore.sh   # picks first online player via RCON
```

After every **`docker restart mc`**, recreate screens:

```bash
bash scripts/mapbrowser-restore.sh
```

Edit coords/URLs in `plugin-configs/MapBrowser/screens.yml` (defaults: SakuraSpawn walls at `117 -49 -53` and `70 -57 -88`, 2×2). JCEF still loads on boot — only the **map walls** disappear.

1. Join **Java** as OP. Prefer Java over Bedrock/Geyser for map walls.
2. Stand **facing a flat wall** (or open air). The screen faces **toward you** (opposite of your look).
3. Aim at the wall, use tab-complete for coords, keep size small (`2 2` — `10 10` is very heavy on software GL):
   ```
   /mb create <x> <y> <z> https://example.com 2 2
   ```
4. You should see filled item-frame maps on that face. Click to interact.
5. Useful:
   ```
   /mb list
   /mb near
   /mb modify <id> refresh
   /mb remove <id>
   ```

If chat says success but you see **nothing**: wrong face (walk around the block), wrong coords/world, or displays wiped by a restart — create again.

If you see **black maps**: wait 5–15s (first paint is slow), then `/mb modify <id> refresh`.

If the **whole server dies** on create: `SIGSEGV` / `libGLX_mesa` → JogAmp/Xvfb path; tell P.

## Permissions (LuckPerms example)

```
/lp group admin permission set mapbrowser.command.* true
```

