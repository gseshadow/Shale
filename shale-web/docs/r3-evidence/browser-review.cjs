// Intercepted synthetic records only. No credentials, live SQL/audit/session or host acceptance.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict'), fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/r3-evidence';
function fixture() {
  const original = fetch.bind(window), calls = [], held = [];
  const user = { authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Synthetic reviewer', email: null, nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
  const item = caseId => ({ caseId, caseName: 'Synthetic Match — ' + 'UnbrokenSyntheticName'.repeat(9), caseNumber: 'SYNTHETIC-36', status: { id: 1, name: 'Open', color: '#ffffa0' }, practiceArea: { id: 2, name: 'Synthetic practice' }, responsibleAttorney: { userId: 3, displayName: 'Synthetic attorney' }, primaryLegalAssistant: null, updatedAt: '2026-10-10T12:34' });
  const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  let mode = 'success';
  window.fixture = { calls, set(value) { mode = value; }, release() { held.splice(0).forEach(done => done()); } };
  window.fetch = async (input, init = {}) => {
    const url = new URL(String(input), location.href), path = url.pathname;
    if (!path.startsWith('/api/')) return original(input, init);
    calls.push({ path, method: init.method || 'GET' });
    if (path === '/api/auth/me') return json(user);
    if (path === '/api/auth/login') return json({ authenticated: true, tokenType: 'Bearer', expiresInSeconds: 3600, accessToken: 'synthetic-review-only', user });
    if (path === '/api/auth/logout') return json({ revoked: true });
    if (path.endsWith('/overview')) {
      if (mode === 'timeout') return new Promise(resolve => held.push(() => resolve(json(item(36)))));
      if (mode === 'body-timeout') return new Response(new ReadableStream({ start(controller) { held.push(() => { try { controller.enqueue(new TextEncoder().encode(JSON.stringify(item(36)))); controller.close(); } catch {} }); } }));
      if (mode === 'audit') return json({ error: 'case_audit_unavailable', message: 'Private witness' }, 503);
      if (mode === 'forbidden') return json({ error: 'case_read_denied' }, 403);
      if (mode === 'unavailable') return json({ error: 'case_request_failed' }, 404);
      if (mode === 'rejected') return json({}, 401);
      if (mode === 'oversized') return new Response(' '.repeat(8193));
      if (mode === 'malformed') return json({ ...item(36), excluded: true });
      return json(item(Number(path.split('/')[4])));
    }
    if (path === '/api/v2/cases/search-page' || path === '/api/v2/cases/assigned-page') {
      const body = init.body ? JSON.parse(init.body) : null, page = body ? body.page : Number(url.searchParams.get('page'));
      const matches = body ? Array.from({ length: 55 }, (_, i) => item(i + 36)) : [item(1)];
      return json({ items: matches.slice(page * 25, (page + 1) * 25), page, size: 25, hasMore: matches.length > (page + 1) * 25 });
    }
    throw new Error('Excluded synthetic API request');
  };
}
(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  try {
    for (const width of [320, 360, 768, 1280]) for (const theme of ['light', 'dark']) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      await context.route('**/*', route => {
        if (new URL(route.request().url()).origin === origin) return route.continue();
        unexpected.push('External request'); return route.abort();
      });
      await context.addInitScript(fixture);
      await context.addInitScript(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-review-only'));
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      assert.equal(await page.evaluate(() => fixture.calls.length), 0);
      await page.goto(origin + '/case-workspace'); await page.getByRole('heading', { name: 'Case workspace', exact: true }).waitFor();
      await page.getByLabel('Theme (this session)').selectOption(theme);
      assert.equal(await page.evaluate(() => fixture.calls.filter(c => c.path !== '/api/auth/me').length), 0);
      await page.getByRole('button', { name: 'Assigned', exact: true }).focus(); await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Open Overview for Case 1', exact: true }).waitFor();
      await page.getByRole('button', { name: 'Case-name search', exact: true }).click();
      await page.getByLabel('Case name', { exact: true }).fill('Match'); await page.keyboard.press('Enter');
      await page.getByRole('button', { name: 'Open Overview for Case 36', exact: true }).waitFor();
      await page.getByRole('button', { name: 'Next', exact: true }).focus(); await page.keyboard.press('Space');
      const open = page.getByRole('button', { name: 'Open Overview for Case 61', exact: true }); await open.waitFor();
      await page.waitForFunction(() => document.querySelector('[role="status"]')?.textContent.includes('page 2 loaded'));
      await page.screenshot({ path: `${out}/workspace-${width}-${theme}.png`, fullPage: true });
      await open.focus(); assert.equal(await open.evaluate(e => getComputedStyle(e).outlineWidth), '3px'); await page.keyboard.press('Enter');
      await page.getByRole('heading', { name: 'Case summary', exact: true }).waitFor();
      assert(await page.getByText('2026-10-10 12:34', { exact: true }).count());
      const cdp = await context.newCDPSession(page); const tree = await cdp.send('Accessibility.getFullAXTree'); await cdp.detach();
      assert(tree.nodes.some(n => n.role?.value === 'status'));
      assert(tree.nodes.some(n => n.role?.value === 'main'));
      await page.screenshot({ path: `${out}/overview-${width}-${theme}.png`, fullPage: true });
      await page.evaluate(() => fixture.set('audit')); await page.getByRole('button', { name: 'Read Overview again', exact: true }).click();
      await page.getByRole('alert').waitFor(); assert((await page.getByRole('alert').innerText()).includes('required read audit'));
      assert.equal(await page.getByRole('heading', { name: 'Case summary', exact: true }).count(), 0);
      await page.screenshot({ path: `${out}/audit-unavailable-${width}-${theme}.png`, fullPage: true });
      await page.getByRole('link', { name: 'Back to Case workspace', exact: true }).focus(); await page.keyboard.press('Enter');
      await open.waitFor(); await page.waitForFunction(() => document.activeElement?.getAttribute('data-case-id') === '61');
      assert.equal(await page.getByLabel('Case name', { exact: true }).inputValue(), 'Match');
      const before = await page.evaluate(() => fixture.calls.length);
      await page.evaluate(() => { dispatchEvent(new Event('focus')); dispatchEvent(new Event('online')); });
      await page.getByLabel('Theme (this session)').selectOption(theme === 'dark' ? 'light' : 'dark');
      assert.equal(await page.evaluate(() => fixture.calls.length), before);
      const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth); assert(noOverflow);
      const controls = await page.locator('.shale-work-page button,.shale-work-page input').evaluateAll(elements => elements.filter(e => e.getBoundingClientRect().width).map(e => ({ width: e.getBoundingClientRect().width, height: e.getBoundingClientRect().height })));
      assert(controls.every(c => c.width >= 44 && c.height >= 44));
      assert.equal(page.url(), origin + '/case-workspace');
      assert(!await page.evaluate(() => JSON.stringify(history.state).includes('Match')));
      assert(await page.evaluate(() => fixture.calls.every(c => c.path.startsWith('/api/auth/') || c.path.startsWith('/api/v2/cases/'))));
      observations.push({ width, theme, synthetic: true, noOverflow, targetMinimum44: true, nativeKeyboard: true, visibleFocus: true, backQueryPageFocus: true, loadingResultLiveRegionAndAXTree: true, auditFailureClearsEntity: true, noExcludedRequestsOrAutomaticRefetch: true });
      await context.close();
    }
    for (const mode of ['forbidden', 'unavailable', 'oversized', 'malformed', 'timeout', 'body-timeout', 'rejected']) {
      const context = await browser.newContext({ viewport: { width: 360, height: 900 } });
      await context.addInitScript(fixture); await context.addInitScript(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-review-only'));
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.clock.install();
      await page.goto(origin + '/case-workspace'); await page.getByRole('heading', { name: 'Case workspace', exact: true }).waitFor();
      await page.evaluate(mode => fixture.set(mode), mode);
      await page.goto(origin + '/case-workspace/36'); // New document fixture mode must be set before route opening below.
      await page.getByRole('heading', { name: 'Case summary', exact: true }).waitFor();
      await page.evaluate(mode => fixture.set(mode), mode); await page.getByRole('button', { name: 'Read Overview again', exact: true }).click();
      if (mode.includes('timeout')) await page.clock.runFor(8000);
      if (mode === 'rejected') {
        await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
        await page.evaluate(() => fixture.set('success'));
        await page.getByLabel('Email', { exact: true }).fill('synthetic@example.invalid'); await page.getByLabel('Password', { exact: true }).fill('synthetic-only');
        await page.getByRole('button', { name: 'Sign in', exact: true }).click(); await page.getByRole('heading', { name: 'Case summary', exact: true }).waitFor();
        assert.equal(page.url(), origin + '/case-workspace/36');
      } else {
        await page.getByRole('alert').waitFor(); assert.equal(await page.getByRole('heading', { name: 'Case summary', exact: true }).count(), 0);
        assert(!await page.getByRole('alert').innerText().then(t => t.includes('Private')));
        await page.evaluate(() => fixture.release()); await page.clock.runFor(1);
        assert.equal(await page.getByRole('heading', { name: 'Case summary', exact: true }).count(), 0);
      }
      observations.push({ mode, synthetic: true, safeFeedback: true, noStaleDetail: true, noReplay: true, explicitLoginReturn: mode === 'rejected' });
      await context.close();
    }
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ browser: browser.version(), observations, pageErrors: errors, unexpectedRequests: unexpected, liveAcceptance: false, screenReaderSpeech: 'unverified', physicalDevices: 'unverified' }, null, 2) + '\n');
    console.log(JSON.stringify({ scenarios: observations.length, errors: errors.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
