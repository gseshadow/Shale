// Isolated synthetic review; never imported by the application.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173', out = 'shale-web/docs/phase-3h-evidence';
(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  try {
    for (const width of [320, 1280]) for (const scenario of ['read', 'store', 'clear', 'rejection']) {
      console.log(JSON.stringify({ width, scenario, started: true }));
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      await context.route('**/*', route => {
        const url = new URL(route.request().url());
        if (url.origin === origin && !url.pathname.startsWith('/api/')) return route.continue();
        unexpected.push(url.origin + url.pathname); return route.abort();
      });
      await context.addInitScript(({ scenario }) => {
        const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User', nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
        const originalFetch = window.fetch.bind(window), originalRead = Storage.prototype.getItem, originalWrite = Storage.prototype.setItem, originalClear = Storage.prototype.removeItem;
        const fixture = window.fixture = { calls: [], storageCalls: [], failRead: false, failStore: false, failClear: false, reject: false };
        Storage.prototype.getItem = function(key) { fixture.storageCalls.push('read'); if (fixture.failRead) throw new Error('Synthetic private read'); return originalRead.call(this, key); };
        Storage.prototype.setItem = function(key, value) { fixture.storageCalls.push('store'); if (fixture.failStore) throw new Error('Synthetic private write'); return originalWrite.call(this, key, value); };
        Storage.prototype.removeItem = function(key) { fixture.storageCalls.push('clear'); if (fixture.failClear) throw new Error('Synthetic private clear'); return originalClear.call(this, key); };
        window.fetch = async (input, init = {}) => {
          const path = new URL(String(input), location.href).pathname;
          if (!path.startsWith('/api/')) return originalFetch(input, init);
          fixture.calls.push({ path, method: init.method || 'GET' });
          const json = (body, status = 200) => new Response(JSON.stringify(body), { status });
          if (path === '/api/auth/me') return json(user);
          if (path === '/api/auth/login') return json({ accessToken: 'synthetic-new', user });
          if (path === '/api/auth/logout') return json({ revoked: true });
          if (path === '/api/cases/assigned') return json([]);
          if (path === '/api/tasks/assigned') return json([{ id: 12, caseId: 7, title: 'Synthetic Task', completedAt: null }]);
          if (path === '/api/tasks/12/complete') return json({}, fixture.reject ? 401 : 200);
          if (path === '/api/contacts/7') return json({ id: 7, shaleClientId: 1, displayName: 'Synthetic Contact', firstName: null, lastName: null, name: null, updatedAt: null, email: null, phone: null, condition: null });
          throw new Error('Unexpected fixture endpoint: ' + path);
        };
        // Fixture seeding is separate from application ownership, only for fresh documents.
        originalWrite.call(sessionStorage, 'shale-web.accessToken', 'synthetic-seed');
        fixture.failRead = scenario === 'read';
      }, { scenario });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      await page.getByLabel('Preview theme').selectOption('dark');
      assert.deepEqual(await page.evaluate(() => [window.fixture.calls.length, window.fixture.storageCalls.length]), [0, 0]);
      await page.goto(origin + (scenario === 'read' ? '/contacts/7?page=2#details' : '/my-shale'));
      if (scenario === 'read') {
        await page.getByRole('alert').filter({ hasText: 'could not read' }).waitFor();
        assert.equal(await page.evaluate(() => window.fixture.calls.length), 0);
        assert.equal(await page.getByRole('navigation').count(), 0);
        await page.evaluate(() => { window.fixture.failRead = false; });
        await page.getByRole('button', { name: 'Retry', exact: true }).press('Enter');
        await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor();
        assert.equal(page.url(), origin + '/contacts/7?page=2#details');
      } else {
        await page.getByRole('heading', { name: 'My Shale', exact: true }).waitFor();
        if (scenario === 'store') {
          await page.getByRole('button', { name: 'Logout', exact: true }).click();
          await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
          await page.evaluate(() => { window.fixture.failStore = true; });
          await page.getByLabel('Email', { exact: true }).fill('synthetic@example.invalid');
          await page.getByLabel('Password', { exact: true }).fill('synthetic-password');
          await page.getByRole('button', { name: 'Sign in', exact: true }).press('Enter');
          await page.getByRole('alert').filter({ hasText: 'could not store' }).waitFor();
          assert.equal(await page.getByRole('navigation').count(), 0);
          assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken')), null);
          await page.evaluate(() => { window.fixture.failStore = false; });
          await page.getByRole('button', { name: 'Sign in', exact: true }).press('Enter');
          await page.getByRole('heading', { name: 'My Shale', exact: true }).waitFor();
        } else {
          await page.evaluate(({ scenario }) => { window.fixture.failClear = true; window.fixture.reject = scenario === 'rejection'; }, { scenario });
          if (scenario === 'clear') await page.getByRole('button', { name: 'Logout', exact: true }).click();
          else await page.getByRole('button', { name: 'Complete', exact: true }).click();
          await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
          await page.getByRole('alert').filter({ hasText: 'reloading may restore' }).waitFor();
          assert.equal(await page.getByRole('navigation').count(), 0);
          assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') !== null), true);
          if (scenario === 'clear') await page.getByRole('status').filter({ hasText: 'server confirmed' }).waitFor();
          await page.screenshot({ path: `${out}/${scenario}-${width}.png`, fullPage: true });
        }
      }
      assert.equal(await page.getByText(/Synthetic private/).count(), 0);
      observations.push({ width, scenario, previewApiCalls: 0, previewStorageCalls: 0, passed: true });
      await context.close();
    }
    // Exercise the actual adapter in Chromium, independently of lifecycle fixtures.
    const context = await browser.newContext(); const page = await context.newPage(); await page.goto(origin + '/foundation.html');
    await page.evaluate(async () => {
      const { createBrowserCredentialStore } = await import('/src/credentialStore.ts');
      const store = createBrowserCredentialStore();
      if (store.read() !== null) throw new Error('Fresh browser store must be empty');
      store.store('synthetic.opaque+/=');
      if (sessionStorage.getItem('shale-web.accessToken') !== 'synthetic.opaque+/=' || localStorage.length !== 0) throw new Error('Storage policy changed');
      store.clear(); if (sessionStorage.getItem('shale-web.accessToken') !== null) throw new Error('Clear failed');
      const descriptor = Object.getOwnPropertyDescriptor(window, 'sessionStorage');
      Object.defineProperty(window, 'sessionStorage', { configurable: true, get() { throw new Error('Synthetic private getter'); } });
      for (const method of ['read', 'store', 'clear']) {
        const denied = createBrowserCredentialStore();
        let failed = false;
        try { method === 'store' ? denied.store('synthetic') : denied[method](); }
        catch (error) { failed = error.name === 'CredentialStorageError' && !error.message.includes('private getter'); }
        if (!failed) throw new Error('Getter denial escaped boundary');
      }
      Object.defineProperty(window, 'sessionStorage', descriptor);
    });
    observations.push({ scenario: 'actual Chromium adapter key/opaque/read/write/clear/getter denial', passed: true }); await context.close();
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ synthetic: true, browser: await browser.version(), observations, pageErrors: errors, unexpectedDestinations: unexpected }, null, 2));
    console.log(JSON.stringify({ scenarios: observations.length, pageErrors: errors.length, unexpectedDestinations: unexpected.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
