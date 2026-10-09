import { matchPath } from 'react-router-dom';
import { destinationForRoute, unavailableDestinations } from '../app/routeRegistry';
import type { NavigationAdapter } from '../shell/navigation';

export function foundationUrl(path: string) {
  return `/foundation.html?destination=${encodeURIComponent(path)}`;
}
export const previewNavigation: NavigationAdapter = {
  linkTo: foundationUrl,
  activeDestination: path => destinationForRoute(path) ?? unavailableDestinations.find(
    item => matchPath({ path: item.path, end: true }, path),
  ),
};
