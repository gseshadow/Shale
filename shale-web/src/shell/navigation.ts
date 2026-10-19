import type { Destination } from '../app/routeRegistry';

export { routeDestinations as destinations, destinationForRoute as activeDestination } from '../app/routeRegistry';

/** Only the isolated synthetic preview supplies an adapter. Never grants authority. */
export type NavigationAdapter = {
  linkTo: (path: string) => string;
  activeDestination: (path: string) => Destination | undefined;
};
