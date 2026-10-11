import { describe, expect, it } from 'vitest';
import { redirectPathFrom } from './returnPath';

describe('safe router-relative login return targets', () => {
  it.each(['/case-workspace', '/case-workspace/7', '/my-shale', '/cases', '/cases/7', '/tasks', '/tasks/12', '/contacts', '/contacts/7',
    '/organizations', '/organizations/7', '/team', '/team/7', '/settings', '/cases/7/', '/CASES/7'])
  ('preserves existing protected route %s with query and hash', pathname => {
    expect(redirectPathFrom({ from: { pathname, search: '?page=2&sort=due%20date', hash: '#details' } }))
      .toBe(`${pathname}?page=2&sort=due%20date#details`);
  });
  it.each([
    undefined, null, {}, { from: null }, { from: '/cases/7' }, { from: {} },
    ...['https://evil.invalid/cases/7', 'http://shale.invalid/cases/7', '//evil.invalid/cases/7',
      'javascript:alert(1)', 'cases/7', '/\\evil.invalid', '/cases\\7', '/cases/7\n',
      '/cases/%', '/cases/%ff', '/cases/%0a', '/cases/%2f%2fevil.invalid', '/cases/%5c7',
      '/cases/../login', '/cases/%2e%2e', '/cases/.', '/login', '/LOGIN/', '/login?next=/cases/7',
      '/', '/unknown', '/calendar', '/reports', '/search', '/calendar/7', '/settings/personal', '/tasks/7/activity', '/foundation.html', '/cases/7/unknown', '/nested-base/cases/7', '/cases/7?x=1']
      .map(pathname => ({ from: { pathname } })),
    { from: { pathname: 7 } }, { from: { pathname: '/cases/7', search: null } },
    { from: { pathname: '/cases/7', hash: {} } },
    ...['page=2', '?x=1#fragment', '?bad=%', '?bad=%0a', '?bad=\\', '?bad=raw space']
      .map(search => ({ from: { pathname: '/cases/7', search } })),
    ...['details', '#bad=%', '#bad=%00', '#bad=\\']
      .map(hash => ({ from: { pathname: '/cases/7', hash } })),
  ])('falls back for invalid, external, auth-loop or undeclared target %#', state => {
    expect(redirectPathFrom(state)).toBe('/my-shale');
  });
  it('retains encoded query values and hash without interpreting them as destinations', () => {
    expect(redirectPathFrom({ from: { pathname: '/contacts/7', search: '?sort=a%2Fb&ref=https%3A%2F%2Fexample.invalid', hash: '#a%20b' } }))
      .toBe('/contacts/7?sort=a%2Fb&ref=https%3A%2F%2Fexample.invalid#a%20b');
  });
});
