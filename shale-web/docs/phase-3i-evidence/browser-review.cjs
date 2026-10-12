// Isolated synthetic endpoints, ignored-abort promises; no live service/credentials/draft storage.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out = 'shale-web/docs/phase-3i-evidence';
(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const observations = [], errors = [], unexpected = [];
  try {
    for (const width of [320, 1280]) for (const scenario of ['navigation', 'pending-discard', 'pending-failure', 'success', 'validation', 'conflict', 'network', 'unusable', 'rejection', 'failed-clear rejection', 'logout', 'failed-clear logout']) {
      console.log(JSON.stringify({ width, scenario, started: true }));
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      await context.route('**/*', route => {
        const url = new URL(route.request().url());
        if (url.origin === origin && !url.pathname.startsWith('/api/')) return route.continue();
        unexpected.push(url.origin + url.pathname); return route.abort();
      });
      await context.addInitScript(() => {
        const originalFetch = window.fetch.bind(window), calls = [], held = new Map();
        const user = { authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Synthetic User', email: null,
          nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
        let detail = { id: 7, shaleClientId: 1, rowVer: 'AAAAAAAAAAE=', organizationTypeId: null,
          organizationTypeName: null, name: 'Synthetic Organization', email: 'saved@example.invalid',
          phone: 'legacy phone', phoneExtension: '12', fax: 'legacy fax', faxExtension: '34', website: null,
          address1: null, address2: null, city: null, state: null, postalCode: null, country: null,
          notes: 'Original notes', relatedCases: [] };
        const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
        window.fixture = { calls, release: (key, outcome = 'success') => {
            const request = held.get(key); if (!request) throw new Error('No held request');
            if (outcome === 'network') return request.reject(new TypeError('Synthetic network failure'));
            if (outcome === 'success') detail = { ...detail, notes: 'Authoritative saved notes', rowVer: 'AAAAAAAAAAI=' };
            request.resolve(json(outcome === 'unusable' ? {} : detail,
              outcome === 'validation' ? 400 : outcome === 'conflict' ? 409 : outcome === 'rejection' ? 401 : 200));
          },
          failClear: () => { Storage.prototype.removeItem = () => { throw new Error('Synthetic denied'); }; },
          unload: () => { const event = new Event('beforeunload', { cancelable: true }); dispatchEvent(event); return event.defaultPrevented; } };
        window.fetch = async (input, init = {}) => {
          const url = new URL(String(input), location.href);
          if (!url.pathname.startsWith('/api/')) return originalFetch(input, init);
          const path = url.pathname, method = init.method || 'GET'; calls.push({ path, method });
          if (path === '/api/auth/me') return json(user);
          if (path === '/api/auth/login') return json({ accessToken: 'synthetic-new', user });
          if (path === '/api/auth/logout') return json({ revoked: true });
          if (path === '/api/organizations/7') return json(detail);
          if (path === '/api/organizations/search') return json([detail]);
          if (path === '/api/validation/contact-value') return json({});
          if (path === '/api/v2/organizations/7') return new Promise((resolve, reject) => held.set('save', { resolve, reject }));
          if (path === '/api/tasks/assigned' || path === '/api/cases/assigned') return json([]);
          throw new Error('Unexpected fixture endpoint: ' + path);
        };
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/foundation.html'); await page.getByLabel('Preview theme').waitFor();
      assert.equal(await page.evaluate(() => window.fixture.calls.length), 0);
      await page.evaluate(() => sessionStorage.setItem('shale-web.accessToken', 'synthetic-established'));
      const target = scenario === 'navigation' ? '/organizations/7' : '/organizations/7?sort=name#profile';
      await page.goto(origin + (scenario === 'navigation' ? '/organizations' : target));
      if (scenario === 'navigation') {
        await page.getByLabel('Search organizations', { exact: true }).fill('Synthetic');
        await page.getByRole('button', { name: 'Search', exact: true }).click();
        await page.getByRole('button', { name: 'Open organization Synthetic Organization', exact: true }).click();
      }
      const edit = page.getByRole('button', { name: 'Edit organization', exact: true }); await edit.waitFor();
      if (scenario === 'navigation') {
        // Create same-document history entries through the real router before editing.
        await page.getByRole('link', { name: 'Shale home' }).click(); await page.getByRole('heading', { name: 'My Shale', exact: true }).waitFor();
        await page.goBack(); await edit.waitFor();
      }
      if (width === 1280) await page.getByLabel('Theme (this session)').selectOption('dark');
      await edit.click(); assert.equal(await page.evaluate(() => window.fixture.unload()), false);
      await page.getByLabel('Notes', { exact: true }).fill('Temporary');
      assert.equal(await page.evaluate(() => window.fixture.unload()), true);
      await page.getByLabel('Notes', { exact: true }).fill('  Original notes  ');
      assert.equal(await page.evaluate(() => window.fixture.unload()), false);
      await page.getByLabel('Notes', { exact: true }).fill('Synthetic dirty draft');
      assert.equal(await page.evaluate(() => window.fixture.unload()), true);
      const dialog = page.getByRole('dialog', { name: 'Discard organization changes?' });
      async function navigateToTasks() {
        const disclosure = page.locator('details'); if (!(await disclosure.evaluate(node => node.open))) await page.locator('summary').click();
        await page.getByRole('link', { name: 'My Tasks', exact: true }).click(); await dialog.waitFor();
      }
      const length = await page.evaluate(() => history.length);
      if (scenario === 'navigation') {
        await page.getByLabel('Fax', { exact: true }).fill('5551234');
        await page.locator('#organization-fax-extension').fill('001');
        await page.getByLabel('Address line 2', { exact: true }).fill('Draft suite');
        await navigateToTasks(); assert.equal(page.url(), origin + target);
        await page.screenshot({ path: `${out}/dialog-${width}.png`, fullPage: true });
        assert.equal(await page.evaluate(() => document.activeElement?.textContent), 'Keep editing');
        await page.keyboard.press('Shift+Tab'); assert.equal(await page.evaluate(() => document.activeElement?.textContent), 'Discard changes');
        await page.keyboard.press('Tab'); assert.equal(await page.evaluate(() => document.activeElement?.textContent), 'Keep editing');
        await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'hidden' });
        assert.equal(await page.getByLabel('Notes', { exact: true }).inputValue(), 'Synthetic dirty draft');
        assert.equal(await page.getByLabel('Fax', { exact: true }).inputValue(), '5551234');
        assert.equal(await page.locator('#organization-fax-extension').inputValue(), '001');
        assert.equal(await page.getByLabel('Address line 2', { exact: true }).inputValue(), 'Draft suite');
        assert.equal(await page.getByRole('link', { name: 'My Tasks', exact: true }).evaluate(node => node === document.activeElement), true);
        await navigateToTasks(); await page.getByRole('button', { name: 'Keep editing', exact: true }).press('Enter');
        await page.goForward(); await dialog.waitFor(); assert.equal(page.url(), origin + target);
        await page.getByRole('button', { name: 'Keep editing', exact: true }).press('Space');
        await navigateToTasks(); await page.getByRole('button', { name: 'Discard changes', exact: true }).press('Enter');
        await page.getByRole('heading', { name: 'Tasks', exact: true }).waitFor();
        assert.equal(await page.evaluate(() => history.length), length);
        await page.goBack(); await edit.waitFor(); assert.equal(page.url(), origin + target);
        await edit.click(); await page.getByLabel('Notes', { exact: true }).fill('Synthetic dirty draft');
        await page.goBack(); await dialog.waitFor(); assert.equal(page.url(), origin + target);
        await page.getByRole('button', { name: 'Keep editing', exact: true }).click();
        await page.getByRole('button', { name: 'Cancel', exact: true }).click(); await dialog.waitFor();
        await page.getByRole('button', { name: 'Discard changes', exact: true }).click(); await edit.waitFor();
        assert.equal(await page.evaluate(() => window.fixture.unload()), false);
      } else {
        await page.getByRole('button', { name: 'Save organization', exact: true }).click();
        await page.waitForFunction(() => window.fixture.calls.some(call => call.method === 'PATCH'));
        if (['success', 'validation', 'conflict', 'network', 'unusable'].includes(scenario)) {
          await navigateToTasks();
          await page.evaluate(outcome => window.fixture.release('save', outcome), scenario);
          if (scenario === 'success') {
            await edit.waitFor(); await dialog.waitFor({ state: 'hidden' });
            assert.equal(page.url(), origin + target); await edit.click();
            assert.equal(await page.getByLabel('Notes', { exact: true }).inputValue(), 'Authoritative saved notes');
            assert.equal(await page.evaluate(() => window.fixture.unload()), false);
            await page.getByRole('button', { name: 'Cancel', exact: true }).click();
          } else {
            await page.getByRole('alert').waitFor();
            assert.equal(await dialog.count(), 1);
            await page.getByRole('button', { name: 'Keep editing', exact: true }).click();
            assert.equal(await page.getByLabel('Notes', { exact: true }).inputValue(), 'Synthetic dirty draft');
            assert.equal(await page.evaluate(() => window.fixture.unload()), true);
            await page.getByRole('button', { name: 'Cancel', exact: true }).click(); await dialog.waitFor();
            await page.getByRole('button', { name: 'Discard changes', exact: true }).click(); await edit.waitFor();
          }
        } else if (scenario === 'pending-discard' || scenario === 'pending-failure') {
          await navigateToTasks(); assert(await dialog.textContent().then(text => text.includes('may already have committed')));
          await page.getByRole('button', { name: 'Discard changes', exact: true }).click();
          await page.getByRole('heading', { name: 'Tasks', exact: true }).waitFor();
          await page.evaluate(outcome => window.fixture.release('save', outcome), scenario === 'pending-failure' ? 'validation' : 'success'); assert.equal(page.url(), origin + '/tasks');
          assert.equal(await page.getByRole('alert').count(), 0);
        } else {
          if (scenario.startsWith('failed-clear')) await page.evaluate(() => window.fixture.failClear());
          if (scenario.endsWith('rejection')) {
            await navigateToTasks(); await page.evaluate(() => window.fixture.release('save', 'rejection'));
          } else await page.getByRole('button', { name: 'Logout', exact: true }).click();
          await page.getByRole('heading', { name: 'Sign in', exact: true }).waitFor();
          assert.equal(await dialog.count(), 0); assert.equal(await page.getByRole('form', { name: 'Edit organization' }).count(), 0);
          assert.equal(await page.evaluate(() => sessionStorage.getItem('shale-web.accessToken') !== null), scenario.startsWith('failed-clear'));
          if (scenario.startsWith('failed-clear')) await page.getByRole('alert').filter({ hasText: 'reloading may restore' }).waitFor();
          assert.equal(await page.evaluate(() => window.fixture.unload()), false);
          assert.equal(await page.evaluate(() => history.length), length);
          await page.getByLabel('Email', { exact: true }).fill('synthetic@example.invalid');
          await page.getByLabel('Password', { exact: true }).fill('synthetic-password');
          await page.getByRole('button', { name: 'Sign in', exact: true }).click();
          await page.getByRole('heading', { name: scenario.endsWith('logout') ? 'My Shale' : 'Synthetic Organization', exact: true }).waitFor();
          await page.evaluate(() => window.fixture.release('save'));
          assert.equal(page.url(), origin + (scenario.endsWith('logout') ? '/my-shale' : target));
          assert.equal(await page.getByRole('form', { name: 'Edit organization' }).count(), 0);
        }
        assert.equal(await page.evaluate(() => window.fixture.calls.filter(call => call.method === 'PATCH').length), 1);
      }
      assert.equal(await page.evaluate(() => window.fixture.unload()), false);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
      await page.screenshot({ path: `${out}/${scenario}-${width}.png`, fullPage: true });
      observations.push({ width, scenario, passed: true }); await context.close();
    }
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
    fs.writeFileSync(`${out}/browser-observations.json`, JSON.stringify({ browser: await browser.version(), synthetic: true, observations, errors, unexpected }, null, 2) + '\n');
    console.log(JSON.stringify({ scenarios: observations.length, errors, unexpected }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
