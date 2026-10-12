// Synthetic only: ignored-abort promises deliberately exercise stale-response races.
// Fixtures live outside app imports; unexpected network destinations are aborted.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/phase-3f-evidence';
(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  try {
    for (const width of [320, 1280]) for (const scenario of ['expired-read', 'revoked-write', 'stale-rejection', 'late-create', 'non-session']) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      await context.route('**/*', route => {
        const url = new URL(route.request().url());
        if (url.origin === origin && !url.pathname.startsWith('/api/')) return route.continue();
        unexpected.push(url.origin + url.pathname); return route.abort();
      });
      await context.addInitScript(({ scenario }) => {
        const originalFetch = window.fetch.bind(window), held = new Map(), calls = [];
        const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
          nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
        const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
        let login = sessionStorage.getItem('shale-web.accessToken') === 'synthetic-new', outcome = 403;
        window.fixture = { calls, release: (key, body = {}, status = 401) => held.get(key)?.(json(body, status)), outcome: value => { outcome = value; } };
        window.fetch = async (input, init = {}) => {
          const url = new URL(String(input), location.href);
          if (!url.pathname.startsWith('/api/')) return originalFetch(input, init);
          const path = url.pathname, method = init.method || 'GET';
          calls.push({ path, method }); // Never record headers, credentials or payloads.
          const hold = key => new Promise(resolve => held.set(key, resolve));
          if (path === '/api/auth/me') return json(user);
          if (path === '/api/auth/login') { login = true; return json({ accessToken: 'synthetic-new', user }); }
          if (path === '/api/auth/logout') return json({ revoked: true });
          if (path === '/api/contacts/7') return !login && scenario === 'expired-read' ? hold('read') : json({ id: 7, displayName: 'Synthetic Contact' });
          if (path === '/api/cases/assigned') return !login && scenario === 'stale-rejection' ? hold('old') : json([]);
          if (path === '/api/tasks/assigned') return json([{ id: 12, caseId: 7, title: 'Synthetic Task', completedAt: null }]);
          if (path === '/api/tasks/12/complete') {
            if (scenario === 'revoked-write') return json({}, 401);
            let recordedCalls;
      if (scenario === 'non-session') { if (outcome === 'network') throw new TypeError('Synthetic offline'); return json({}, outcome); }
          }
          if (path === '/api/contacts' && method === 'POST') return hold('create');
          if (path === '/api/validation/contact-value') return hold('validation');
          throw new Error('Unexpected synthetic endpoint: ' + path);
        };
      }, { scenario });
      const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      assert.equal(await page.evaluate(() => window.fixture.calls.length), 0, 'Preview isolation');
      await page.evaluate(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-established'));
      const target = scenario === 'expired-read' ? '/contacts/7?sort=name#profile' : scenario === 'late-create' ? '/contacts' : '/my-shale';
      await page.goto(origin + target);
      await page.getByRole('heading', { name: scenario === 'expired-read' ? 'Contact Detail' : scenario === 'late-create' ? 'Contacts' : 'My Shale', exact: true }).waitFor();
      const historyLength = await page.evaluate(() => history.length);
      async function login() {
        await page.getByLabel('Email', { exact: true }).fill('synthetic@example.invalid');
        await page.getByLabel('Password', { exact: true }).fill('synthetic-password');
        await page.getByRole('button', { name: 'Sign in', exact: true }).focus(); await page.keyboard.press('Enter');
      }
      let recordedCalls;
      if (scenario === 'non-session') {
        for (const status of [403, 409, 503, 'network']) {
          await page.evaluate(status => window.fixture.outcome(status), status);
          await page.getByRole('button', { name: 'Complete', exact: true }).click();
          await page.getByRole('alert').waitFor();
          assert.equal(page.url(), origin + target);
          assert(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') !== null));
          await page.getByRole('button', { name: 'Complete', exact: true }).waitFor({ state: 'visible' });
          await assert.doesNotReject(() => page.waitForFunction(() => !Array.from(document.querySelectorAll('button')).find(b => b.textContent === 'Completing…')));
        }
      } else {
        if (scenario === 'expired-read') await page.evaluate(() => window.fixture.release('read'));
        if (scenario === 'revoked-write') await page.getByRole('button', { name: 'Complete', exact: true }).click();
        if (scenario === 'stale-rejection') await page.getByRole('button', { name: 'Logout', exact: true }).click();
        if (scenario === 'late-create') {
          await page.getByRole('button', { name: 'New contact', exact: true }).click();
          await page.getByLabel('Display name', { exact: true }).fill('Synthetic draft');
          await page.getByLabel('Email', { exact: true }).focus(); await page.keyboard.press('Tab');
          await page.getByRole('button', { name: 'Create contact', exact: true }).click();
          await page.waitForFunction(() => window.fixture.calls.some(c => c.path === '/api/contacts'));
          await page.evaluate(() => window.fixture.release('validation'));
        }
        await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
        assert.equal(await page.getByRole('navigation').count(), 0);
        assert(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') === null));
        assert.equal(await page.evaluate(() => history.length), historyLength);
        if (scenario !== 'stale-rejection') {
          assert.equal(await page.getByRole('alert').count(), 1);
          assert.match(await page.getByRole('alert').innerText(), /session ended/);
          assert.match(await page.getByRole('alert').innerText(), /may have been saved/);
          assert(await page.getByRole('heading', { name: 'Sign in' }).evaluate(el => el === document.activeElement));
        }
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await page.screenshot({ path: `${out}/${scenario}-${width}.png`, fullPage: true });
        await login();
        const restored = scenario === 'expired-read' ? 'Synthetic Contact' : scenario === 'late-create' ? 'Contacts' : 'My Shale';
        await page.getByRole('heading', { name: restored, exact: true }).waitFor();
        assert.equal(page.url(), origin + target);
        assert.equal(await page.evaluate(() => history.length), historyLength);
        assert(await page.evaluate(() => history.state.usr === null));
        if (scenario === 'stale-rejection') await page.evaluate(() => window.fixture.release('old'));
        if (scenario === 'late-create') await page.evaluate(() => window.fixture.release('create', { id: 99, displayName: 'Old Synthetic Contact' }, 200));
        // Let late settlements run; a subsequent read of the DOM verifies no stale navigation/data.
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        assert.equal(page.url(), origin + target);
        assert.equal(await page.getByText('Old Synthetic Contact', { exact: true }).count(), 0);
        assert(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') !== null));
        const calls = await page.evaluate(() => window.fixture.calls);
        assert.equal(calls.filter(c => c.path.endsWith('/complete')).length, scenario === 'revoked-write' ? 1 : 0, 'No mutation replay');
        assert.equal(calls.filter(c => c.path === '/api/contacts').length, scenario === 'late-create' ? 1 : 0, 'No create replay');
        recordedCalls = calls;
        await page.goBack(); await page.getByLabel('Preview theme').waitFor();
        await page.goForward(); await page.getByRole('heading', { name: restored, exact: true }).waitFor();
        assert.equal(page.url(), origin + target);
      }
      observations.push({ scenario, width, passed: true, calls: recordedCalls || await page.evaluate(() => window.fixture.calls) });
      await context.close();
    }
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ synthetic: true, browser: await browser.version(), observations, errors, unexpected }, null, 2) + '\n');
    console.log(`${observations.length} synthetic browser scenarios passed; no live acceptance claimed.`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
