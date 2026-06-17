# Con Los Pibes — Minecraft Server

Servidor Paper **1.21.11** con minijuegos (BRX, SkyWars, PartyGames, HungerGames, etc.), Geyser/Floodgate (Xbox/Bedrock), MySQL y backups automáticos.

## Instalación en una PC nueva

Requisitos: [Docker](https://docs.docker.com/get-docker/) (Desktop en Windows, Engine en Linux), [Git](https://git-scm.com/), [GitHub CLI](https://cli.github.com/) (para descargar datos), 7-Zip / p7zip (solo si importas archivos locales).

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

Eso descarga el **release de datos** (~5 GB en partes 7z), extrae `server-data/` + `mysql-data/` y ejecuta `docker compose up -d`.

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
