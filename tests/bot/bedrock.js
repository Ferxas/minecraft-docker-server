// Bedrock client test through Geyser: join, see what the player gets (texts, forms, containers),
// open /menu and click through a submenu.
const bedrock = require('bedrock-protocol');
const port = +(process.argv[2] || 19133);
const name = process.argv[3] || 'BedTester';
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
const strip = (s) => String(s ?? '').replace(/§./g, '');

const client = bedrock.createClient({ host: '127.0.0.1', port, username: name, offline: true, skipPing: false, connectTimeout: 20000 });
let runtimeId = null; let containers = []; let lastContainer = null;
client.on('start_game', (p) => { runtimeId = p.runtime_entity_id; log('START_GAME dim', p.dimension, 'pos', p.player_position); });
client.on('spawn', () => { log('SPAWNED'); });
client.on('text', (p) => { const m = strip(p.message); if (m.trim()) log('TEXT', p.type, m.slice(0, 160), p.parameters?.length ? JSON.stringify(p.parameters).slice(0, 80) : ''); });
client.on('set_title', (p) => { if (strip(p.text).trim()) log('TITLE', p.type, strip(p.text).slice(0, 100)); });
client.on('modal_form_request', (p) => { log('FORM', strip(p.data).slice(0, 300)); });
client.on('container_open', (p) => { lastContainer = p; log('CONTAINER_OPEN id', p.window_id, 'type', p.window_type); });
client.on('inventory_content', (p) => {
  if (lastContainer && p.window_id === lastContainer.window_id) {
    const items = p.input.map((it, i) => it && it.network_id ? `${i}:${it.network_id}${it.extra?.nbt?.nbt?.value?.display?.value?.Name ? '"' + strip(it.extra.nbt.nbt.value.display.value.Name.value) + '"' : ''}` : null).filter(Boolean);
    log('CONTAINER_ITEMS', items.length, items.slice(0, 12).join(' | ').slice(0, 400));
  }
});
client.on('update_block', () => {});
client.on('disconnect', (p) => { log('DISCONNECT', strip(p.message)); });
client.on('kick', (p) => { log('KICK', JSON.stringify(p)); });
client.on('error', (e) => log('ERROR', e.message));

function command(cmd) {
  client.queue('command_request', { command: cmd, origin: { type: 'player', uuid: '00000000-0000-0000-0000-000000000000', request_id: '', player_entity_id: 0n }, internal: false, version: '52' });
  log('>> ' + cmd);
}
(async () => {
  await new Promise((r) => client.once('spawn', r));
  await new Promise((r) => setTimeout(r, 6000));
  command('/menu');
  await new Promise((r) => setTimeout(r, 4000));
  command('/minigames');
  await new Promise((r) => setTimeout(r, 4000));
  command('/tienda');
  await new Promise((r) => setTimeout(r, 4000));
  client.close(); setTimeout(() => process.exit(0), 1000);
})();
setTimeout(() => { log('TIMEOUT'); process.exit(1); }, 90000);
