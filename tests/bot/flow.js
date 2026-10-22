// Generic flow runner: node flow.js <botName> "<step>;<step>;..."
// steps: cmd /x | click N [right] | wait ms | where | hotbar | inv | use <hotbarSlot|itemName> | close | menu | quit
const L = require('./lib');
(async () => {
  const [name, stepStr, rp] = [process.argv[2], process.argv[3] || '', process.argv[4]];
  const bot = L.createBot(name, { quiet: false });
  await bot.ready;
  // Record map packets (filled_map rendering) for the 'maps' step.
  bot.maps = {}; bot._hurtSeen = 0; bot.on('entityHurt', (en) => { if (en !== bot.entity) bot._hurtSeen++; });
  bot._client.on('map', (pk) => {
    const id = pk.itemDamage ?? pk.mapId;
    const m = bot.maps[id] || (bot.maps[id] = { pixels: new Uint8Array(128 * 128), updates: 0, icons: 0 });
    m.updates++; m.icons = (pk.icons || []).length;
    if (pk.columns > 0 && pk.data) for (let i = 0; i < pk.columns * pk.rows; i++) {
      const x = pk.x + (i % pk.columns), y = pk.y + Math.floor(i / pk.columns);
      m.pixels[y * 128 + x] = pk.data[i];
    }
  });
  await L.sleep(3000);
  // Click through the WelcomeAds pages like a player would (page 1 is forced).
  for (let i = 0; i < 6 && bot.currentWindow; i++) {
    const w = bot.currentWindow;
    const items = L.windowItems(w);
    bot.log('WELCOME page', i + 1, items.join(' | '));
    const slot = [50, 49, 48].find((s) => w.slots[s]);
    if (slot == null) { bot.closeWindow(w); break; }
    await bot.clickWindow(slot, 0, 0).catch(() => {});
    await L.sleep(1800);
  }
  if (bot.currentWindow) bot.log('!! window still open after welcome:', L.textOf(bot.currentWindow.title));
  await L.sleep(3500);
  const showWin = (w) => bot.log('  WIN', L.textOf(w.title), '=>', L.windowItems(w).filter((x) => !/ ""$/.test(x)).join(' | '));
  for (const raw of stepStr.split(';').map((s) => s.trim()).filter(Boolean)) {
    const [op, ...rest] = raw.split(' '); const arg = rest.join(' ');
    bot.log('>>', raw);
    if (op === 'cmd') { const wp = L.waitWindow(bot, 2500); bot.chat(arg); const w = await wp; if (w) showWin(w); }
    else if (op === 'click') {
      if (!bot.currentWindow) { bot.log('  !! no window open'); continue; }
      const [slot, b] = arg.split(' ');
      const wp = L.waitWindow(bot, 2500);
      await bot.clickWindow(+slot, b === 'right' ? 1 : 0, 0).catch((e) => bot.log('  click err', e.message));
      const w = await wp; if (w) showWin(w);
    }
    else if (op === 'wait') await L.sleep(+arg);
    else if (op === 'where') bot.log('  WHERE', L.whereIs(name));
    else if (op === 'hotbar') bot.log('  HOTBAR', L.hotbar(bot));
    else if (op === 'inv') bot.log('  INV', L.inventoryList(bot));
    else if (op === 'use') {
      let slot = +arg;
      if (isNaN(slot)) slot = bot.inventory.slots.slice(36, 45).findIndex((it) => it && (it.name === arg || L.itemName(it).includes(arg)));
      if (slot < 0) { bot.log('  !! item not in hotbar:', arg); continue; }
      const wp = L.waitWindow(bot, 2500); await L.useHotbar(bot, slot, 100); const w = await wp; if (w) showWin(w);
    }
    else if (op === 'close') { if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await L.sleep(500); }
    else if (op === 'rcon') bot.log('  RCON', L.rcon(arg).slice(0, 400));
    else if (op === 'look') { const [y, p] = arg.split(' ').map(Number); await bot.look(y, p, true); }
    else if (op === 'jump') { bot.setControlState('jump', true); await L.sleep(400); bot.setControlState('jump', false); }
    else if (op === 'sneak') { bot.setControlState('sneak', true); await L.sleep(600); bot.setControlState('sneak', false); }
    else if (op === 'chest') {
      const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');
      if (!bot.pathfinder) bot.loadPlugin(pathfinder);
      const ids = ['chest', 'trapped_chest', 'barrel'].map((n) => bot.registry.blocksByName[n]?.id).filter(Boolean);
      const blks = bot.findBlocks({ matching: ids, maxDistance: +(arg || 24), count: 4 }).map((p) => bot.blockAt(p));
      const blk = blks[0];
      if (blks.length > 1) bot.log('  chests found:', blks.map((b) => b.position.toString()).join(' '));
      if (!blk) { bot.log('  !! no chest nearby'); continue; }
      bot.log('  chest at', blk.position.toString(), 'dist', blk.position.distanceTo(bot.entity.position).toFixed(1));
      try {
        const m = new Movements(bot); m.canDig = false; m.allow1by1towers = false; bot.pathfinder.setMovements(m);
        await Promise.race([bot.pathfinder.goto(new goals.GoalNear(blk.position.x, blk.position.y, blk.position.z, 2)), L.sleep(20000)]);
        bot.log('  reached, dist', blk.position.distanceTo(bot.entity.position).toFixed(1));
        await bot.lookAt(blk.position.offset(0.5, 0.5, 0.5));
        await bot.activateBlock(blk).catch((e) => bot.log('  activate err', e.message));
        await L.sleep(1500);
        if (bot.currentWindow) { bot.log('  CHEST', L.windowItems(bot.currentWindow).join(' | ') || '(empty)'); bot.closeWindow(bot.currentWindow); }
        const drops = Object.values(bot.entities).filter((e) => e.name === 'item' && e.position.distanceTo(blk.position) < 4);
        const near = Object.values(bot.entities).filter((e) => e !== bot.entity && e.position.distanceTo(blk.position) < 5);
        bot.log('  NEAR', near.map((e) => e.name + (e.equipment?.filter(Boolean).length ? '[' + e.equipment.filter(Boolean).map(L.itemName).join(',') + ']' : '') + (e.metadata ? '{' + e.metadata.map((m, i) => (m && typeof m === 'object' && m.itemId !== undefined) ? i + ':' + (bot.registry.items[m.itemId]?.name || m.itemId) : '').filter(Boolean).join(',') + '}' : '')).join(' | ') || '(none)');
        bot.log('  DROPS', drops.map((e) => { try { return L.itemName(e.getDroppedItem()); } catch { return '?'; } }).join(' | ') || '(none)');
      } catch (e) { bot.log('  chest err', e.message); }
    }
    else if (op === 'maps') {
      const held = bot.heldItem; bot.log('  held', L.itemName(held));
      for (const [id, m] of Object.entries(bot.maps)) {
        let nz = 0; const uniq = new Set(); for (const v of m.pixels) { if (v) nz++; uniq.add(v); }
        bot.log(`  MAP #${id} updates=${m.updates} icons=${m.icons} filled=${(nz / 163.84).toFixed(1)}% colors=${uniq.size}`);
        if (arg) require('fs').writeFileSync(`map_${id}.bin`, Buffer.from(m.pixels));
      }
      if (!Object.keys(bot.maps).length) bot.log('  MAP: no map packets received');
    }
    else if (op === 'signs') {
      const ids = Object.values(bot.registry.blocksByName).filter((b) => /sign$/.test(b.name)).map((b) => b.id);
      const ps = bot.findBlocks({ matching: ids, maxDistance: +(arg || 8), count: 30 });
      for (const p of ps) { const b = bot.blockAt(p); let t = ''; try { t = JSON.stringify(b.blockEntity?.front_text?.messages ?? b.blockEntity ?? null).replace(/\\"/g,''); } catch {} bot.log('  SIGN', p.toString(), t.slice(0, 120)); }
    }
    else if (op === 'clicksign') {
      const [x, y, z] = arg.split(' ').map(Number); const b = bot.blockAt(new (require('vec3'))(x, y, z));
      if (!b) { bot.log('  !! no block'); continue; }
      await bot.lookAt(b.position.offset(0.5, 0.5, 0.5)); const wp = L.waitWindow(bot, 2500);
      await bot.activateBlock(b).catch((e) => bot.log('  sign err', e.message)); const w = await wp; if (w) showWin(w);
    }
    else if (op === 'goto') {
      const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');
      if (!bot.pathfinder) bot.loadPlugin(pathfinder);
      const [x, y, z, r] = arg.split(' ').map(Number);
      const m = new Movements(bot); m.canDig = false; bot.pathfinder.setMovements(m);
      await Promise.race([bot.pathfinder.goto(new goals.GoalNear(x, y, z, r || 2)).catch((e) => bot.log('  goto err', e.message)), L.sleep(25000)]);
      bot.log('  now at', L.posStr(bot));
    }
    else if (op === 'attack') {
      const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');
      if (!bot.pathfinder) bot.loadPlugin(pathfinder);
      const target = bot.players[arg]?.entity;
      if (!target) { bot.log('  !! target not visible', arg); continue; }
      const m = new Movements(bot); m.canDig = false; bot.pathfinder.setMovements(m);
      bot.log('  target dist', target.position.distanceTo(bot.entity.position).toFixed(1));
      await Promise.race([bot.pathfinder.goto(new goals.GoalFollow(target, 1)).catch(() => {}), L.sleep(20000)]);
      bot.pathfinder.stop();
      bot.log('  before', L.rcon(`data get entity ${arg} Health`));
      let hurts = 0; const h = (e) => { if (e === target) hurts++; }; bot.on('entityHurt', h);
      const sw = bot.inventory.items().find((i) => /sword|axe/.test(i.name)); if (sw) await bot.equip(sw, 'hand');
      for (let i = 0; i < 6; i++) { await bot.lookAt(target.position.offset(0, 1.5, 0)); bot.attack(target); await L.sleep(700); }
      bot.removeListener('entityHurt', h); bot.log('  HURT events on target:', hurts, 'weapon:', sw?.name);
      bot.log('  after', L.rcon(`data get entity ${arg} Health`));
    }
    else if (op === 'npc') {
      const [x, z] = arg.split(' ').map(Number);
      const e = Object.values(bot.entities).filter((en) => en !== bot.entity && (en.name === 'player' || en.type === 'passive' || en.type === 'mob')).sort((a, b) => Math.hypot(a.position.x - x, a.position.z - z) - Math.hypot(b.position.x - x, b.position.z - z))[0];
      if (!e || Math.hypot(e.position.x - x, e.position.z - z) > 1.5) { bot.log('  !! no NPC at', x, z); continue; }
      await bot.lookAt(e.position.offset(0, 1.6, 0));
      const wp = L.waitWindow(bot, 3000); await bot.activateEntity(e).catch((er) => bot.log('  npc err', er.message)); const w = await wp;
      if (w) showWin(w); else bot.log('  !! no window after clicking NPC at', x, z);
    }
    else if (op === 'hitmob') {
      const [type, n] = arg.split(' ');
      const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');
      if (!bot.pathfinder) bot.loadPlugin(pathfinder);
      const sw = bot.inventory.items().find((i) => /sword|axe/.test(i.name)); if (sw) await bot.equip(sw, 'hand');
      let hits = 0;
      for (let i = 0; i < +n; i++) {
        const e = bot.nearestEntity((en) => en.name === type);
        if (!e) { bot.log('  no', type, 'left after', hits, 'hits'); break; }
        if (e.position.distanceTo(bot.entity.position) > 3) { bot.pathfinder.setMovements(new Movements(bot)); await Promise.race([bot.pathfinder.goto(new goals.GoalFollow(e, 2)).catch(() => {}), L.sleep(3000)]); bot.pathfinder.stop(); }
        if (i % 10 === 0) bot.log('    dist', e.position.distanceTo(bot.entity.position).toFixed(1), 'pos', e.position.toString(), 'me', L.posStr(bot), 'hurtSeen', bot._hurtSeen || 0);
        await bot.lookAt(e.position.offset(0, e.height * 0.6, 0)); bot.attack(e); hits++; await L.sleep(650);
      }
      bot.log('  hits', hits);
    }
    else if (op === 'quit') break;
    await L.sleep(600);
  }
  bot.quit(); setTimeout(() => process.exit(0), 800);
})().catch((e) => { console.error('FAIL', e.message); process.exit(1); });
