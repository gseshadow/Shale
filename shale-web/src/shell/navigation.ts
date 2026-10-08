/** Presentation availability only. This registry is never an authorization decision. */
export const destinations = [
  { path: '/my-shale', label: 'My Shale', available: true },
  { path: '/tasks', label: 'My Tasks', available: true },
  { path: '/cases', label: 'Cases', available: true },
  { path: '/contacts', label: 'Contacts', available: true },
  { path: '/organizations', label: 'Organizations', available: true },
  { path: '/team', label: 'Team', available: true },
  { path: '/calendar', label: 'Calendar', available: false },
  { path: '/reports', label: 'Reports', available: false },
  { path: '/search', label: 'Search', available: false },
  { path: '/settings', label: 'Settings', available: true },
] as const;
export function activeDestination(path: string) {
  return destinations.find(item => path === item.path || path.startsWith(`${item.path}/`));
}
export function foundationUrl(path: string) {
  return `/foundation.html?destination=${encodeURIComponent(path)}`;
}
