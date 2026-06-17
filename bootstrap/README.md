# Bootstrap (opcional)

En el arranque, el servicio `mc-init` rellena `./server-data` **solo si falta algo**.

## Contenido normal (automático desde el repo)

Montado por `docker-compose.yml`:

- `../plugin-configs` → configs de plugins (solo archivos nuevos)
- `../skript` → scripts Skript (solo si no existen)
- `../plugins` → JARs (solo si no existen)
- `../worlds` → mundos del repo (solo si la carpeta destino no existe)
- `../configs` → plantillas (`server.properties`, etc.)

## Seed completo (otra PC sin `server-data/`)

1. Copia la carpeta **`server-data/`** desde tu backup o export, **o**
2. Coloca aquí **`full-server.tar.gz`** con el contenido de `/data` en la raíz del tar.

El tar de export manual (`mc-server-full-export-*.tar.gz`) trae `minecraft/` y `mysql/`:

```bash
# Extraer solo la parte Minecraft dentro de server-data:
tar -xzf mc-server-full-export-2026-06-07.tar.gz -C server-data --strip-components=1 minecraft
```

Luego: `docker compose up -d`
