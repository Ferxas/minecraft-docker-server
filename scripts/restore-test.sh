#!/bin/bash
# Prueba de restauración (no toca el servidor en marcha). Uso dentro de WSL:
#   bash /mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server/scripts/restore-test.sh
# 1) verifica el sha1 del último backup en C:\Users\Usuario\MinecraftBackups
# 2) lo extrae en ~/servers/minecraft/restore-test y valida mundos y archivos clave
# 3) importa el último volcado MySQL en un contenedor MySQL temporal y cuenta las tablas
set -u
D=/mnt/c/Users/Usuario/MinecraftBackups
T=$(ls -1t $D/mc-backup-*.tar.gz | head -1)
echo "== backup: $(basename "$T")"
[ "$(sha1sum "$T" | awk '{print $1}')" = "$(cat "$T.sha1")" ] && echo "sha1 OK" || echo "SHA1 MISMATCH"

W=~/servers/minecraft/restore-test; mkdir -p $W; cd $W
tar -xzf "$T" 2>/dev/null && echo "extraído en $W"
M=backup/minecraft-data
python3 - "$M" <<'PY'
import sys, gzip, os
M = sys.argv[1]; ok = True
for w in ['SakuraSpawn','world','skywars1','HGPlay','PartyGames','BattleRoyaleLobby','BattleRoyaleMap4','lobbySW','tntrun_void']:
    try:
        d = gzip.open(f'{M}/{w}/level.dat').read()
        n = len(os.listdir(f'{M}/{w}/region')) if os.path.isdir(f'{M}/{w}/region') else 0
        print(f'  {w:18} level.dat OK, {n} region files')
    except Exception as e:
        ok = False; print(f'  {w:18} ERROR {e}')
for f in ['server.properties','plugins/LuckPerms/luckperms-h2-v2.mv.db','plugins/Skywars/arenas/skywars1/blocks.dat','plugins/DeluxeMenus/gui_menus/main_hub.yml']:
    e = os.path.exists(os.path.join(M, f)); ok = ok and e
    print(f'  {f:50} {"OK" if e else "MISSING"}')
print('MUNDOS/ARCHIVOS:', 'OK' if ok else 'FALLO')
PY

echo "== MySQL"
SQL=$(ls -1t $D/mysql-*.sql.gz | head -1)
docker run -d --rm --name mc-restore-test -e MYSQL_ROOT_PASSWORD=test mysql:8.0 >/dev/null
# la imagen arranca un servidor temporal durante la inicialización: esperar al definitivo
for i in $(seq 1 90); do
  docker exec mc-restore-test mysql -uroot -ptest -e "select 1" >/dev/null 2>&1 && sleep 5 \
    && docker exec mc-restore-test mysql -uroot -ptest -e "select 1" >/dev/null 2>&1 && break
  sleep 2
done
zcat "$SQL" | docker exec -i mc-restore-test mysql -uroot -ptest 2>/dev/null && echo "importación de $(basename "$SQL") OK"
docker exec mc-restore-test mysql -uroot -ptest -N -e "SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_schema NOT IN ('mysql','sys','information_schema','performance_schema') GROUP BY table_schema;" 2>/dev/null
docker stop mc-restore-test >/dev/null
echo "Listo. Borra la copia extraída cuando termines (~4 GB): $W"
