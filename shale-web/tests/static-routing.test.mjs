// Synthetic SWA fallback contract; not a replacement for hosted HTTP verification.
// Run: node --test shale-web/tests/static-routing.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const config = JSON.parse(readFileSync(new URL('../public/staticwebapp.config.json', import.meta.url), 'utf8'));
const excludes = config.navigationFallback.exclude;
// This is deliberately a restricted approximation of documented SWA wildcard + extension syntax.
function match(pattern, path) {
  if (pattern === path) return true;
  if (pattern.endsWith('/*') && path.startsWith(pattern.slice(0, -1))) return true;
  const extensionMatch = /^\/\*\.\{([a-z0-9,]+)\}$/.exec(pattern);
  if (extensionMatch) return extensionMatch[1].split(',').some(ext => path.endsWith('.' + ext));
  return false;
}
function excluded(path) { return excludes.some(pattern => match(pattern, path)); }

test('index fallback stays available for supported extensionless routes', () => {
  assert.equal(config.navigationFallback.rewrite, '/index.html');
  for (const path of ['/', '/login', '/my-shale', '/case-workspace', '/case-workspace/6502', '/cases/6502', '/tasks/1', '/contacts/1', '/organizations/1', '/team/1', '/settings']) {
    assert.equal(excluded(path), false, path);
  }
});
test('frontend API namespace cannot resolve to SPA HTML', () => {
  for (const path of ['/api', '/api/', '/api/health', '/api/auth/login']) assert.equal(excluded(path), true, path);
  assert.equal(excluded('/apiculture'), false);
});
test('missing emitted assets and nested static file paths cannot resolve to SPA HTML', () => {
  for (const path of ['/assets/missing.js', '/assets/nested/missing.css', '/missing.js', '/nested/missing.png', '/image.svg', '/manifest.webmanifest', '/foundation.html', '/staticwebapp.config.json', '/unknown.pdf']) assert.equal(excluded(path), true, path);
  assert.equal(excluded('/cases/6502/overview'), false);
});
test('keeps scope limited to navigationFallback without headers, API proxy or routes', () => {
  assert.deepEqual(Object.keys(config), ['navigationFallback']);
  assert.equal(config.navigationFallback.exclude.includes('/assets/*'), true);
});
