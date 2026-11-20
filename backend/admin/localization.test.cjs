// Run with: node admin/localization.test.cjs (requires Chromium and Node 22+).
// Uses an in-process mock server; no real API or credentials are contacted.
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { setTimeout: sleep } = require('node:timers/promises');
const requests = [];
const fixtures = {
  '/overview': { counts: { users: 1, connection_sessions: 7 }, activeSubscriptions: 1 },
  '/stats': { totalUsers: 1, activeSubscriptions: 1, walletsCount: 1, balanceTotal: 4, sessionsLast30Days: 7, activeUsers: { day: 1, week: 1, month: 1 }, subscriptionsByPlan: [{ name: 'الخادم', count: 1 }] },
  '/users': [{ id: 'u1', full_name: 'المستخدمون', email: 'person@example.com', role: 'admin', status: 'active', last_login_at: '2026-01-02T12:00:00Z', wallet: { balance: 3, currency: 'USD' } }],
  '/subscription-plans': [{ id: 'p1', name: '<img src=x onerror="window.xss=1">', description: 'خطة من الخادم', price: 2, currency: 'USD', duration_days: 30, free_duration_minutes: 60, free_claim_once: true, is_active: true, is_featured: true, feature_flags: { advanced: true } }],
  '/top-up-methods': [{ code: 'method"x', name: 'وسيلة الخادم', currency: 'USD', network: 'test', address: 'initial address', min_amount: 1, exchange_rate: 1, instructions: 'server instructions', is_enabled: true }],
  '/top-up-requests': [{ id: 'r1', users: { email: 'person@example.com' }, method_code: 'method', submitted_amount: 2, submitted_currency: 'USD', transaction_reference: 'abc', expected_usd: 2 }],
  '/landing-settings': { downloadUrl: 'https://example.com/app.apk' },
  '/app-update-settings': { enabled: true, minimumVersionCode: 1, message: 'رسالة من الخادم', updateUrl: '' },
  '/support-settings': { telegramUsername: 'help' },
  '/payment-secrets': { oxapay: { configured: true, feePaidByPayer: true }, syria: { configured: false } },
  '/users/u1/access': { grants: [{ feature_key: 'vpn', enabled: true }] },
  '/users/u1/activity': [{ action: 'server_action', entity_type: 'record', created_at: '2026-01-02T12:00:00Z', metadata: { note: 'سجل من الخادم' } }],
  '/users/u1/wallet': { wallet: { balance: 3, currency: 'USD' }, transactions: [] }
};
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname.startsWith('/admin/')) {
    const name = url.pathname === '/admin/' ? 'index.html' : path.basename(url.pathname);
    if (!['index.html', 'admin.js', 'localization.js'].includes(name)) { res.writeHead(404).end(); return; }
    res.writeHead(200, { 'Content-Type': name.endsWith('.html') ? 'text/html; charset=utf-8' : 'text/javascript; charset=utf-8' });
    fs.createReadStream(path.join(__dirname, name)).pipe(res); return;
  }
  if (url.pathname.startsWith('/api/')) {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      requests.push({ path: url.pathname + url.search, method: req.method, language: req.headers['accept-language'], body });
      const key = url.pathname.replace('/api/v1/admin', '');
      const error = key === '/local-error' || key === '/server-error';
      res.writeHead(error ? 500 : 200, { 'Content-Type': 'application/json' });
      if (key === '/local-error') return res.end('{}');
      if (key === '/server-error') return res.end(JSON.stringify({ error: { message: 'المستخدمون' } }));
      if (url.pathname === '/api/v1/auth/login') return res.end(JSON.stringify({ data: { accessToken: 'fake' } }));
      return res.end(JSON.stringify({ data: fixtures[key] ?? {} }));
    });
    return;
  }
  res.writeHead(404).end();
});

class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map();
    ws.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (!message.id) return;
      const task = this.pending.get(message.id); this.pending.delete(message.id);
      if (message.error) task.reject(new Error(message.error.message)); else task.resolve(message.result);
    });
  }
  call(method, params = {}) { return new Promise((resolve, reject) => { const id = ++this.id; this.pending.set(id, { resolve, reject }); this.ws.send(JSON.stringify({ id, method, params })); }); }
  async eval(expression) {
    const result = await this.call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    return result.result.value;
  }
  close() { this.ws.close(); }
}
async function connect(port) {
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  const client = new CDP(ws); await client.call('Page.enable'); await client.call('Runtime.enable'); return client;
}
async function waitFor(fn, label) {
  for (let i = 0; i < 100; i++) { const value = await fn(); if (value) return value; await sleep(50); }
  throw new Error(`Timed out waiting for ${label}`);
}
async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'admin-i18n-'));
  const chrome = spawn(process.env.CHROMIUM || 'chromium', ['--headless', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' });
  let page, blocked;
  try {
    const active = path.join(profile, 'DevToolsActivePort');
    await waitFor(() => fs.existsSync(active), 'Chromium');
    const port = Number(fs.readFileSync(active, 'utf8').split('\n')[0]);
    page = await connect(port);
    await page.call('Page.navigate', { url: `${origin}/admin/` });
    await waitFor(() => page.eval('!!window.AdminI18n'), 'login');
    assert.equal(await page.eval('document.documentElement.lang'), 'ar');
    assert.equal(await page.eval('document.documentElement.dir'), 'rtl');
    await page.eval(`document.querySelector('#email').value='person@example.com'; document.querySelector('#password').value='pass'; document.querySelector('#loginForm').requestSubmit()`);
    await waitFor(() => page.eval('!!document.querySelector("#newPlan")'), 'dashboard');
    assert(requests.some(r => r.path === '/api/v1/auth/login' && r.language === 'ar'));
    assert(requests.filter(r => r.path.startsWith('/api/v1/admin')).every(r => r.language === 'ar'));
    assert.equal(await page.eval('window.xss || 0'), 0);
    assert.equal(await page.eval('document.querySelector("#subscriptionPlans img")'), null);
    await page.eval(`
      document.querySelector('[data-tab="subscriptionPlans"]').click();
      document.querySelector('#newPlan').click();
      window.savedInput = document.querySelector('#modalForm [name="name"]');
      savedInput.value = 'غير محفوظ <data>';
      document.querySelector('#modalForm [name="description"]').value = 'draft text';
      document.querySelector('#modalForm [name="isFeatured"]').checked = true;
      document.querySelector('#modalForm input[name="features"][value="vpn"]').checked = true;
      document.querySelector('#secretOxapayKey').value = 'secret draft';
      document.querySelector('.method-address').value = 'address draft';
      document.querySelector('#userSearch').value = 'draft search';
    `);
    const count = requests.length;
    await page.eval('document.querySelector("#languageToggle").click()');
    assert.equal(requests.length, count, 'switch must not issue requests');
    assert.equal(await page.eval('document.documentElement.lang + "/" + document.documentElement.dir'), 'en/ltr');
    assert.equal(await page.eval('localStorage.getItem("admin_language")'), 'en');
    assert.equal(await page.eval('document.querySelector("#modalForm [name=name]") === window.savedInput'), true);
    assert.equal(await page.eval('savedInput.value'), 'غير محفوظ <data>');
    assert.equal(await page.eval('document.querySelector("#modalForm [name=description]").value'), 'draft text');
    assert.equal(await page.eval('document.querySelector("#modalForm [name=isFeatured]").checked'), true);
    assert.equal(await page.eval('document.querySelector("#secretOxapayKey").value'), 'secret draft');
    assert.equal(await page.eval('document.querySelector(".method-address").value'), 'address draft');
    assert.equal(await page.eval('document.querySelector("#userSearch").value'), 'draft search');
    assert.equal(await page.eval('document.querySelector("#modalRoot h3").textContent'), 'Add subscription plan');
    assert.equal(await page.eval('document.querySelector("#cancelModal").textContent'), 'Cancel');
    assert.equal(await page.eval('document.querySelector("#secretOxapayKey").placeholder'), 'Leave blank to keep the current value');
    assert.equal(await page.eval('document.querySelector("[data-tab=subscriptionPlans]").classList.contains("active")'), true);
    assert.equal(await page.eval('document.querySelector(".user-name").textContent'), 'المستخدمون', 'server text must not be translated');
    assert.equal(await page.eval('document.querySelector("#appUpdateMessage").value'), 'رسالة من الخادم');
    assert.equal(await page.eval('document.querySelector("#subscriptionPlans tbody b").textContent'), '<img src=x onerror="window.xss=1">');
    await page.eval('document.querySelector("#languageToggle").click()');
    assert.equal(await page.eval('document.querySelector("#modalRoot h3").textContent'), 'إضافة خطة اشتراك');
    assert.equal(await page.eval('savedInput.value'), 'غير محفوظ <data>');
    await page.eval('document.querySelector("#languageToggle").click(); document.querySelector("#modalForm").requestSubmit()');
    const savedPlan = await waitFor(() => requests.find(r => r.path === '/api/v1/admin/subscription-plans' && r.method === 'POST'), 'plan save');
    const planPayload = JSON.parse(savedPlan.body);
    assert.equal(savedPlan.language, 'en');
    assert.equal(planPayload.name, 'غير محفوظ <data>');
    assert.equal(planPayload.description, 'draft text');
    assert.equal(planPayload.isFeatured, true);
    assert.equal(planPayload.featureFlags.vpn, true);
    await waitFor(() => page.eval('!document.querySelector("#modalForm")'), 'plan dialog close');
    await waitFor(() => requests.slice(count).filter(r => r.path.startsWith('/api/v1/admin') && r.method === 'GET').length >= 10, 'refreshed dashboard requests');
    await sleep(100);
    await page.eval(`window.capturedConfirm=''; window.confirm = message => { window.capturedConfirm=message; return false; }; document.querySelector('[data-delete-plan]').click()`);
    assert.match(await page.eval('window.capturedConfirm'), /^Delete the plan/);
    await page.eval(`window.capturedPrompt=''; window.prompt = (message, value) => { window.capturedPrompt=message; window.promptDefault=value; return null; }; document.querySelector('.topup-reject').click()`);
    assert.equal(await page.eval('window.capturedPrompt'), 'Reason for rejection:');
    assert.equal(await page.eval('window.promptDefault'), 'لم يتم التحقق من العملية');
    await page.eval(`document.querySelector('[data-action=features]').click()`);
    await waitFor(() => page.eval('!!document.querySelector("#modalForm input[name=features]")'), 'features modal');
    assert.match(await page.eval('document.querySelector("#modalRoot").textContent'), /Device-wide VPN connection/);
    await page.eval(`document.querySelector('#modalForm [name=expiresAt]').value = '2030-03-04'; document.querySelector('#modalForm [value=advanced]').checked = true; document.querySelector('#languageToggle').click()`);
    assert.equal(await page.eval('document.querySelector("#modalForm [name=expiresAt]").value'), '2030-03-04');
    assert.equal(await page.eval('document.querySelector("#modalForm [value=advanced]").checked'), true);
    assert.match(await page.eval('document.querySelector("#modalRoot").textContent'), /اتصال VPN على مستوى الجهاز/);
    await page.eval('document.querySelector("#languageToggle").click()');
    await page.eval('document.querySelector("#cancelModal").click(); document.querySelector("[data-action=activity]").click()');
    await waitFor(() => page.eval('!!document.querySelector(".activity-list")'), 'activity modal');
    assert.match(await page.eval('document.querySelector(".activity-list").textContent'), /سجل من الخادم/);
    await page.eval('document.querySelector("#cancelModal").click(); document.querySelector("[data-action=password]").click()');
    assert.match(await page.eval('document.querySelector("#modalRoot").textContent'), /New password/);
    await page.eval('document.querySelector("#cancelModal").click(); document.querySelector("#broadcastUsers").click()');
    assert.match(await page.eval('document.querySelector("#modalRoot").textContent'), /Send a message to all users/);
    await page.eval('document.querySelector("#cancelModal").click(); document.querySelector("[data-action=plan]").click()');
    assert.match(await page.eval('document.querySelector("#modalRoot").textContent'), /Activation period in days/);
    await page.eval('document.querySelector("#cancelModal").click()');
    await page.eval('action(() => api("/local-error"))');
    assert.equal(await page.eval('document.querySelector("#toastRoot .toast.error:last-child").textContent'), 'Unable to complete the request');
    await page.eval('document.querySelector("#languageToggle").click()');
    assert.equal(await page.eval('document.querySelector("#toastRoot .toast.error:last-child").textContent'), 'تعذر تنفيذ الطلب');
    await page.eval(`(async () => { const original = window.fetch; window.fetch = () => Promise.reject(new TypeError('Failed to fetch')); try { await action(() => api('/network-error')); } finally { window.fetch = original; } })()`);
    assert.equal(await page.eval('document.querySelector("#toastRoot .toast.error:last-child").textContent'), 'تعذر الاتصال بالخادم');
    await page.eval('action(() => api("/server-error"))');
    await page.eval('document.querySelector("#languageToggle").click()');
    assert.equal(await page.eval('document.querySelector("#toastRoot .toast.error:last-child").textContent'), 'المستخدمون');
    await page.call('Page.reload');
    await waitFor(() => page.eval('!!document.querySelector("#newPlan")'), 'reload');
    assert.equal(await page.eval('document.documentElement.lang + "/" + document.documentElement.dir'), 'en/ltr');
    assert(requests.slice(count).some(r => r.path.startsWith('/api/v1/admin') && r.language === 'en'));

    blocked = await connect(port);
    await blocked.call('Page.addScriptToEvaluateOnNewDocument', { source: `Object.defineProperty(window, 'localStorage', { get() { throw new DOMException('Blocked', 'SecurityError'); } });` });
    await blocked.call('Page.navigate', { url: `${origin}/admin/` });
    await waitFor(() => blocked.eval('!!window.AdminI18n'), 'blocked-storage login');
    await blocked.eval(`document.querySelector('#languageToggle').click(); document.querySelector('#email').value='a@b.test'; document.querySelector('#password').value='pass'; document.querySelector('#loginForm').requestSubmit()`);
    await waitFor(() => blocked.eval('!!document.querySelector("#newPlan")'), 'blocked-storage dashboard');
    assert.equal(await blocked.eval('document.documentElement.lang'), 'en');
    assert.equal(await blocked.eval('document.querySelector("#app").classList.contains("hidden")'), false);
    console.log('PASS: live switching, drafts, modal state, translations, escaping, API headers, reload persistence and blocked storage');
  } finally {
    page?.close(); blocked?.close(); chrome.kill('SIGTERM'); server.close();
    await sleep(100); fs.rmSync(profile, { recursive: true, force: true });
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
