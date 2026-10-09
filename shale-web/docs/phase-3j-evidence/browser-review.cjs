// Fresh isolated synthetic fixtures. No live server-session acceptance or credential artifacts.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/phase-3j-evidence';
(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  try {
    for (const width of [320, 1280]) for (const scenario of ['login-fetch', 'login-body', 'me-fetch', 'me-body', 'shared-budget', 'credentials', 'unavailable', 'malformed', 'storage']) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      await context.route('**/*', route => {
        const url = new URL(route.request().url());
        if (url.origin === origin && !url.pathname.startsWith('/api/')) return route.continue();
        unexpected.push(url.origin + url.pathname); return route.abort();
      });
      await context.addInitScript(({ scenario }) => {
        const original = fetch.bind(window), calls = [], held = [];
        const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User', nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
        const payload = { authenticated: true, tokenType: 'Bearer', expiresInSeconds: 3600, accessToken: 'synthetic-only', user };
        const json = (body, status = 200) => new Response(JSON.stringify(body), { status });
        let mode = sessionStorage.getItem('shale-web.accessToken') ? 'success' : scenario;
        const originalSet = Storage.prototype.setItem;
        window.fixture = { calls, success() { mode = 'success'; Storage.prototype.setItem = originalSet; }, release() { held.splice(0).forEach(done => done()); } };
        if (mode === 'storage') Storage.prototype.setItem = () => { throw new Error('Synthetic private storage error'); };
        window.fetch = async (input, init = {}) => {
          const path = new URL(String(input), location.href).pathname;
          if (!path.startsWith('/api/')) return original(input, init);
          calls.push({ path, method: init.method || 'GET' });
          if (path === '/api/auth/login' || path === '/api/auth/me') {
            const login = path.endsWith('/login'), body = login ? payload : user;
            if (mode === (login ? 'login-fetch' : 'me-fetch') || mode === 'shared-budget') return new Promise(resolve => held.push(() => resolve(json(body))));
            if (mode === (login ? 'login-body' : 'me-body')) return { ok: true, status: 200, json: () => new Promise(resolve => held.push(() => resolve(body))) };
            if (login && mode === 'credentials') return json({ message: 'Do not expose synthetic body' }, 401);
            if (login && mode === 'unavailable') return json({ message: 'Do not expose synthetic body' }, 503);
            if (login && mode === 'malformed') return json({ accessToken: 'synthetic-only' });
            return json(body);
          }
          if (path === '/api/contacts/7') return json({ id: 7, displayName: 'Synthetic Contact' });
          throw new Error('Unexpected synthetic endpoint');
        };
      }, { scenario });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      const start = new Date('2026-10-09T12:00:00Z'); await page.clock.install({ time: start }); await page.clock.pauseAt(new Date(start.getTime() + 1000));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      assert.equal(await page.evaluate(() => fixture.calls.length), 0);
      const target = '/contacts/7?page=2#profile'; await page.goto(origin + target);
      await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
      const returnState = await page.evaluate(() => history.state.usr), length = await page.evaluate(() => history.length);
      const submit = page.getByRole('button', { name: 'Sign in', exact: true });
      await page.getByLabel('Email', { exact: true }).fill('synthetic@example.invalid'); await page.getByLabel('Password', { exact: true }).fill('synthetic-password');
      const stalled = scenario.includes('fetch') || scenario.includes('body') || scenario === 'shared-budget';
      await submit.focus();
      if (stalled) await page.keyboard.press('Enter');
      // Immediate failure must not settle between the first and duplicate activations.
      await page.locator('form').evaluate((form, count) => { for (let i = 0; i < count; i++) form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); }, stalled ? 2 : 3);
      if (scenario === 'shared-budget') {
        await page.clock.runFor(6000); await page.evaluate(() => fixture.release());
        await page.waitForFunction(() => fixture.calls.some(call => call.path.endsWith('/me')));
        await page.clock.runFor(1999); assert(await page.getByRole('button', { name: 'Signing in…' }).isDisabled());
        await page.clock.runFor(1);
      } else if (stalled) await page.clock.runFor(8000);
      await page.getByRole('alert').waitFor();
      const message = await page.getByRole('alert').innerText();
      assert(!message.includes('Do not expose')); assert(!message.includes('private storage'));
      assert(message.includes(scenario === 'credentials' ? 'email or password' : scenario === 'storage' ? 'could not store' : 'could not confirm sign-in'));
      if (stalled) assert(message.includes('server session may have been created'));
      assert(await submit.isEnabled()); assert.equal(await page.getByRole('navigation').count(), 0);
      assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken')), null);
      assert.deepEqual(await page.evaluate(() => history.state.usr), returnState);
      assert.equal(await page.evaluate(() => fixture.calls.filter(call => call.path.endsWith('/login')).length), 1);
      const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth); assert(noOverflow);
      await page.getByLabel('Password', { exact: true }).fill('');
      if (scenario === 'login-fetch') await page.screenshot({ path: `${out}/timeout-${width}.png`, fullPage: true });
      await page.evaluate(() => fixture.success()); await page.getByLabel('Password', { exact: true }).fill('synthetic-password');
      await submit.focus(); await page.keyboard.press('Enter');
      await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor();
      const detailReads = await page.evaluate(() => fixture.calls.filter(call => call.path === '/api/contacts/7').length);
      assert(detailReads > 0);
      await page.evaluate(() => fixture.release()); await page.clock.runFor(1000);
      assert.equal(page.url(), origin + target); assert.equal(await page.evaluate(() => history.length), length); assert.equal(await page.evaluate(() => history.state.usr), null);
      assert.equal(await page.evaluate(() => fixture.calls.filter(call => call.path.endsWith('/login')).length), 2);
      assert.equal(await page.evaluate(() => fixture.calls.filter(call => call.path === '/api/contacts/7').length), detailReads);
      await page.goBack(); await page.getByLabel('Preview theme').waitFor();
      await page.goForward(); await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor(); assert.equal(page.url(), origin + target);
      observations.push({ width, scenario, synthetic: true, deadlineMs: 8000, noOverflow, synchronousDuplicatesPrevented: true, usableFormAfterFailure: true, noAutomaticRetry: true, safeReturnAndHistory: true, lateResultDiscarded: stalled });
      await context.close();
    }
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ browser: browser.version(), playwright: require((process.env.PLAYWRIGHT_MODULE || 'playwright') + '/package.json').version, deterministicClock: true, observations, pageErrors: errors, unexpectedRequests: unexpected, liveBackendAcceptance: false }, null, 2) + '\n');
    console.log(JSON.stringify({ scenarios: observations.length, pageErrors: errors.length, unexpectedRequests: unexpected.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
