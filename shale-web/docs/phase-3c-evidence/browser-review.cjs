// Isolated synthetic logout review. No operational imports or live API fallthrough.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/phase-3c-evidence';
const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
function deferred() { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; }
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.BROWSER_EXECUTABLE_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  fs.mkdirSync(out, { recursive: true });
  try {
    for (const width of [320, 1280]) for (const outcome of ['success', 'negative', 'http', 'network', 'timeout', 'new-login']) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      const release = deferred(), seen = deferred(); const calls = [];
      await context.addInitScript(() => {
        window.reviewUnhandled = 0;
        addEventListener('unhandledrejection', () => { window.reviewUnhandled++; });
      });
      await context.route('**/*', async route => {
        const url = new URL(route.request().url()), endpoint = url.pathname;
        if (!endpoint.startsWith('/api/')) {
          if (url.origin === origin) return route.continue();
          unexpected.push(url.origin + endpoint); return route.abort();
        }
        calls.push({ path: endpoint, method: route.request().method() });
        let body = [], status = 200;
        if (endpoint === '/api/auth/logout') {
          // Compare synthetic credential without including it in observations/logs.
          assert.equal(route.request().headers().authorization, 'Bearer synthetic-established');
          seen.resolve(); await release.promise;
          if (outcome === 'network') return route.abort('failed');
          body = { revoked: outcome !== 'negative', message: 'Logged out.' };
          if (outcome === 'http') status = 503;
        } else if (endpoint === '/api/auth/me') body = user;
        else if (endpoint === '/api/auth/login') body = { accessToken: 'synthetic-new-login', expiresInSeconds: 3600 };
        else if (endpoint === '/api/contacts/7') body = { id: 7, displayName: 'Synthetic Contact' };
        else if (!['/api/cases/assigned', '/api/tasks/assigned'].includes(endpoint)) {
          unexpected.push(endpoint); return route.abort();
        }
        try { await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) }); }
        catch (error) { if (!['timeout', 'new-login'].includes(outcome)) throw error; }
      });
      const page = await context.newPage(); page.on('pageerror', e => errors.push(e.name));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      assert.equal(calls.length, 0, 'Preview remains API-free');
      await page.evaluate(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-established'));
      await page.goto(origin + '/contacts/7?sort=name#profile');
      await page.getByRole('heading', { name: 'Synthetic Contact', exact: true }).waitFor();
      const details = page.locator('details'); await details.evaluate(el => { el.open = true; });
      await page.getByRole('link', { name: 'My Tasks', exact: true }).click();
      await page.getByRole('heading', { name: 'Tasks', exact: true }).waitFor();
      await details.evaluate(el => { el.open = true; });
      const logout = page.getByRole('button', { name: 'Logout', exact: true });
      const historyLength = await page.evaluate(() => history.length);
      await logout.focus();
      if (outcome === 'success') {
        // Same event turn before unmount: synchronous activation guard.
        await logout.evaluate(el => { el.click(); el.click(); });
      } else await page.keyboard.press(width === 320 ? 'Space' : 'Enter');
      await seen.promise;
      await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
      const status = page.getByRole('status');
      await status.filter({ hasText: 'Waiting for the server' }).waitFor();
      assert.equal(page.url(), origin + '/login');
      assert.equal(await page.evaluate(() => history.state.usr), null);
      assert.equal(await page.evaluate(() => history.length), historyLength);
      assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken')), null);
      assert.equal(await page.getByRole('navigation').count(), 0);
      assert.equal(await page.evaluate(() => document.activeElement.id), 'login-title');
      assert.equal(await status.getAttribute('aria-live'), 'polite');
      assert.equal(await status.getAttribute('aria-atomic'), 'true');
      assert(await page.getByRole('button', { name: 'Sign in', exact: true }).isEnabled());
      assert.equal(calls.filter(c => c.path === '/api/auth/logout').length, 1);
      await page.keyboard.press('Tab');
      assert(await page.getByLabel('Email', { exact: true }).evaluate(el => el === document.activeElement));
      if (outcome === 'new-login') await page.keyboard.type('user@example.invalid');
      await page.keyboard.press('Tab');
      assert(await page.getByLabel('Password', { exact: true }).evaluate(el => el === document.activeElement));
      if (outcome === 'new-login') await page.keyboard.type('synthetic-password');
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Signed-out screen reflows');
      if (outcome === 'success') await page.screenshot({ path: `${out}/pending-${width}.png`, fullPage: true });
      if (outcome === 'new-login') {
        await page.keyboard.press('Tab'); await page.keyboard.press('Enter');
        await page.getByRole('heading', { name: 'My Shale', exact: true }).waitFor();
        release.resolve();
        await page.waitForTimeout(100);
        assert.equal(page.url(), origin + '/my-shale');
        assert.equal(await page.getByText(/revocation of the session used here/).count(), 0);
        assert(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') === 'synthetic-new-login'));
      } else {
        if (outcome !== 'timeout') release.resolve();
        await status.filter({ hasText: outcome === 'success' ? 'server confirmed revocation' : 'could not be confirmed' }).waitFor({ timeout: 12000 });
        assert(await page.getByLabel('Password', { exact: true }).evaluate(el => el === document.activeElement), 'Remote completion does not steal focus');
        assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken')), null);
        assert.equal(calls.filter(c => c.path === '/api/auth/logout').length, 1);
        if (['success', 'http', 'timeout'].includes(outcome)) await page.screenshot({ path: `${out}/${outcome}-${width}.png`, fullPage: true });
        release.resolve();
        const reads = calls.filter(c => c.path === '/api/contacts/7').length;
        await page.goBack(); await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
        assert.equal(calls.filter(c => c.path === '/api/contacts/7').length, reads, 'Historical protected entry cannot load while signed out');
        assert.equal(await page.getByRole('navigation').count(), 0);
      }
      assert.equal(await page.evaluate(() => window.reviewUnhandled), 0);
      observations.push({ width, outcome, oneLogoutAttempt: true, immediateLocalClear: true, protectedUnmount: true,
        noCredentialReplay: true, loginUsable: true, keyboard: true, politeAtomicStatus: true, safeHistory: true, noOverflow: true, calls });
      await context.close();
    }
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ synthetic: true, liveRevocationAcceptance: false,
      browser: browser.version(), playwright: require((process.env.PLAYWRIGHT_MODULE || 'playwright') + '/package.json').version,
      observations, pageErrors: errors, unexpectedRequests: unexpected }, null, 2) + '\n');
    console.log(JSON.stringify({ scenarios: observations.length, pageErrors: errors.length, unexpectedRequests: unexpected.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
