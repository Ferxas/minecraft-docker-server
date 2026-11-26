# MapBrowser — fork del servidor

- **Original:** https://github.com/Steve3184/MapBrowser (AGPL-3.0, ver `LICENSE`), basado en el commit `e60dff3`
  ("make createAction using runTaskAsynchronously", después de la 1.0-beta3).
- **Esta versión:** `1.0-beta3-es1`. Todo lo cambiado está en `FORK-CHANGES.patch` (diff contra `e60dff3`), por si hay que
  pasarlo a una versión nueva del original: clona el original, `git checkout <nuevo>` y `git apply --3way FORK-CHANGES.patch`.
- Al ser AGPL, el código de esta versión tiene que seguir publicado (está aquí, en el repo).

## Compilar y desplegar

Desde WSL (Docker no ve `/mnt/c`, el script copia el código a `~/build/mapbrowser`):

```bash
bash plugins-src/MapBrowser/tools/build-in-wsl.sh     # → build/libs/MapBrowser-1.0-beta3-es1-all.jar
bash plugins-src/MapBrowser/tools/deploy-to-stack.sh  # copia al stack y a plugins/update/ (se aplica al reiniciar)
```

`deploy-to-stack.sh` también deja el jar en `plugins/MapBrowser-1.0-beta3-all.jar` del repo (el que usa una instalación
nueva; `mc-init.sh` lo sincroniza siempre): haz commit de ese jar. Se mantiene el nombre del original para que Paper lo
reemplace.

Uso dentro del juego: `plugin-configs/MapBrowser/SETUP.md`.
