#!/bin/bash
# Minecraft (Con Los Pibes): copia los backups FUERA del disco virtual de WSL, a
# C:\Users\Usuario\MinecraftBackups, y añade un volcado consistente de MySQL.
#  - mundo/plugins: copia el tar.gz diario más reciente de mc-backups/ (si es nuevo y pasa gzip -t)
#  - MySQL: mysqldump --single-transaction de todas las BD (el backup diario copia los ficheros en caliente)
#  - retención POR CANTIDAD (no por antigüedad): aunque el server esté apagado semanas, nunca se queda vacío
# Lo ejecuta cron cada 6 horas. Registro: ~/servers/minecraft/logs/backup-offdisk.log
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
SRC=/home/ferxas/servers/minecraft/stack/mc-backups
DEST=/mnt/c/Users/Usuario/MinecraftBackups
KEEP_WORLD=7      # últimos 7 backups completos (~2.4 GB c/u)
KEEP_SQL=28       # últimos 28 volcados MySQL (7 días a 4 por día)
LOG=/home/ferxas/servers/minecraft/logs/backup-offdisk.log
mkdir -p "$(dirname "$LOG")"
log() { echo "$(date -u '+%Y-%m-%d %H:%M:%S') UTC $*" >> "$LOG"; }
if [ -f "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 524288 ]; then mv -f "$LOG" "$LOG.1"; fi
mkdir -p "$DEST" || { log "ERROR: no puedo crear $DEST"; exit 1; }

# 1) backup completo más reciente
newest=$(ls -1t "$SRC"/mc-backup-*.tar.gz 2>/dev/null | head -1)
if [ -n "$newest" ]; then
  name=$(basename "$newest")
  if [ ! -f "$DEST/$name" ]; then
    if gzip -t "$newest" 2>/dev/null; then
      cp --preserve=timestamps "$newest" "$DEST/$name.part" && mv -f "$DEST/$name.part" "$DEST/$name" \
        && sha1sum "$newest" | awk '{print $1}' > "$DEST/$name.sha1" \
        && log "copiado $name ($(stat -c %s "$newest") bytes)" \
        || { log "ERROR: fallo al copiar $name"; rm -f "$DEST/$name.part"; }
    else
      log "ERROR: $name está corrupto (gzip -t), no se copia"
    fi
  fi
else
  log "AVISO: no hay backups en $SRC"
fi

# 2) volcado consistente de MySQL
if docker ps --format '{{.Names}}' | grep -qx mc-mysql; then
  sqlname="mysql-$(date -u +%Y%m%d-%H%M).sql.gz"
  if docker exec mc-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases --single-transaction --routines --events 2>/dev/null' | gzip > "$DEST/$sqlname.part" \
     && [ "$(zcat "$DEST/$sqlname.part" | tail -1 | grep -c 'Dump completed')" = 1 ]; then
    mv -f "$DEST/$sqlname.part" "$DEST/$sqlname"; log "volcado MySQL $sqlname ($(stat -c %s "$DEST/$sqlname") bytes)"
  else
    rm -f "$DEST/$sqlname.part"; log "ERROR: mysqldump falló"
  fi
fi

# 3) retención por cantidad
ls -1t "$DEST"/mc-backup-*.tar.gz 2>/dev/null | tail -n +$((KEEP_WORLD+1)) | while read -r f; do rm -f "$f" "$f.sha1"; log "retención: borrado $(basename "$f")"; done
ls -1t "$DEST"/mysql-*.sql.gz 2>/dev/null | tail -n +$((KEEP_SQL+1)) | while read -r f; do rm -f "$f"; log "retención: borrado $(basename "$f")"; done
exit 0
