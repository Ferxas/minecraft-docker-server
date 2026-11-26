# MapBrowser + MapEngine

In-game Chromium browser rendered on Minecraft maps ([MapBrowser](https://github.com/Steve3184/MapBrowser)).

## What is installed

| Piece | Version | Role |
|---|---|---|
| [MapEngine](https://hangar.papermc.io/Minceraft/MapEngine) | 1.8.12 | Map render API (required) |
| MapBrowser | 1.0-beta3-es1 (fork, `plugins-src/MapBrowser`, rama `servidor-es`) | Browser screens via JCEF |

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

## Fork del servidor (1.0-beta3-es1)

Código: `plugins-src/MapBrowser` (clon de https://github.com/Steve3184/MapBrowser, rama `servidor-es`).
Compilar: `bash plugins-src/MapBrowser/tools/build-in-wsl.sh` desde WSL → `build/libs/MapBrowser-1.0-beta3-es1-all.jar`.
Se despliega con el nombre de siempre (`plugins/MapBrowser-1.0-beta3-all.jar`), vía `server-data/plugins/update/` + reinicio.

Qué cambia respecto al original:
- **Las pantallas se guardan solas** en `plugins/MapBrowser/screens.yml` (incluida la última página) y vuelven tras cada reinicio.
  Ya no hacen falta `mapbrowser-boot.sk` ni `mapbrowser-restore.sh` (borrados). mc-init ya **no** sobrescribe `screens.yml`.
- **Crear sin coordenadas:** mirando a una pared, `/mb crear [web] [ancho] [alto]` (por defecto 3×2, centrada donde miras).
- **Controles en la pantalla** (cualquier jugador, permiso `mapbrowser.use`):
  clic izq. = pulsar · clic der. = bajar · Shift+clic der. = subir · Shift+clic izq. = menú.
  (El original mandaba el clic derecho como botón derecho del ratón, que no hacía nada.)
- **Menú / mando** (`/mb` o Shift+clic izq.): atrás, adelante, recargar, inicio, subir, bajar, favoritos de `config.yml`,
  escribir, teclado en pantalla, Enter, borrar, enlace de la página actual.
- **Escribir desde el chat:** `/mb escribir` → cada mensaje se teclea en la web y pulsa Enter (`+texto` sin Enter, `<` borra,
  `!salir` termina; se apaga solo si te alejas o en 2 min sin escribir).
- `/mb ir youtube.com` o `/mb ir recetas de pizza` (búsqueda en Google). Solo http/https.
- Mensajes en español, alias `/web`, los comandos en inglés siguen funcionando.
- Arreglos: `/mb remove` dejaba entradas colgadas; quien cambiaba de mundo seguía "viendo" la pantalla; no se renderiza si nadie mira.

## Comandos

Para todos (`mapbrowser.use`, por defecto sí): `/mb` (menú de la pantalla cercana), `/mb escribir`, `/mb atras|adelante|recargar|inicio|subir|bajar|teclado`.

Admin (OP o `mapbrowser.command.*`):

| Comando | Qué hace |
|---|---|
| `/mb crear [web] [ancho] [alto]` | Pantalla en la pared que miras |
| `/mb ir <web o búsqueda>` | Cambia la página (`mapbrowser.navigate`) |
| `/mb inicio fijar` | La página actual pasa a ser la de inicio |
| `/mb tamaño <ancho> <alto>` | Redimensiona |
| `/mb mover` | Lleva la pantalla más cercana a la pared que miras |
| `/mb escala <1-4>` | Más = la web se ve más pequeña (cabe más) |
| `/mb quitar [id]` · `/mb lista` · `/mb cerca` | Gestionar |
| `/mb config` | Recarga favoritos/controles de `config.yml` |
| `/mb input/keys/executeJs <id> …` | Los originales (también desde consola) |

Añade `#id` a cualquier comando para elegir otra pantalla: `/mb ir #2 google.com`.
Las pantallas que no se ven bien: espera 5–15 s (primer pintado lento) o `/mb recargar`.
Si el servidor entero se cae al crear: `SIGSEGV` / `libGLX_mesa` → ver Docker notes.

## Permissions (LuckPerms example)

```
/lp group admin permission set mapbrowser.command.* true
/lp group default permission set mapbrowser.use true      # ya es true por defecto
```
