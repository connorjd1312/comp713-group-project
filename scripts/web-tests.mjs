import { test } from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';

const staticRoot = new URL('../backend/src/main/resources/static/', import.meta.url);
const app = readFileSync(new URL('app.js', staticRoot), 'utf8');
function environment() {
  const elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, { innerHTML: '', textContent: '', value: '',
      style: {}, addEventListener() {}, querySelector: () => null, reset() {},
      classList: { add() {} } });
    return elements.get(id);
  };
  const requests = [];
  const context = vm.createContext({ console, URLSearchParams,
    location: { search: '?id=1', protocol: 'http:', host: 'localhost' },
    document: { getElementById: element },
  });
  vm.runInContext(app, context);
  context.callApi = () => new Promise((resolve, reject) => requests.push({ resolve, reject }));
  context.connectLive = () => {};
  element('filters').status = { value: '' };
  element('filters').email = { value: '' };
  return { context, requests, element };
}
function page(context, name) {
  const html = readFileSync(new URL(name, staticRoot), 'utf8');
  vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1], context);
}
const flush = () => new Promise(resolve => setImmediate(resolve));
const ticket = (version, status) => ({ id: 1, version, status, customerName: 'Test', customerEmail: 'test@example.com',
  deviceType: 'PHONE', deviceModel: 'Demo', issueDescription: 'A test repair',
  createdAt: '2026-10-27T10:00:00', updatedAt: '2026-10-27T10:00:00', history: [], quote: null });

test('ticket list ignores an older response that finishes last', async () => {
  const { context, requests, element } = environment();
  page(context, 'tickets.html');
  vm.runInContext('loadTickets()', context);
  requests[1].resolve([ticket(2, 'READY')]);
  await flush();
  requests[0].resolve([ticket(1, 'RECEIVED')]);
  await flush();
  assert.match(element('rows').innerHTML, /READY/);
  assert.doesNotMatch(element('rows').innerHTML, /RECEIVED/);
});

test('ticket detail ignores older versions and obsolete load errors', async () => {
  const { context, requests, element } = environment();
  page(context, 'ticket.html');
  vm.runInContext('load()', context);
  requests[1].resolve(ticket(2, 'READY'));
  await flush();
  requests[0].reject({ message: 'obsolete failure', error: 'NETWORK_ERROR' });
  await flush();
  context.staleTicket = ticket(1, 'RECEIVED');
  vm.runInContext('render(staleTicket)', context);
  assert.match(element('title').innerHTML, /READY/);
  assert.doesNotMatch(element('message').innerHTML, /obsolete failure/);
});

test('repair action is disabled until the current quote is approved', async () => {
  const { context, requests, element } = environment();
  page(context, 'ticket.html');
  requests[0].resolve(ticket(1, 'DIAGNOSING'));
  await flush();
  assert.equal(element('advanceBtn').disabled, true);
  context.approved = { ...ticket(2, 'DIAGNOSING'), quote: { status: 'APPROVED', amount: 10, description: 'Repair' } };
  vm.runInContext('render(approved)', context);
  assert.equal(element('advanceBtn').disabled, false);
});

test('live connection subscribes before snapshot and repeats recovery after reconnect', () => {
  const sockets = [], timers = [], actions = [];
  class FakeWebSocket {
    constructor() { sockets.push(this); }
    send(message) { actions.push(JSON.parse(message).type); }
  }
  const context = vm.createContext({ WebSocket: FakeWebSocket,
    location: { protocol: 'http:', host: 'localhost' },
    setTimeout: (callback, ms) => timers.push({ callback, ms }),
  });
  vm.runInContext(app, context);
  context.indicator = {};
  context.snapshot = () => actions.push('snapshot');
  context.change = () => actions.push('change');
  vm.runInContext('connectLive(indicator, snapshot, change)', context);
  sockets[0].onopen();
  assert.deepEqual(actions, ['subscribe']);
  sockets[0].onmessage({ data: '{"type":"subscribed"}' });
  assert.deepEqual(actions, ['subscribe', 'snapshot']);
  sockets[0].onclose();
  assert.equal(timers[0].ms, 1000);
  timers[0].callback();
  sockets[1].onopen();
  sockets[1].onmessage({ data: '{"type":"subscribed"}' });
  assert.deepEqual(actions, ['subscribe', 'snapshot', 'subscribe', 'snapshot']);
});
