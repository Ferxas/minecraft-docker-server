// Press every DeluxeMenus button as a regular (non-op) player and record what happens.
const L = require('./lib');
const fs = require('fs');
const { execFileSync } = require('child_process');
const menus = JSON.parse(fs.readFileSync(process.env.MENUS_JSON || 'menus.json', 'utf8'));
const NAME = process.env.BOTNAME || 'MenuTester';
const only = process.argv[2] ? new RegExp(process.argv[2]) : null;

// BFS paths (slot clicks from /menu) to each menu
const paths = { main_hub: [] };
const q = ['main_hub'];
while (q.length) {
  const m = q.shift();
  for (const it of menus[m].items) for (const c of it.cmds) {
    const t = (c.match(/^\[openguimenu\] (\S+)/) || [])[1];
    if (t && !(t in paths)) { paths[t] = [...paths[m], it.slot]; q.push(t); }
  }
}
const sh = (cmd) => { try { return execFileSync('wsl.exe', ['-e', 'bash', '-c', cmd], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }); } catch (e) { return e.stdout || ''; } };
const LOG = '~/servers/minecraft/stack/server-data/logs/latest.log';
const logLen = () => +sh(`wc -l < ${LOG}`).trim();
const logSince = (n) => sh(`tail -n +${n + 1} ${LOG}`).split('\n').filter((l) => l && !/AdvancedServerList|RCON|whois|PackStatus|LocalTime|Thread RCON|Starting GS4/.test(l));

async function joinBot() {
  const bot = L.createBot(NAME, { quiet: true });
  await bot.ready; await L.sleep(3500);
  for (let i = 0; i < 5 && bot.currentWindow; i++) { const w = bot.currentWindow; const s = [50, 49, 48].find((x) => w.slots[x]); if (s == null) { bot.closeWindow(w); break; } await bot.clickWindow(s, 0, 0).catch(() => {}); await L.sleep(1500); }
  await L.sleep(2500);
  return bot;
}

(async () => {
  L.rcon(`deop ${NAME}`);
  const report = [];
  const unreachable = Object.keys(menus).filter((m) => !(m in paths));
  if (unreachable.length) report.push({ menu: '(unreachable)', note: unreachable.join(', ') });
  for (const [m, def] of Object.entries(menus)) {
    if (!(m in paths)) continue;
    for (const it of def.items) {
      const id = `${m}/${it.key}`;
      if (only && !only.test(id)) continue;
      const bot = await joinBot();
      bot.events.length = 0;
      // open /menu and navigate
      let w = null; const wp = L.waitWindow(bot, 4000); bot.chat('/menu'); w = await wp;
      for (const s of paths[m]) { const p = L.waitWindow(bot, 3000); await bot.clickWindow(s, 0, 0).catch(() => {}); w = await p; if (!w) break; }
      const title = w ? L.textOf(w.title) : null;
      if (!w) { report.push({ id, error: 'could not reach menu' }); bot.quit(); await L.sleep(1500); continue; }
      const n0 = logLen(); bot.events.length = 0;
      const p2 = L.waitWindow(bot, 3500);
      await bot.clickWindow(it.slot, 0, 0).catch((e) => bot.log('click err', e.message));
      const w2 = await p2;
      await L.sleep(5000);
      const where = L.whereIs(NAME);
      const chat = bot.events.filter((e) => /CHAT|ACTIONBAR|TITLE/.test(e.kind) && e.msg.trim() && !/joined|returned|SkinsRestorer|pick up items|Broadcast/.test(e.msg)).map((e) => `${e.kind}: ${e.msg.replace(/\n/g, ' / ').slice(0, 160)}`);
      const srv = logSince(n0).map((l) => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '').slice(0, 200)).filter((l) => !/^\s+at /.test(l));
      const r = { id, menu: title, button: L.textOf(it.name || ''), cmds: it.cmds.filter((c) => !c.startsWith('[message]')),
        opened: w2 ? `${L.textOf(w2.title)} [${L.windowItems(w2).filter((x) => !/ ""$/.test(x)).length} items]` : null,
        where, hotbar: L.hotbar(bot).replace(/\d:- \| /g, ''), chat, server: srv };
      report.push(r);
      console.log('\n### ' + id + ' — ' + r.button);
      console.log('  cmds   :', r.cmds.join(' ; '));
      if (r.opened) console.log('  opened :', r.opened);
      console.log('  where  :', r.where, '| hotbar:', r.hotbar);
      for (const c of chat) console.log('  chat   :', c);
      for (const s of srv) console.log('  server :', s);
      bot.quit(); await L.sleep(2500);
    }
  }
  fs.writeFileSync('crawl-report.json', JSON.stringify(report, null, 1));
  if (report[0]?.menu === '(unreachable)') console.log('\nUNREACHABLE MENUS:', report[0].note);
  process.exit(0);
})().catch((e) => { console.error('FAIL', e); process.exit(1); });
