# Con Los Pibes — Minecraft Server

Servidor Paper **1.21.11** con minijuegos (BRX, SkyWars, PartyGames, HungerGames, etc.), Geyser/Floodgate (Xbox/Bedrock), MySQL y backups automáticos.

## Instalación en una PC nueva

Requisitos: [Docker](https://docs.docker.com/get-docker/) (Desktop en Windows, Engine en Linux), **Docker Compose** (`docker compose` o `docker-compose`), [Git](https://git-scm.com/), **curl** (Linux) / PowerShell (Windows), **7-Zip / p7zip** para extraer el release de datos. GitHub CLI es opcional.

En Kali/Debian, si falta Compose:
```bash
sudo apt update && sudo apt install docker-compose-plugin
# o: sudo apt install docker-compose
```

**Windows (PowerShell):**

```powershell
git clone https://github.com/Ferxas/minecraft-docker-server.git
cd minecraft-docker-server
.\install.ps1
```

**Linux (bash):**

```bash
git clone https://github.com/Ferxas/minecraft-docker-server.git
cd minecraft-docker-server
chmod +x install.sh
./install.sh
```

Eso descarga el **release de datos** (~3.7 GB comprimido / ~5 GB extraído, en partes 7z) desde [GitHub Releases](https://github.com/Ferxas/minecraft-docker-server/releases/tag/server-data-v1) con `curl`/`Invoke-WebRequest` (o `gh` si está instalado), extrae `server-data/` + `mysql-data/` y ejecuta `docker compose up -d`.

En Ubuntu/WSL, si falta el extractor:
```bash
sudo apt update && sudo apt install -y p7zip-full
```

### Alternativas

```powershell
# Windows — ya copiaste server-data/ y mysql-data/ manualmente
.\install.ps1 -SkipDataDownload

# Windows — desde un backup 7z local
.\install.ps1 -ImportArchive "D:\backup\server-data-v1.7z.001"
```

```bash
# Linux — datos ya presentes
./install.sh --skip-data-download

# Linux — desde un backup 7z local
./install.sh --import-archive /path/to/server-data-v1.7z.001
```

### Instalación fresh (plugins + mapas, sin datos viejos)

Descarga el release pero **borra** MySQL, permisos LuckPerms, cuentas Discord, userdata de Essentials y progreso de jugadores en mundos. **Conserva** plugins, configs y mapas/arenas construidos.

```powershell
.\install.ps1 -Fresh
```

```bash
./install.sh --fresh
```

Si ya extrajiste los datos y solo quieres limpiarlos:

```powershell
.\install.ps1 -Fresh -SkipDataDownload
```

```bash
./install.sh --fresh --skip-data-download
```

También puedes ejecutar solo la limpieza:

```bash
./scripts/strip-server-runtime-data.sh .
```

## Puertos

| Puerto | Uso |
|--------|-----|
| 25565 | Java |
| 19132/UDP | Bedrock (Geyser) |
| 3306 | MySQL |

## Estructura

| Carpeta | Descripción |
|---------|-------------|
| `server-data/` | Servidor completo (`/data`) — **no está en git** |
| `mysql-data/` | Base MySQL — **no está en git** |
| `plugin-configs/` | Configs de referencia (bootstrap idempotente) |
| `skript/` | Scripts Skript |
| `scripts/` | Utilidades (export, publish, package) |
| `plugins/` | Jars propios que `mc-init` copia al servidor (ClearBorder, LegacyWarmup, WelcomeAdsBridge, MapBrowser) |
| `plugins-src/` | Código de esos plugins propios |
| `docker/mc-jcef/` | Imagen de Paper con Chromium (para las pantallas web de MapBrowser) |

## Pantallas web (MapBrowser)

El servidor usa un **fork propio de MapBrowser** (`plugins-src/MapBrowser`, versión `1.0-beta3-es1`):
las pantallas se guardan solas y vuelven tras reiniciar, se crean mirando a una pared (`/mb crear youtube.com`)
y cualquier jugador las usa con el ratón (clic izq. pulsar, clic der. bajar, Shift+der. subir, Shift+izq. menú)
o escribiendo en el chat con `/mb escribir`. El jar ya compilado está en `plugins/MapBrowser-1.0-beta3-all.jar`
y `mc-init` lo instala siempre, así que una instalación nueva no necesita compilar nada.

- Uso y comandos: [`plugin-configs/MapBrowser/SETUP.md`](plugin-configs/MapBrowser/SETUP.md)
- Compilar / actualizar el fork: [`plugins-src/MapBrowser/UPSTREAM.md`](plugins-src/MapBrowser/UPSTREAM.md)

## Mantenimiento

```powershell
docker compose up -d          # arrancar
docker compose down           # parar
docker logs mc -f             # logs
.\scripts\docker_mc_publish.ps1   # sincronizar configs nuevos del repo (no sobrescribe)
```

## Publicar datos actualizados (maintainer)

```powershell
.\scripts\export-volume-to-server-data.ps1   # migrar desde volumen Docker
.\scripts\package-server-data.ps1            # crear 7z en bootstrap/release/
gh release create server-data-v2 bootstrap/release/server-data-v1.7z.* --repo Ferxas/minecraft-docker-server --title "Server data v2"
```

Actualiza `$ReleaseTag` en `install.ps1` / `--release-tag` en `install.sh` al publicar una versión nueva.
