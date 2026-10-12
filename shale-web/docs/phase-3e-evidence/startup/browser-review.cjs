// Isolated synthetic fixtures; all API traffic intercepted, no live session acceptance.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/phase-3e-evidence/startup';
const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const path = '/contacts/7?sort=name#profile';
(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  try {
    for (const width of [320, 1280]) for (const action of ['retry', 'return']) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      let mode = 'stall'; const calls = [], held = [];
      await context.route('**/*', async route => {
        const url = new URL(route.request().url());
        if (!url.pathname.startsWith('/api/')) {
          if (url.origin === origin) return route.continue();
          unexpected.push(url.origin + url.pathname); return route.abort();
        }
        const endpoint = url.pathname; calls.push({ path: endpoint, method: route.request().method() });
        if (endpoint === '/api/auth/me' && mode === 'stall') { held.push(route); return; }
        const body = endpoint === '/api/auth/me' ? user : endpoint === '/api/contacts/7' ? { id: 7, displayName: 'Synthetic Contact' } : null;
        if (!body) { unexpected.push(endpoint); return route.abort(); }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      });
      const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
      const clockStart = new Date('2026-10-09T12:00:00Z');
      await page.clock.install({ time: clockStart });
      await page.clock.pauseAt(new Date(clockStart.getTime() + 1_000));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      assert.equal(calls.length, 0, 'Preview API isolation');
      await page.evaluate(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-startup'));
      await page.goto(origin + path);
      await page.getByRole('status').filter({ hasText: 'Checking your Shale session' }).waitFor();
      const historyLength = await page.evaluate(() => history.length);
      const retry = page.getByRole('button', { name: 'Retry', exact: true });
      assert(await retry.isDisabled()); assert.equal(await page.getByRole('navigation').count(), 0);
      await page.screenshot({ path: `${out}/pending-${action}-${width}.png`, fullPage: true });
      await page.clock.runFor(8_000); await page.getByRole('alert').waitFor();
      assert.equal(page.url(), origin + path);
      assert(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') !== null));
      assert.equal(await page.getByRole('navigation').count(), 0);
      assert(calls.every(c => c.path === '/api/auth/me' && c.method === 'GET'));
      const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth);
      assert(noOverflow); assert(!(await retry.isDisabled()));
      await page.screenshot({ path: `${out}/timeout-${action}-${width}.png`, fullPage: true });
      if (action === 'retry') {
        const before = calls.length; await retry.focus(); await page.keyboard.press('Enter');
        await page.getByRole('status').filter({ hasText: 'Checking your Shale session' }).waitFor();
        assert(await retry.isDisabled()); await retry.evaluate(el => { el.click(); el.click(); });
        // Advance a complete second attempt, proving a fresh deadline without automatic retries.
        await page.clock.runFor(7_999); assert(await retry.isDisabled());
        await page.clock.runFor(1); await page.getByRole('alert').waitFor();
        assert.equal(calls.length, before + 1, 'Exactly one explicit Retry');
        mode = 'success'; await retry.focus(); await page.keyboard.press('Space');
        await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor();
        assert.equal(page.url(), origin + path);
        assert.equal(await page.evaluate(() => history.length), historyLength);
        await page.screenshot({ path: `${out}/restored-${width}.png`, fullPage: true });
        await page.goBack(); await page.getByLabel('Preview theme').waitFor();
        await page.goForward(); await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor();
        assert.equal(page.url(), origin + path);
      } else {
        await page.getByRole('button', { name: 'Return to sign in', exact: true }).focus(); await page.keyboard.press('Enter');
        await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
        assert.equal(page.url(), origin + '/login');
        assert(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') === null));
        assert.equal(await page.evaluate(() => history.state.usr), null);
        assert.equal(await page.evaluate(() => history.length), historyLength);
        assert.equal(await page.getByRole('navigation').count(), 0);
        assert(calls.every(c => c.path === '/api/auth/me'));
        await page.screenshot({ path: `${out}/sign-in-${width}.png`, fullPage: true });
      }
      // Release cancelled requests after the final state; cancellation may make fulfillment impossible.
      for (const route of held) await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(user) }).catch(() => {});
      await page.clock.runFor(1_000);
      assert.equal(page.url(), origin + (action === 'retry' ? path : '/login'));
      observations.push({ width, action, synthetic: true, deadlineMs: 8000, storedBearerRetainedOnTimeout: true,
        protectedAccessBlocked: true, freshRetryDeadline: action === 'retry', duplicateRetryPrevented: action === 'retry',
        restoredExactDetail: action === 'retry', localReturn: action === 'return', historyPreserved: true, noOverflow, calls });
      await context.close();
    }
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ browser: browser.version(),
      playwright: require((process.env.PLAYWRIGHT_MODULE || 'playwright') + '/package.json').version,
      deterministicBrowserClock: true, observations, pageErrors: errors, unexpectedRequests: unexpected, liveBackendAcceptance: false }, null, 2) + '\n');
    console.log(JSON.stringify({ scenarios: observations.length, pageErrors: errors.length, unexpectedRequests: unexpected.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
