import { describe, expect, it } from 'vitest';
import { matchPath } from 'react-router-dom';
import { destinationForRoute, isSafeReturnRoute, matchExistingRoute, operationalRouteIds, routeDestinations, routePath, routes } from './routeRegistry';
import { activeDestination, destinations } from '../shell/navigation';
import { foundationUrl, previewNavigation } from '../preview/navigation';

// Independent compatibility inventory: adding/changing a route requires a URL decision.
const expectedRoutes = {
  root: '/', login: '/login', myShale: '/my-shale', cases: '/cases', caseDetail: '/cases/:caseId',
  tasks: '/tasks', taskDetail: '/tasks/:taskId', contacts: '/contacts', contactDetail: '/contacts/:contactId',
  organizations: '/organizations', organizationDetail: '/organizations/:organizationId', team: '/team',
  teamMemberDetail: '/team/:userId', settings: '/settings', fallback: '*',
};
describe('canonical existing application route ownership', () => {
  it('contains exactly the existing URLs, identities and public/fallback exclusions', () => {
    expect(Object.fromEntries(Object.entries(routes).map(([id, route]) => [id, route.path]))).toEqual(expectedRoutes);
    expect(operationalRouteIds).toHaveLength(12);
    for (const id of ['root', 'login', 'fallback'] as const) {
      expect(routes[id].navigation).toBeNull(); expect(routes[id].safeReturn).toBe(false);
      expect(operationalRouteIds).not.toContain(id);
    }
    for (const id of operationalRouteIds) expect(routes[id].safeReturn).toBe(true);
    expect(new Set(Object.values(routes).map(route => route.path)).size).toBe(15);
  });
  it.each([
    ['/my-shale', 'myShale', 'My Shale'], ['/cases', 'cases', 'Cases'], ['/cases/7', 'caseDetail', 'Cases'],
    ['/tasks', 'tasks', 'My Tasks'], ['/tasks/007', 'taskDetail', 'My Tasks'],
    ['/contacts', 'contacts', 'Contacts'], ['/contacts/name', 'contactDetail', 'Contacts'],
    ['/organizations', 'organizations', 'Organizations'], ['/organizations/7', 'organizationDetail', 'Organizations'],
    ['/team', 'team', 'Team'], ['/team/7', 'teamMemberDetail', 'Team'], ['/settings', 'settings', 'Settings'],
    ['/CASES/7/', 'caseDetail', 'Cases'], ['/TASKS/', 'tasks', 'My Tasks'],
  ])('recognizes %s with the declared navigation parent and unchanged Router semantics', (path, id, label) => {
    expect(matchExistingRoute(path)).toBe(id); expect(isSafeReturnRoute(path)).toBe(true);
    expect(destinationForRoute(path)?.label).toBe(label);
    expect(activeDestination(path)).toBe(destinationForRoute(path));
  });
  it.each(['/', '/login', '/unknown', '/cases/7/overview', '/tasks/7/activity', '/settings/personal',
    '/cases-other', '/calendar', '/reports', '/search', '/calendar/7', '/foundation.html'])
  ('does not turn unavailable or undeclared path %s into a route or active destination', path => {
    expect(matchExistingRoute(path)).toBeUndefined(); expect(isSafeReturnRoute(path)).toBe(false);
    expect(activeDestination(path)).toBeUndefined();
  });
  it('derives the established navigation order and keeps only three destinations unavailable', () => {
    expect(destinations).toBe(routeDestinations);
    expect(destinations.map(item => [item.path, item.label, item.available])).toEqual([
      ['/my-shale', 'My Shale', true], ['/tasks', 'My Tasks', true], ['/cases', 'Cases', true],
      ['/contacts', 'Contacts', true], ['/organizations', 'Organizations', true], ['/team', 'Team', true],
      ['/calendar', 'Calendar', false], ['/reports', 'Reports', false], ['/search', 'Search', false], ['/settings', 'Settings', true],
    ]);
    for (const item of destinations.filter(item => !item.available)) {
      expect(Object.values(routes).some(route => route.path === item.path)).toBe(false);
      expect(previewNavigation.activeDestination(item.path)).toEqual(item);
      expect(previewNavigation.linkTo(item.path)).toBe(foundationUrl(item.path));
    }
    expect(previewNavigation.activeDestination('/calendar/unknown')).toBeUndefined();
  });
  it('builds every static URL from the same registry', () => {
    expect(routePath('root')).toBe('/'); expect(routePath('login')).toBe('/login');
    expect(routePath('myShale')).toBe('/my-shale'); expect(routePath('cases')).toBe('/cases');
    expect(routePath('tasks')).toBe('/tasks'); expect(routePath('contacts')).toBe('/contacts');
    expect(routePath('organizations')).toBe('/organizations'); expect(routePath('team')).toBe('/team');
    expect(routePath('settings')).toBe('/settings');
  });
  it('builds detail links with the original parameter names and values', () => {
    const links = [
      ['caseDetail', routePath('caseDetail', { caseId: 7 }), 'caseId', '7', '/cases/7'],
      ['taskDetail', routePath('taskDetail', { taskId: '007' }), 'taskId', '007', '/tasks/007'],
      ['contactDetail', routePath('contactDetail', { contactId: 17 }), 'contactId', '17', '/contacts/17'],
      ['organizationDetail', routePath('organizationDetail', { organizationId: 27 }), 'organizationId', '27', '/organizations/27'],
      ['teamMemberDetail', routePath('teamMemberDetail', { userId: 37 }), 'userId', '37', '/team/37'],
    ] as const;
    for (const [id, path, param, value, expected] of links) {
      expect(path).toBe(expected); expect(matchPath(routes[id].path, path)?.params[param]).toBe(value);
      expect(matchExistingRoute(path)).toBe(id);
    }
  });
});
