'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');
const [rootArg, java, seedArg, platform, modules] = process.argv.slice(2);
const root = path.resolve(rootArg), seed = path.resolve(seedArg);
assert(path.basename(root).startsWith('nordfireworks-test-') && !fs.existsSync(root), 'Fresh isolated test directory required');
assert(['Paper', 'Folia'].includes(platform));
assert.notEqual(root, seed);
const mineflayer = require(path.join(path.resolve(modules), 'mineflayer'));
const project = path.resolve(__dirname, '..');
let server, output = '', exited = false, bot, sequence = 0;
const packets = [], passed = [];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(fn, label, timeout = 20000) {
  const start = Date.now();
  while (!fn()) { if (Date.now() - start > timeout) throw Error('Timeout: ' + label); await sleep(50); }
}
async function marker(command, pattern) {
  const offset = output.length;
  server.stdin.write(command + '\n');
  await until(() => pattern.test(output.slice(offset)), command);
  return output.slice(offset).match(pattern);
}
async function state() {
  const match = await marker('fwtest state FWRocket', /FW_STATE (\d+) (\d+) (\d+) (\d+) (\d+) (true|false)/);
  return { cooldown: +match[1], main: +match[2], off: +match[3], boosts: +match[4], ground: +match[5], gliding: match[6] === 'true' };
}
function use(hand = 0) {
  bot._client.write('use_item', { hand, sequence: sequence++, rotation: { x: 0, y: 0 } });
}
function groundUse(position, hand = 0) {
  bot._client.write('block_place', { hand, location: position, direction: 1, cursorX: 0.5, cursorY: 1, cursorZ: 0.5,
    insideBlock: false, worldBorderHit: false, sequence: sequence++ });
}
function pass(label) { passed.push(label); console.log('PASS: ' + label); }
async function prepare(mode) {
  const match = await marker('fwtest prepare FWRocket ' + mode, mode === 'air' ? /FW_PREPARED air true/ : /FW_PREPARED ground (-?\d+) (-?\d+) (-?\d+)/);
  await sleep(150);
  return mode === 'ground' ? { x: +match[1], y: +match[2], z: +match[3] } : null;
}
async function main() {
  const occupied = await new Promise(resolve => {
    const socket = net.connect({ host: '127.0.0.1', port: 25648 });
    socket.on('connect', () => { socket.destroy(); resolve(true); });
    socket.on('error', () => resolve(false));
  });
  assert(!occupied, 'Loopback test port already occupied');
  fs.mkdirSync(path.join(root, 'plugins'), { recursive: true });
  // Runtime files only: never copy existing plugins, configuration, worlds or player data.
  for (const name of ['server.jar', 'cache', 'libraries', 'versions', 'eula.txt']) {
    if (fs.existsSync(path.join(seed, name))) fs.cpSync(path.join(seed, name), path.join(root, name), { recursive: true });
  }
  assert(fs.readFileSync(path.join(root, 'eula.txt'), 'utf8').includes('eula=true'), 'Existing accepted test EULA required');
  fs.copyFileSync(path.join(__dirname, 'fixtures/server.properties'), path.join(root, 'server.properties'));
  fs.copyFileSync(path.join(project, 'target/NordFireworks-1.0.0.jar'), path.join(root, 'plugins/NordFireworks-1.0.0.jar'));
  fs.copyFileSync(path.join(__dirname, 'build/FireworksTestProbe.jar'), path.join(root, 'plugins/FireworksTestProbe.jar'));
  server = spawn(java, ['-Dterminal.jline=false', '-Dterminal.ansi=false', '-Xms256M', '-Xmx1400M', '-jar', 'server.jar', 'nogui'],
    { cwd: root, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  for (const stream of [server.stdout, server.stderr]) stream.on('data', bytes => { output += bytes.toString().replace(/\x1b\[[0-9;]*m/g, ''); });
  server.on('exit', () => { exited = true; });
  server.on('error', error => { output += String(error); exited = true; });
  await until(() => /Done \(/.test(output) || exited, 'server startup', 120000);
  assert(!exited, output.slice(-4000));
  assert.match(output, /Enabling NordFireworks v1.0.0/);
  assert.match(output, new RegExp(platform + ' version'));
  pass(platform + ' enables the same release JAR');
  bot = mineflayer.createBot({ host: '127.0.0.1', port: 25648, username: 'FWRocket', version: '26.2', auth: 'offline', hideErrors: true });
  bot.on('error', error => { console.error(error); });
  bot.on('kicked', reason => { console.error('KICKED', reason); });
  bot.once('spawn', () => { bot.physicsEnabled = false; });
  bot._client.on('set_cooldown', packet => packets.push(packet));
  await until(() => bot.entity && output.includes('FWRocket joined the game'), 'client join', 60000);
  await sleep(500);

  const position = await prepare('ground');
  const beforePackets = packets.length;
  groundUse(position);
  await sleep(150);
  let s = await state();
  assert.equal(s.ground, 1); assert.equal(s.main, 63); assert(s.cooldown > 0);
  assert(packets.slice(beforePackets).some(p => p.cooldownGroup === 'minecraft:firework_rocket' && p.cooldownTicks === 40));
  pass('Actual ground launch consumes one rocket and sends the vanilla cooldown packet');
  groundUse(position, 1);
  bot._client.write('held_item_slot', { slotId: 1 });
  groundUse(position);
  await sleep(150);
  s = await state();
  assert.equal(s.ground, 1); assert.equal(s.main, 64); assert.equal(s.off, 64);
  pass('Offhand and hotbar switching cannot bypass cooldown or consume blocked rockets');

  await prepare('air');
  assert.equal((await state()).gliding, true);
  use();
  await sleep(150);
  s = await state();
  assert.equal(s.boosts, 1); assert.equal(s.main, 63); assert(s.cooldown > 0);
  use(1); use();
  await sleep(150);
  s = await state();
  assert.equal(s.boosts, 1); assert.equal(s.main, 63); assert.equal(s.off, 64);
  pass('Actual elytra boost is throttled without consuming denied rockets');
  await sleep(2100);
  use(1);
  await sleep(150);
  s = await state();
  assert.equal(s.boosts, 2); assert.equal(s.off, 63);
  pass('Next rocket works after the built-in cooldown expires');

  await prepare('air');
  await marker('fwtest cancel FWRocket', /FW_CANCEL_READY/);
  use();
  await sleep(150);
  s = await state();
  assert.equal(s.boosts, 0); assert.equal(s.main, 64); assert.equal(s.cooldown, 0);
  pass('Another plugin cancelling a boost causes no cooldown and no item loss');

  await prepare('air');
  await marker('fwtest bypass FWRocket', /FW_BYPASS_READY/);
  use(); use(1);
  await sleep(200);
  s = await state();
  assert.equal(s.boosts, 2); assert.equal(s.cooldown, 0);
  pass('Explicit bypass permission disables new plugin cooldowns');
  await marker('nordfireworks reload', /NordFireworks reloaded: 40 ticks/);
  assert(!/FW_PROBE_FAILED|Could not pass event|Thread failed main thread check|Cannot read world asynchronously/i.test(output));
  pass('Reload succeeds; no event or region ownership errors');
}
(async () => {
  try { await main(); }
  catch (error) { console.error(error); console.error(output.slice(-3000)); process.exitCode = 1; }
  finally {
    if (bot) bot.quit();
    if (server && !exited) {
      server.stdin.write('stop\n');
      try { await until(() => exited, 'clean test shutdown', 45000); }
      catch { server.kill(); process.exitCode = 1; }
    }
    if (fs.existsSync(root)) {
      fs.writeFileSync(path.join(root, 'integration-output.log'), output);
      fs.writeFileSync(path.join(root, 'results.json'), JSON.stringify({ platform, passed, success: !process.exitCode, notes: 'Synthetic loopback test, not a load benchmark or human visual test.' }, null, 2));
    }
  }
})();
