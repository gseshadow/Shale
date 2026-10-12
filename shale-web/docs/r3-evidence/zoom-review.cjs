// Native browser zoom in an isolated synthetic-only Chromium profile; no live acceptance.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs'), assert = require('node:assert/strict');
const out = 'shale-web/docs/r3-evidence';
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
(async () => {
  const context = await chromium.launchPersistentContext(process.env.ZOOM_PROFILE || '/tmp/shale-r3-zoom-profile', {
    executablePath: process.env.BROWSER_EXECUTABLE_PATH || '/usr/bin/chromium', headless: true,
    args: ['--no-sandbox'], viewport: { width: 1280, height: 900 },
  });
  const errors = [], records = [], calls = [];
  const item = { caseId: 36, caseName: 'Synthetic Match — ' + 'UnbrokenSyntheticName'.repeat(9), caseNumber: null,
    status: { id: 1, name: 'Open', color: '#ffffa0' }, practiceArea: null, responsibleAttorney: null,
    primaryLegalAssistant: null, updatedAt: '2026-10-10T12:34' };
  await context.addInitScript(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-review-only'));
  await context.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    calls.push(path);
    if (path === '/api/auth/me') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Synthetic reviewer', email: null, nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null }) });
    if (path === '/api/v2/cases/search-page') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: [item], page: 0, size: 25, hasMore: false }) });
    if (path === '/api/v2/cases/36/overview') return route.fulfill({ contentType: 'application/json', body: JSON.stringify(item) });
    throw new Error('Excluded synthetic zoom API request');
  });
  try {
    const settings = await context.newPage(); await settings.goto('chrome://settings/appearance');
    await settings.locator('#zoomLevel').selectOption({ label: '100%' });
    const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
    await page.goto(origin + '/case-workspace'); await page.getByLabel('Case name', { exact: true }).waitFor();
    const baseline = await page.evaluate(() => ({ dpr: devicePixelRatio, width: innerWidth, height: innerHeight }));
    for (const zoom of [200, 400]) {
      await settings.locator('#zoomLevel').selectOption({ label: zoom + '%' });
      for (const theme of ['light', 'dark']) {
        await page.goto(origin + '/case-workspace'); await page.getByLabel('Case name', { exact: true }).waitFor();
        await page.getByLabel('Theme (this session)').selectOption(theme);
        const geometry = await page.evaluate(() => ({ dpr: devicePixelRatio, width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth }));
        assert.equal(geometry.dpr, baseline.dpr * zoom / 100); assert.equal(geometry.width, Math.round(baseline.width * 100 / zoom));
        assert.equal(geometry.width, geometry.scrollWidth);
        await page.getByLabel('Case name', { exact: true }).fill('Match'); await page.keyboard.press('Enter');
        const open = page.getByRole('button', { name: 'Open Overview for Case 36', exact: true }); await open.waitFor(); await open.focus();
        assert.equal(await open.evaluate(e => getComputedStyle(e).outlineWidth), '3px'); await page.keyboard.press('Enter');
        await page.getByRole('heading', { name: 'Case summary', exact: true }).waitFor();
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), geometry.width);
        const cdp = await context.newCDPSession(page), metrics = await cdp.send('Page.getLayoutMetrics');
        const capture = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true,
          clip: { x: 0, y: 0, width: metrics.contentSize.width, height: metrics.contentSize.height, scale: 100 / zoom } });
        fs.writeFileSync(`${out}/zoom-${zoom}-${theme}.png`, Buffer.from(capture.data, 'base64')); await cdp.detach();
        await page.getByRole('link', { name: 'Back to Case workspace', exact: true }).focus(); await page.keyboard.press('Enter');
        await page.waitForFunction(() => document.activeElement?.getAttribute('data-case-id') === '36');
        assert.equal(await page.getByLabel('Case name', { exact: true }).inputValue(), 'Match');
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), geometry.width);
        records.push({ zoom, theme, ...geometry, noOverflow: true, nativeKeyboard: true, visibleFocus: true, backQueryFocus: true });
      }
    }
    await settings.locator('#zoomLevel').selectOption({ label: '100%' });
    assert.deepEqual(errors, []);
    fs.writeFileSync(`${out}/zoom-observations.json`, JSON.stringify({ browser: context.browser().version(),
      mechanism: 'Native Chromium Settings > Appearance > Page zoom, disposable profile, physical viewport 1280x900.',
      screenshotExport: 'CDP contentSize with scale 100/zoom exports CSS resolution without changing native browser zoom.',
      baseline, records, errors, allowedRequestsOnly: calls.every(p => p === '/api/auth/me' || p === '/api/v2/cases/search-page' || p === '/api/v2/cases/36/overview'),
      liveAcceptance: false, limits: 'Headless Chromium only; physical devices and assistive-technology speech unverified.' }, null, 2) + '\n');
    console.log(JSON.stringify({ scenarios: records.length, errors: errors.length }));
  } finally { await context.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
