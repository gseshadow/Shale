import { generatePath, matchPath } from 'react-router-dom';

type Navigation = { label: string; order: number } | { parent: 'caseWorkspace' | 'cases' | 'tasks' | 'contacts' | 'organizations' | 'team' } | null;
type RouteDefinition = {
  kind: 'operational' | 'public' | 'fallback';
  path: string;
  navigation: Navigation;
  safeReturn: boolean;
};

// URL/presentation metadata only. ProtectedRoute and the server own session/authorization
// enforcement. No components, API clients or shell imports may enter this module.
export const routes = {
  root: { kind: 'public', path: '/', navigation: null, safeReturn: false },
  login: { kind: 'public', path: '/login', navigation: null, safeReturn: false },
  myShale: { kind: 'operational', path: '/my-shale', navigation: { label: 'My Shale', order: 0 }, safeReturn: true },
  caseWorkspace: { kind: 'operational', path: '/case-workspace', navigation: { label: 'Case workspace', order: 2.5 }, safeReturn: true },
  caseOverview: { kind: 'operational', path: '/case-workspace/:caseId', navigation: { parent: 'caseWorkspace' }, safeReturn: true },
  cases: { kind: 'operational', path: '/cases', navigation: { label: 'Cases', order: 2 }, safeReturn: true },
  caseDetail: { kind: 'operational', path: '/cases/:caseId', navigation: { parent: 'cases' }, safeReturn: true },
  tasks: { kind: 'operational', path: '/tasks', navigation: { label: 'My Tasks', order: 1 }, safeReturn: true },
  taskDetail: { kind: 'operational', path: '/tasks/:taskId', navigation: { parent: 'tasks' }, safeReturn: true },
  contacts: { kind: 'operational', path: '/contacts', navigation: { label: 'Contacts', order: 3 }, safeReturn: true },
  contactDetail: { kind: 'operational', path: '/contacts/:contactId', navigation: { parent: 'contacts' }, safeReturn: true },
  organizations: { kind: 'operational', path: '/organizations', navigation: { label: 'Organizations', order: 4 }, safeReturn: true },
  organizationDetail: { kind: 'operational', path: '/organizations/:organizationId', navigation: { parent: 'organizations' }, safeReturn: true },
  team: { kind: 'operational', path: '/team', navigation: { label: 'Team', order: 5 }, safeReturn: true },
  teamMemberDetail: { kind: 'operational', path: '/team/:userId', navigation: { parent: 'team' }, safeReturn: true },
  settings: { kind: 'operational', path: '/settings', navigation: { label: 'Settings', order: 9 }, safeReturn: true },
  fallback: { kind: 'fallback', path: '*', navigation: null, safeReturn: false },
} as const satisfies Record<string, RouteDefinition>;

export type RouteId = keyof typeof routes;
export type OperationalRouteId = { [Id in RouteId]: typeof routes[Id]['kind'] extends 'operational' ? Id : never }[RouteId];
export const operationalRouteIds = (Object.keys(routes) as RouteId[]).filter(
  (id): id is OperationalRouteId => routes[id].kind === 'operational',
);

// All existing detail routes have one required parameter. Keep numeric/string values
// unchanged, as in the original links; this builder is not entity eligibility validation.
type PathParameters<Path extends string> = Path extends `${string}:${infer Parameter}` ? Record<Parameter, string | number> : never;
export function routePath<Id extends Exclude<RouteId, 'fallback'>>(
  id: Id, ...args: PathParameters<typeof routes[Id]['path']> extends never ? [] : [PathParameters<typeof routes[Id]['path']>]
): string {
  const params = Object.fromEntries(Object.entries(args[0] ?? {}).map(([key, value]) => [key, String(value)]));
  return generatePath(routes[id].path, params);
}

// Recognition only. returnPath.ts must validate each untrusted URL component first.
// Router defaults preserve existing case-insensitive and trailing-slash semantics.
export function matchExistingRoute(pathname: string) {
  return operationalRouteIds.find(id => matchPath({ path: routes[id].path, end: true }, pathname));
}
export function isSafeReturnRoute(pathname: string): boolean {
  const id = matchExistingRoute(pathname);
  return id !== undefined && routes[id].safeReturn;
}

export type Destination = { id: string; path: string; label: string; order: number; available: boolean };
// These are presentation/preview identifiers, never route declarations or return targets.
export const unavailableDestinations = [
  { id: 'calendar', path: '/calendar', label: 'Calendar', order: 6, available: false },
  { id: 'reports', path: '/reports', label: 'Reports', order: 7, available: false },
  { id: 'search', path: '/search', label: 'Search', order: 8, available: false },
] as const;
export const routeDestinations: readonly Destination[] = [
  ...operationalRouteIds.flatMap(id => {
    const route = routes[id];
    return 'label' in route.navigation ? [{ id, path: route.path, ...route.navigation, available: true }] : [];
  }),
  ...unavailableDestinations,
].sort((a, b) => a.order - b.order);

export function destinationForRoute(pathname: string): Destination | undefined {
  const id = matchExistingRoute(pathname);
  if (!id) return undefined;
  const navigation = routes[id].navigation;
  const destinationId = 'parent' in navigation ? navigation.parent : id;
  return routeDestinations.find(destination => destination.id === destinationId);
}
