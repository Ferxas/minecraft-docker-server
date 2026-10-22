# Bots de prueba

Bots que entran al servidor como jugadores reales (modo offline) para probar menús, minijuegos y flujos.
Requisitos: Node 18+ en Windows, servidor corriendo en WSL (`docker exec mc rcon-cli` disponible vía `wsl.exe`).

```bash
npm install
```

## flow.js — secuencia de pasos
`node flow.js <NombreBot> "<paso>; <paso>; ..."`

Pasos: `cmd /comando` · `click <slot> [right]` · `wait <ms>` · `where` · `hotbar` · `inv` · `use <slot|item>` ·
`close` · `rcon <comando de consola>` · `goto x y z [r]` · `chest [radio]` · `signs [radio]` · `clicksign x y z` ·
`attack <jugador>` · `hitmob <tipo> <golpes>` · `npc x z` · `maps` · `sneak` · `jump`

Ejemplo (partida de SkyWars en Lighthouse desde el menú, 2 bots):
```bash
node flow.js SwBot1 "cmd /menu; click 19; click 13; click 15; click 12; wait 50000; where; chest 30" &
node flow.js SwBot2 "cmd /menu; click 19; click 13; click 15; click 12; wait 50000; where"
```
La primera vez cada bot pasa las páginas de bienvenida (WelcomeAds) solo.

## crawl.js — pulsa TODOS los botones de todos los menús
1. Exporta los menús a JSON (desde `plugin-configs/DeluxeMenus/gui_menus`, con PyYAML):
   ```bash
   python -c "import yaml,glob,json,os; json.dump({os.path.basename(f)[:-4]: {'items': [{'key':k,'slot':(it.get('slot') if 'slot' in it else (it.get('slots') or [None])[0]),'name':it.get('display_name'),'cmds':it.get('click_commands') or []} for k,it in (yaml.safe_load(open(f,encoding='utf8')).get('items') or {}).items() if it.get('click_commands')]} for f in glob.glob('../../plugin-configs/DeluxeMenus/gui_menus/*.yml')}, open('menus.json','w',encoding='utf8'), ensure_ascii=False)"
   ```
2. `node crawl.js` (o `node crawl.js "skywars_"` para filtrar). Usa un jugador normal (sin OP) y reconecta
   en cada botón; informa chat, comandos ejecutados, errores del servidor, ventana abierta y mundo final.

## bedrock.js — cliente Bedrock vía Geyser
Arranca un Geyser Standalone de prueba con `validate-bedrock-login: false` en otro puerto (p. ej. 19133)
y ejecuta `node bedrock.js 19133 BedTester`. No cambies esa opción en el Geyser real.

Los bots deberían tener `grim.exempt` (los bots no envían el paquete "tick end" y Grim los marca).
## Limpiar los datos de los bots después de probar
UUID offline de un bot: `md5("OfflinePlayer:<nombre>")` con versión 3. Los bots Bedrock de prueba usan
`00000000-0000-0000-0000-000000000000`; los `00000000-0000-0000-0009-…` son jugadores Bedrock reales, no los toques.

1. **Con el servidor encendido:**
   - por cada bot: `lp user <uuid> clear`;
   - borra sus filas de la BD de Hunger Games:
     ```sql
     DELETE FROM hungergames.player_stats WHERE uuid IN (...);
     DELETE FROM hungergames.player_monthly_playtime WHERE uuid IN (...);
     ```
2. **Con el servidor parado** (`docker stop mc`), en `server-data`:
   - borra los archivos con el uuid o el nombre del bot en el nombre: `*/playerdata`, `*/stats`, `*/advancements`,
     `plugins/{Essentials/userdata, Multiverse-Inventories, MythicMobs/data/players, ImageFrame/players,
     FeatherMorph/playerdata, FeatherMorph/skins, SkinsRestorer, WIT/cache/players, Skywars/players,
     BattleRoyaleX/players, PartyGames/players}`;
   - quita sus entradas de:
     - `usercache.json`
     - `plugins/Multiverse-Inventories/playernames.json`
     - `plugins/AdvancedServerList/playercache.json`
     - `plugins/ExtraGreetings/playerdata.yml`
     - `plugins/TNTRun_reloaded/{players,stats}.yml`
3. Guarda antes un `tar` de todo lo que vayas a borrar.
