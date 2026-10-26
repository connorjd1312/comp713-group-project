// Full-system checks using only Node's standard library and the packaged Java applications.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, openSync, closeSync, readFileSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import os from 'node:os';

// Resolve Oracle's Windows launcher to the actual JDK binary so killing a test
// child also stops its JVM instead of leaving a detached service behind.
const settings = spawnSync('java', ['-XshowSettings:properties', '-version'], { encoding: 'utf8', windowsHide: true });
const javaHome = settings.stderr?.match(/java\.home\s*=\s*(.+)/)?.[1]?.trim();
const java = process.env.VERIFY_JAVA || (javaHome ? resolve(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java');

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, 'test-results', new Date().toISOString().replace(/[:.]/g, '-'));
mkdirSync(output, { recursive: true });
const backendJar = resolve(root, 'backend/target/repair-tickets-0.0.1-SNAPSHOT.jar');
const messagingJar = resolve(root, 'messaging/target/repair-messaging-0.0.1-SNAPSHOT.jar');
const children = new Set();
const results = [];
let socket;

async function freePort() {
  const server = createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}
const port = await freePort();
const brokerPort = await freePort();
const base = `http://127.0.0.1:${port}`;
const env = { ...process.env, BROKER_URL: `tcp://127.0.0.1:${brokerPort}`,
  REPAIR_DATA_DIR: output, BROKER_QUEUE: 'repair.events' };

function start(name, jar, args) {
  const log = resolve(output, `${name}.log`);
  const fd = openSync(log, 'w');
  const child = spawn(java, ['-Xmx256m', '-jar', jar, ...args], {
    cwd: root, env, windowsHide: true, stdio: ['ignore', fd, fd],
  });
  closeSync(fd);
  child.log = log;
  child.on('error', error => { child.startError = error; });
  children.add(child);
  return child;
}
async function stop(child) {
  if (!child || child.exitCode !== null) return;
  child.kill();
  await until(() => child.exitCode !== null || child.signalCode !== null, 15000);
  children.delete(child);
}
async function until(check, timeout = 60000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    if (await check()) return;
    await delay(200);
  }
  throw new Error('Timed out waiting for condition');
}
function check(name, detail = '') {
  results.push({ name, passed: true, detail });
  console.log(`PASS ${name}${detail ? ': ' + detail : ''}`);
}
async function request(path, method = 'GET', body, expected = 200) {
  const response = await fetch(base + path, { method,
    headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(10000) });
  const data = await response.text();
  assert.equal(response.status, expected, `${method} ${path}: ${data}`);
  return data ? JSON.parse(data) : null;
}
function backend(name) {
  return start(name, backendJar, [`--server.port=${port}`, '--app.seed-demo-data=false',
    '--spring.jpa.show-sql=false', `--spring.datasource.url=jdbc:h2:file:${output.replaceAll('\\', '/')}/repairdb;WRITE_DELAY=0`]);
}
async function ready(child, marker) {
  await until(() => {
    if (child.startError) throw child.startError;
    if (child.exitCode !== null) throw new Error(readFileSync(child.log, 'utf8'));
    return readFileSync(child.log, 'utf8').includes(marker);
  });
}
async function connect() {
  const ws = new WebSocket(base.replace('http:', 'ws:') + '/ws/tickets');
  ws.messages = [];
  ws.addEventListener('message', event => ws.messages.push(JSON.parse(event.data)));
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket open timeout')), 10000);
    ws.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
    ws.addEventListener('error', error => { clearTimeout(timer); reject(error); }, { once: true });
  });
  ws.send('{"type":"subscribe"}');
  await until(() => ws.messages.some(message => message.type === 'subscribed'));
  return ws;
}
function command(args) {
  const result = spawnSync(java, ['-Xmx128m', '-jar', messagingJar, ...args], {
    cwd: root, env, windowsHide: true, encoding: 'utf8', timeout: 30000,
  });
  assert.equal(result.status, 0, result.stderr || result.error?.message || result.stdout);
  return result.stdout.trim();
}
function receipts() { return JSON.parse(command(['receipts'])); }

try {
  let api = backend('backend');
  await ready(api, 'Started RepairTicketsApplication');
  socket = await connect();
  await request('/api/tickets', 'POST', {}, 400);
  await request('/api/tickets/999999', 'GET', undefined, 404);
  check('Invalid input and missing ticket return appropriate errors');

  const create = (email = 'mvp@example.com') => request('/api/tickets', 'POST', {
    name: 'MVP Customer', email, deviceType: 'PHONE', deviceModel: 'Demo Phone',
    issueDescription: 'The screen flickers after charging overnight',
  }, 201);
  let ticket = await create();
  await until(() => socket.messages.some(message => message.ticketId === ticket.id && message.change === 'CREATED'));
  assert.equal((await request('/api/tickets?email=mvp%40example.com')).length, 1);
  check('Customer booking is visible through REST and WebSocket');
  assert.ok((await request('/api/integration/status')).pending > 0);
  check('Broker outage leaves a durable event while ticket creation succeeds');

  const path = `/api/tickets/${ticket.id}`;
  await request(path + '/status', 'PATCH', { status: 'READY' }, 409);
  ticket = await request(path + '/status', 'PATCH', { status: 'DIAGNOSING' });
  await request(path + '/status', 'PATCH', { status: 'REPAIRING' }, 409);
  await request(path + '/quote', 'POST', { amount: 99, description: 'Replace display connector' }, 201);
  await request(path + '/quote/decision', 'POST', { customerEmail: 'other@example.com', approved: true }, 403);
  await request(path + '/quote/decision', 'POST', { customerEmail: 'mvp@example.com', approved: false });
  await request(path + '/status', 'PATCH', { status: 'REPAIRING' }, 409);
  await request(path + '/quote', 'POST', { amount: 79, description: 'Revised connector repair' }, 201);
  ticket = await request(path + '/quote/decision', 'POST', { customerEmail: 'mvp@example.com', approved: true });
  assert.ok(ticket.version > 0);
  await until(() => socket.messages.some(message => message.ticketId === ticket.id && message.change === 'QUOTE_APPROVED'));
  check('Quote decline, revision and approval enforce workflow and notify the technician');

  socket.close();
  await request(path + '/status', 'PATCH', { status: 'REPAIRING' });
  ticket = await request(path + '/status', 'PATCH', { status: 'READY' });
  socket = await connect();
  assert.equal((await request(path)).status, 'READY');
  check('Reconnection snapshot recovers changes missed while disconnected');

  let broker = start('broker', messagingJar, ['broker']);
  await ready(broker, 'BROKER READY');
  await until(async () => (await request('/api/integration/status')).pending === 0);
  check('Outbox drains when the broker becomes available');

  // Stop/restart the broker with queued messages and no consumer yet.
  await stop(broker);
  broker = start('broker-restarted', messagingJar, ['broker']);
  await ready(broker, 'BROKER READY');
  const crash = start('consumer-crash', messagingJar, ['consume', '--crash-after-save']);
  await until(() => crash.exitCode !== null);
  assert.equal(crash.exitCode, 23, readFileSync(crash.log, 'utf8'));
  const retry = start('consumer-redelivery', messagingJar, ['consume', '--once']);
  await until(() => retry.exitCode !== null);
  assert.equal(retry.exitCode, 0, readFileSync(retry.log, 'utf8'));
  assert.match(readFileSync(retry.log, 'utf8'), /DUPLICATE SKIPPED redelivered=true/);
  const firstReceipts = receipts();
  assert.equal(firstReceipts.length, 1);
  check('Broker restart retains events; crash redelivery creates one receipt');

  // Re-publish an already processed event with exactly the same domain eventId.
  const duplicatePath = resolve(output, 'duplicate.json');
  writeFileSync(duplicatePath, firstReceipts[0].payload);
  command(['publish', duplicatePath]);
  const consumer = start('consumer', messagingJar, ['consume']);
  await ready(consumer, 'CONSUMER READY');
  const published = (await request('/api/integration/status')).published;
  await until(() => receipts().length === published);
  await until(() => readFileSync(consumer.log, 'utf8').includes('DUPLICATE SKIPPED'));
  assert.equal(receipts().length, published);
  check('Independent consumer drains backlog and repeated event has no duplicate effect');

  const samples = [];
  for (let i = 0; i < 100; i++) await create(`load-${i}@example.com`);
  for (let i = 0; i < 110; i++) {
    const startTime = performance.now();
    await request(path);
    if (i >= 10) samples.push(performance.now() - startTime);
  }
  samples.sort((a, b) => a - b);
  const p95 = samples[Math.ceil(samples.length * .95) - 1];
  const performanceResult = { targetP95Ms: 500, p95Ms: +p95.toFixed(2),
    medianMs: +samples[49].toFixed(2), samples: 100, warmup: 10, concurrency: 1,
    ticketCount: (await request('/api/tickets')).length,
    operation: 'GET ticket detail, full HTTP response including JSON parsing',
    platform: `${os.platform()} ${os.release()}`, cpu: os.cpus()[0].model,
    node: process.version, rawSortedMs: samples };
  writeFileSync(resolve(output, 'response-time.json'), JSON.stringify(performanceResult, null, 2));
  assert.ok(p95 < 500, `p95 ${p95} ms exceeds 500 ms`);
  check('Response-time acceptance target', `p95 ${p95.toFixed(2)} ms < 500 ms; 101 tickets, 100 samples`);

  socket.close();
  await stop(api);
  await assert.rejects(fetch(base + path, { signal: AbortSignal.timeout(2000) }));
  api = backend('backend-restarted');
  await ready(api, 'Started RepairTicketsApplication');
  const persisted = await request(path);
  assert.equal(persisted.status, 'READY');
  assert.equal(persisted.quote.status, 'APPROVED');
  assert.equal(persisted.history.length, ticket.history.length);
  check('Backend failure/restart preserves ticket, quote and history');
  await until(async () => (await request('/api/integration/status')).pending === 0);
  await until(() => receipts().length === (published + 100));
  check('Outbox backlog survives backend restart and is eventually consumed');
} catch (error) {
  results.push({ name: 'Run failed', passed: false, detail: error.stack });
  console.error(error);
  process.exitCode = 1;
} finally {
  socket?.close();
  for (const child of children) await stop(child);
  writeFileSync(resolve(output, 'results.json'), JSON.stringify({ at: new Date().toISOString(), results }, null, 2));
  console.log(`Evidence: ${output}`);
}
