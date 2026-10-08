// Synthetic startup recovery only. No operational imports or live API fallthrough.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/phase-3b-evidence';
const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const contact = { id: 7, displayName: 'Synthetic Contact' };
const path = '/contacts/7?sort=name#profile';
function deferred() { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; }
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.BROWSER_EXECUTABLE_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  try {
    for (const width of [320, 1280]) for (const action of ['retry', 'sign-in']) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      const calls = []; let mode = 'outage'; const held = deferred(), seen = deferred();
      await context.route('**/*', async route => {
        const url = new URL(route.request().url());
        if (!url.pathname.startsWith('/api/')) {
          if (url.origin === origin) return route.continue();
          unexpected.push(url.origin + url.pathname); return route.abort();
        }
        const endpoint = url.pathname; calls.push({ path: endpoint, method: route.request().method() });
        let body = [], status = 200;
        if (endpoint === '/api/auth/me') {
          if (mode === 'outage') { status = 503; body = {}; }
          else if (mode === 'hold') { seen.resolve(); await held.promise; body = user; }
          else body = user;
        } else if (endpoint === '/api/contacts/7') body = contact;
        else if (endpoint === '/api/auth/login') body = { accessToken: 'synthetic-recovery-login', expiresInSeconds: 3600 };
        else if (!['/api/cases/assigned', '/api/tasks/assigned'].includes(endpoint)) {
          unexpected.push(endpoint); return route.abort();
        }
        return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      });
      const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      assert.equal(calls.length, 0, 'Preview stays API-free');
      // Seed a synthetic bearer without printing it or including it in observations/screenshots.
      await page.evaluate(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-recovery-startup'));
      await page.goto(origin + path); await page.getByRole('alert').waitFor();
      const historyLength = await page.evaluate(() => history.length);
      assert.equal(page.url(), origin + path);
      assert.equal(await page.getByRole('navigation').count(), 0);
      assert(calls.every(c => c.path === '/api/auth/me'), 'No protected or mutation requests during outage');
      assert(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') !== null));
      assert.equal(await page.evaluate(() => document.activeElement?.id), 'verification-title');
      const retry = page.getByRole('button', { name: 'Retry', exact: true });
      await page.keyboard.press('Tab'); assert(await retry.evaluate(el => el === document.activeElement));
      await page.screenshot({ path: `${out}/outage-${action}-${width}.png`, fullPage: true });
      const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth);
      assert(noOverflow, 'Recovery must reflow without horizontal page overflow');
      const targets = await page.getByRole('button').evaluateAll(els => els.map(el => ({
        name: el.textContent, width: el.getBoundingClientRect().width, height: el.getBoundingClientRect().height,
      })));
      assert(targets.every(t => t.width >= 44 && t.height >= 44));
      // A keyboard Retry that fails again keeps focus and retains the saved bearer.
      const beforeRepeat = calls.length; await page.keyboard.press('Enter');
      await page.getByRole('alert').waitFor(); assert.equal(calls.length, beforeRepeat + 1);
      assert(await retry.evaluate(el => el === document.activeElement));
      mode = 'hold'; const beforeRetry = calls.length;
      await page.keyboard.press('Space'); await seen.promise;
      assert(await retry.isDisabled());
      assert.equal(await retry.getAttribute('aria-busy'), 'true');
      await retry.evaluate(el => { el.click(); el.click(); });
      assert.equal(calls.length, beforeRetry + 1, 'Duplicate clicks cannot duplicate /me');
      assert(calls.every(c => c.path === '/api/auth/me'));
      if (action === 'retry') {
        mode = 'success'; held.resolve();
        await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor();
        assert.equal(page.url(), origin + path);
        assert.equal(await page.evaluate(() => history.length), historyLength);
        // The dev entry uses StrictMode, which replays existing feature mount effects.
        assert([1, 2].includes(calls.filter(c => c.path === '/api/contacts/7').length));
        assert(!calls.some(c => c.path === '/api/auth/login' || c.method !== 'GET'));
        await page.screenshot({ path: `${out}/restored-${width}.png`, fullPage: true });
        await page.goBack(); await page.getByLabel('Preview theme').waitFor();
        assert.equal(page.url(), origin + '/foundation.html', 'No added recovery/login history entry');
        await page.goForward(); await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor();
        assert.equal(page.url(), origin + path);
      } else {
        await page.keyboard.press('Tab');
        const returnButton = page.getByRole('button', { name: 'Return to sign in', exact: true });
        assert(await returnButton.evaluate(el => el === document.activeElement));
        await page.keyboard.press('Enter'); await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
        assert.equal(page.url(), origin + '/login');
        assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') === null), true);
        assert.equal(await page.evaluate(() => history.state.usr), null);
        assert.equal(await page.evaluate(() => history.length), historyLength);
        assert.equal(await page.evaluate(() => document.activeElement?.id), 'login-title');
        mode = 'success'; held.resolve();
        // Allow the held response to finish before proving it did not restore authentication.
        await page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/me');
        await page.screenshot({ path: `${out}/sign-in-${width}.png`, fullPage: true });
        assert.equal(await page.getByRole('navigation').count(), 0);
        assert(!calls.some(c => c.path === '/api/contacts/7' || c.path === '/api/auth/logout'));
        await page.getByLabel('Email', { exact: true }).fill('user@example.invalid');
        await page.getByLabel('Password', { exact: true }).fill('synthetic-password');
        await page.getByRole('button', { name: 'Sign in', exact: true }).click();
        await page.getByRole('heading', { name: 'My Shale', exact: true }).waitFor();
        assert.equal(page.url(), origin + '/my-shale', 'Explicit return drops the old detail target');
      }
      observations.push({ width, action, outageRetainsBearer: true, blocksContentAndFeatureRequests: true,
        repeatedFailureRetainsFocus: true, duplicateRetryBlocked: true, exactPathRestored: action === 'retry',
        localClearAndLateResultDiscarded: action === 'sign-in', replacementHistory: true, nativeKeyboard: true,
        noOverflow, targets, calls });
      await context.close();
    }
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ synthetic: true, browser: browser.version(),
      playwright: require((process.env.PLAYWRIGHT_MODULE || 'playwright') + '/package.json').version,
      observations, pageErrors: errors, unexpectedRequests: unexpected, liveBackendAcceptance: false }, null, 2) + '\n');
    console.log(JSON.stringify({ scenarios: observations.length, pageErrors: errors.length, unexpectedRequests: unexpected.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
