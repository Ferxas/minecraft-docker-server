// Test-bot helpers for the Minecraft server (mineflayer).
const mineflayer = require('mineflayer');

const strip = (s) => String(s ?? '').replace(/§./g, '');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Flatten chat components / NBT-encoded components into plain text.
function textOf(c) {
  if (c == null) return '';
  if (typeof c === 'string') { try { return textOf(JSON.parse(c)); } catch { return strip(c); } }
  if (typeof c !== 'object') return strip(String(c));
  if (c.constructor && c.constructor.name === 'ChatMessage') return strip(c.toString());
  if (c.type && 'value' in c) return textOf(c.value); // nbt node
  if (Array.isArray(c)) return c.map(textOf).join('');
  let out = '';
  if (c.text != null) out += textOf(c.text);
  if (c.translate != null) out += textOf(c.translate);
  if (c[''] != null) out += textOf(c['']);
  if (c.extra != null) out += textOf(c.extra);
  if (c.with != null && !out) out += textOf(c.with);
  return strip(out);
}

function itemName(it) {
  if (!it) return null;
  const n = it.customName ? textOf(it.customName) : '';
  return `${it.name}${it.count > 1 ? 'x' + it.count : ''}${n ? ' "' + n.trim() + '"' : ''}`;
}

function createBot(username = 'ClaudeBot', opts = {}) {
  const bot = mineflayer.createBot({
    host: opts.host || '127.0.0.1',
    port: opts.port || 25565,
    username,
    version: opts.version || '1.21.11',
    auth: 'offline',
    hideErrors: false,
    checkTimeoutInterval: 120000,
  });
  const log = (...a) => console.log(`[${new Date().toISOString().slice(11, 19)}] [${username}]`, ...a);
  bot.log = log;
  bot.events = [];
  const rec = (kind, msg) => { bot.events.push({ t: Date.now(), kind, msg }); if (!opts.quiet) log(kind, msg); };

  bot.on('messagestr', (m, pos) => { if (m.trim()) rec(pos === 'game_info' ? 'ACTIONBAR' : 'CHAT', m); });
  bot.on('title', (t, type) => rec('TITLE-' + type, textOf(t)));
  bot.on('kicked', (r) => rec('KICKED', textOf(r)));
  bot.on('error', (e) => rec('ERROR', e.message));
  bot.on('end', (r) => rec('END', r));
  bot.on('resourcePack', () => { rec('RESOURCEPACK', 'accepting'); try { bot.acceptResourcePack(); } catch (e) { rec('ERROR', 'rp ' + e.message); } });
  bot.on('windowOpen', (w) => rec('WINDOW', `${textOf(w.title)} (${w.type}, ${w.slots.length} slots)`));
  bot.on('respawn', () => rec('RESPAWN', bot.game?.dimension));
  bot.on('death', () => rec('DEATH', ''));
  bot.on('forcedMove', () => { if (opts.trackMoves) rec('TP', posStr(bot)); });

  bot.ready = new Promise((resolve, reject) => {
    bot.once('spawn', () => { rec('SPAWN', posStr(bot)); resolve(); });
    bot.once('kicked', (r) => reject(new Error('kicked: ' + textOf(r))));
    bot.once('error', reject);
  });
  return bot;
}

function posStr(bot) {
  const p = bot.entity?.position;
  return p ? `${p.x.toFixed(1)},${p.y.toFixed(1)},${p.z.toFixed(1)}` : '?';
}

function hotbar(bot) {
  const out = [];
  for (let i = 36; i <= 44; i++) out.push(`${i - 36}:${itemName(bot.inventory.slots[i]) || '-'}`);
  return out.join(' | ');
}

function inventoryList(bot) {
  return bot.inventory.items().map((it) => `${it.slot}:${itemName(it)}`).join(' | ');
}

function windowItems(w) {
  const n = w.inventoryStart ?? w.slots.length;
  const out = [];
  for (let i = 0; i < n; i++) { const it = w.slots[i]; if (it) out.push(`${i}:${itemName(it)}`); }
  return out;
}

function waitWindow(bot, ms = 5000) {
  return new Promise((resolve) => {
    const t = setTimeout(() => { bot.removeListener('windowOpen', h); resolve(null); }, ms);
    const h = (w) => { clearTimeout(t); setTimeout(() => resolve(w), 400); };
    bot.once('windowOpen', h);
  });
}

async function chat(bot, msg, wait = 1500) { bot.chat(msg); await sleep(wait); }

async function useHotbar(bot, slot, wait = 1500) {
  bot.setQuickBarSlot(slot);
  await sleep(250);
  bot.activateItem();
  await sleep(100);
  bot.deactivateItem();
  await sleep(wait);
}

async function clickWindow(bot, slot, button = 0, mode = 0, wait = 1200) {
  await bot.clickWindow(slot, button, mode).catch((e) => bot.log('ERROR click', e.message));
  await sleep(wait);
}

function worldName(bot) { return bot.game?.dimension; }

// Run a server console command via RCON inside the WSL docker container.
const { execFileSync } = require('child_process');
function rcon(cmd) {
  try {
    const out = execFileSync('wsl.exe', ['-e', 'docker', 'exec', 'mc', 'rcon-cli', cmd], { encoding: 'utf8', env: { ...process.env, MSYS_NO_PATHCONV: '1' }, stdio: ['ignore', 'pipe', 'ignore'] });
    return strip(out.replace(/\u001b\[[0-9;]*m/g, '')).trim();
  } catch (e) { return 'RCON-ERR ' + e.message; }
}
function whereIs(name) {
  const o = rcon('whois ' + name);
  const m = o.match(/Location: \(([^,]+), ([-\d.]+), ([-\d.]+), ([-\d.]+)\)/);
  return m ? `${m[1]} ${(+m[2]).toFixed(0)} ${(+m[3]).toFixed(0)} ${(+m[4]).toFixed(0)}` : o.slice(0, 200);
}

module.exports = { rcon, whereIs, createBot, sleep, strip, textOf, itemName, hotbar, inventoryList, windowItems, waitWindow, chat, useHotbar, clickWindow, posStr, worldName };
