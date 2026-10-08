// Advisory synthetic routing evidence. Never imported by operational code; no live API fallthrough.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/phase-3a-evidence';
const user = { authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Synthetic User', email: 'user@example.invalid', isAdmin: false, isAttorney: false };
const caseDetail = { caseId: 7, caseName: 'Synthetic Case', caseNumber: 'SYN-7', relatedContacts: [], statusHistory: [], mappedCaseDates: [] };
const taskDetail = { id: 7, caseId: 7, title: 'Synthetic Task', caseName: 'Synthetic Case', completedAt: null };
const contactDetail = { id: 7, displayName: 'Synthetic Contact' };
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.BROWSER_EXECUTABLE_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  for (const theme of ['light', 'dark']) for (const [path, heading, endpoint] of [
    ['/cases/7?page=2#details', 'Synthetic Case', '/api/cases/7'],
    ['/contacts/7?sort=name#profile', 'Synthetic Contact', '/api/contacts/7'],
    ['/tasks/7?status=open#activity', 'Synthetic Task', '/api/tasks/7'],
  ]) {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const calls = []; let loginAttempts = 0; const verification = deferred(), verificationSeen = deferred();
    let holdVerification = true; const failFirst = path.startsWith('/tasks');
    await context.route('**/*', async route => {
      const url = new URL(route.request().url()), api = url.pathname.startsWith('/api/');
      if (!api) {
        if (url.origin === origin) return route.continue();
        unexpected.push(url.origin + url.pathname); return route.abort();
      }
      const endpoint = url.pathname;
      calls.push({ path: endpoint, method: route.request().method() });
      let body = [], status = 200;
      if (endpoint === '/api/auth/login') {
        loginAttempts++;
        if (failFirst && loginAttempts === 1) { status = 401; body = { message: 'Synthetic login failure' }; }
        else body = { accessToken: 'synthetic-return-token', expiresInSeconds: 3600 };
      } else if (endpoint === '/api/auth/me') {
        if (holdVerification) { verificationSeen.resolve(); await verification.promise; }
        body = user;
      } else if (endpoint === '/api/auth/logout') body = {};
      else if (endpoint === '/api/cases/7') body = caseDetail;
      else if (endpoint === '/api/tasks/7') body = taskDetail;
      else if (endpoint === '/api/contacts/7') body = contactDetail;
      else if (!['/api/cases/assigned', '/api/tasks/assigned', '/api/cases/7/tasks', '/api/cases/7/updates'].includes(endpoint)) {
        unexpected.push(endpoint); status = 404; body = { message: 'Unexpected synthetic request' };
      }
      return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    });
    const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
    async function at(path) { await page.waitForURL(origin + path); }
    async function signIn() {
      await page.getByLabel('Email', { exact: true }).fill('user@example.invalid');
      await page.getByLabel('Password', { exact: true }).fill('synthetic-password');
      await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    }
    // A prior preview document gives a concrete Back target outside the login redirect chain.
    await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
    await page.goto(origin + path); await at('/login'); await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
    assert.equal(calls.length, 0, 'Signed-out detail must not read protected data');
    const captured = await page.evaluate(() => history.state.usr.from);
    assert.equal(captured.pathname + captured.search + captured.hash, path);
    const historyLength = await page.evaluate(() => history.length);
    if (failFirst) {
      await signIn(); await page.getByRole('alert').waitFor(); await at('/login');
      assert.deepEqual(await page.evaluate(() => history.state.usr.from), captured);
      assert.equal(calls.filter(c => c.path === endpoint).length, 0);
    }
    await signIn(); await verificationSeen.promise; await at('/login');
    assert.equal(calls.filter(c => c.path === endpoint).length, 0, 'Wait for /me before restoring detail');
    assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken')), null);
    holdVerification = false; verification.resolve(); await at(path);
    await page.getByRole('heading', { name: heading, exact: true, level: 1 }).waitFor();
    await page.getByLabel('Theme (this session)').selectOption(theme);
    assert.equal(await page.evaluate(() => history.length), historyLength, 'Login must replace history');
    assert.equal(await page.evaluate(() => history.state.usr), null, 'Return state must be consumed');
    assert(calls.some(c => c.path === endpoint));
    assert(!calls.some(c => c.path === '/api/cases/assigned'), 'No transient My Shale landing');
    await page.screenshot({ path: `${out}/${path.split('/')[1]}-${theme}.png`, fullPage: true });
    await page.goBack(); await at('/foundation.html'); await page.getByLabel('Preview theme').waitFor();
    await page.goForward(); await at(path); await page.getByRole('heading', { name: heading, exact: true, level: 1 }).waitFor();
    // Authenticated router navigation keeps normal push/back/forward and direct URL suffixes.
    await page.getByRole('link', { name: 'My Tasks', exact: true }).click(); await at('/tasks');
    await page.goBack(); await at(path); await page.getByRole('heading', { name: heading, exact: true, level: 1 }).waitFor();
    await page.goForward(); await at('/tasks');
    await page.getByRole('button', { name: 'Logout', exact: true }).click(); await at('/login');
    await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
    assert.equal(await page.evaluate(() => history.state.usr), null);
    assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken')), null);
    const reads = calls.filter(c => c.path === endpoint).length;
    await page.goBack(); await at('/login');
    await page.waitForFunction(() => !!history.state.usr?.from);
    assert.equal(calls.filter(c => c.path === endpoint).length, reads, 'Back after logout must remain signed out');
    // Fresh explicit login discards historical return state and uses the established default.
    await page.goto(origin + '/login?direct=1'); await signIn(); await at('/my-shale');
    await page.getByRole('heading', { name: 'My Shale', exact: true }).waitFor();
    // Re-seed invalid history state on a signed-out login; successful auth must stay in the app.
    await page.getByRole('button', { name: 'Logout', exact: true }).click(); await at('/login');
    await page.evaluate(() => history.replaceState({ ...history.state, usr: { from: { pathname: '//evil.invalid/cases/7' } } }, '', '/login'));
    await page.reload(); await signIn(); await at('/my-shale');
    observations.push({ path, theme, captured, restoredAfterVerification: true, failedThenSuccessful: failFirst,
      historyReplacement: true, backToPreviewWithoutLoginLoop: true, authenticatedNavigationBackForward: true,
      logoutBoundaryOnBack: true, defaultLogin: true, invalidTargetFallback: true, calls });
    await context.close();
  }
  assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
  fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ browser: await browser.version(),
    playwright: require((process.env.PLAYWRIGHT_MODULE || 'playwright') + '/package.json').version,
    fixture: 'Intercepted synthetic API only. No live authentication/backend/tenant/audit acceptance.',
    observations, errors, unexpected }, null, 2) + '\n');
  await browser.close(); console.log('PASS: six synthetic detail round trips, verification/failure/history/logout/default/invalid checks');
})().catch(error => { console.error(error); process.exit(1); });
