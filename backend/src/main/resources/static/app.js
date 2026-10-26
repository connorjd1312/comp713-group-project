// 三個頁面共用的工具：呼叫 API、顯示訊息、格式化資料
const API_BASE = '/api/tickets';

const STATUS_FLOW = ['RECEIVED', 'DIAGNOSING', 'REPAIRING', 'READY', 'COLLECTED'];
const NEXT_STATUS = { RECEIVED: 'DIAGNOSING', DIAGNOSING: 'REPAIRING', REPAIRING: 'READY', READY: 'COLLECTED' };

/**
 * 呼叫後端 API。成功回傳 JSON；失敗時丟出後端的 ApiError 物件，
 * 連不上伺服器時丟出 NETWORK_ERROR，讓頁面可以顯示「連線失敗」。
 */
async function callApi(path, options = {}) {
  let response;
  try {
    response = await fetch(path, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    });
  } catch (networkProblem) {
    throw { status: 0, error: 'NETWORK_ERROR',
            message: 'Cannot reach the server. Please check that it is running and try again.', fieldErrors: {} };
  }
  if (response.status === 204) return null;
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw body || { status: response.status, error: 'HTTP_' + response.status, message: response.statusText, fieldErrors: {} };
  }
  return body;
}

function showMessage(element, type, html) {
  element.className = 'message show ' + type;
  element.innerHTML = html;
}

function hideMessage(element) {
  element.className = 'message';
  element.innerHTML = '';
}

function errorHtml(err) {
  const code = err.status ? `HTTP ${err.status} · ${escapeHtml(err.error)}` : escapeHtml(err.error);
  return `<strong>${escapeHtml(err.message || 'Request failed')}</strong><br><code>${code}</code>`;
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-NZ', { dateStyle: 'medium', timeStyle: 'short' });
}

function badge(status) {
  return `<span class="badge ${escapeHtml(status)}">${escapeHtml(status)}</span>`;
}

function formatMoney(amount) {
  return Number(amount).toLocaleString('en-NZ', { style: 'currency', currency: 'NZD' });
}

/**
 * Live updates over WebSocket (same steps as the Week 10 live lab):
 * subscribe -> wait for "subscribed" -> reload the REST snapshot -> apply change notices.
 * When the connection drops the indicator shows "Reconnecting…" and we retry with backoff;
 * the snapshot reload after re-subscribing repairs anything missed while offline.
 */
function connectLive(indicator, onSnapshotNeeded, onChange) {
  let retryMs = 1000;
  function setState(state, text) {
    indicator.className = 'live ' + state;
    indicator.textContent = text;
  }
  function open() {
    const scheme = location.protocol === 'https:' ? 'wss://' : 'ws://';
    const socket = new WebSocket(scheme + location.host + '/ws/tickets');
    socket.onopen = () => socket.send(JSON.stringify({ type: 'subscribe' }));
    socket.onmessage = e => {
      const msg = JSON.parse(e.data);
      if (msg.type === 'subscribed') {
        retryMs = 1000;
        setState('on', 'Live');
        onSnapshotNeeded();
      } else if (msg.type === 'ticketChanged') {
        onChange(msg);
      }
    };
    socket.onclose = () => {
      setState('off', `Reconnecting in ${retryMs / 1000}s…`);
      setTimeout(open, retryMs);
      retryMs = Math.min(retryMs * 2, 10000);
    };
  }
  setState('off', 'Connecting…');
  open();
}

function clearFieldErrors(form) {
  form.querySelectorAll('.field-error').forEach(el => { el.textContent = ''; });
  form.querySelectorAll('.invalid').forEach(el => el.classList.remove('invalid'));
}

function showFieldErrors(form, fieldErrors) {
  Object.entries(fieldErrors || {}).forEach(([field, text]) => {
    const slot = form.querySelector(`.field-error[data-for="${field}"]`);
    if (slot) slot.textContent = text;
    if (form[field]) form[field].classList.add('invalid');
  });
}
