import { SHELL_KEYS } from '../i18n/keys';
import { isViewStateKind, type ViewStateKind } from './viewState';

export const ROUTE_IDS = [
  'identity',
  'dashboard',
  'files',
  'deployments',
  'console',
  'snapshots',
  'audit',
  'tokens',
  'settings',
  'doctor',
  'about',
] as const;

export type RouteId = (typeof ROUTE_IDS)[number];

export interface RouteDefinition {
  readonly id: RouteId;
  readonly path: string;
  readonly labelKey: string;
  readonly shortcut?: string;
}

/** All screens are reachable from a hash URL so the build works on static hosts without rewrites. */
export const ROUTES: readonly RouteDefinition[] = [
  { id: 'identity', path: 'identity', labelKey: SHELL_KEYS.navIdentity, shortcut: 'alt+i' },
  { id: 'dashboard', path: 'dashboard', labelKey: SHELL_KEYS.navDashboard, shortcut: 'alt+1' },
  { id: 'files', path: 'files', labelKey: SHELL_KEYS.navFiles, shortcut: 'alt+2' },
  {
    id: 'deployments',
    path: 'deployments',
    labelKey: SHELL_KEYS.navDeployments,
    shortcut: 'alt+3',
  },
  { id: 'console', path: 'console', labelKey: SHELL_KEYS.navConsole, shortcut: 'alt+4' },
  { id: 'snapshots', path: 'snapshots', labelKey: SHELL_KEYS.navSnapshots, shortcut: 'alt+5' },
  { id: 'audit', path: 'audit', labelKey: SHELL_KEYS.navAudit, shortcut: 'alt+6' },
  { id: 'tokens', path: 'tokens', labelKey: SHELL_KEYS.navTokens, shortcut: 'alt+7' },
  { id: 'settings', path: 'settings', labelKey: SHELL_KEYS.navSettings, shortcut: 'alt+8' },
  { id: 'doctor', path: 'doctor', labelKey: SHELL_KEYS.navDoctor, shortcut: 'alt+9' },
  { id: 'about', path: 'about', labelKey: SHELL_KEYS.navAbout, shortcut: 'alt+0' },
];

export interface HashLocation {
  readonly route: RouteDefinition | null;
  readonly state: ViewStateKind;
}

const ROUTE_BY_PATH = new Map(ROUTES.map((route) => [route.path, route]));
const ROUTE_BY_ID = new Map(ROUTES.map((route) => [route.id, route]));

export function defaultRoute(): RouteDefinition {
  const route = ROUTE_BY_ID.get('dashboard');
  if (route === undefined) {
    throw new Error('The dashboard route is missing from ROUTES');
  }
  return route;
}

export function routeHash(id: RouteId, state: ViewStateKind = 'empty'): string {
  const route = ROUTE_BY_ID.get(id);
  if (route === undefined) {
    throw new Error(`Unknown route: ${id}`);
  }
  return state === 'empty' ? `#/${route.path}` : `#/${route.path}?state=${state}`;
}

/**
 * Parses `#/files?state=error` into a route plus a view-state preview. An unknown path produces a
 * null route so the shell can render an explicit invalid-route view instead of silently redirecting.
 */
export function parseHashLocation(hash: string): HashLocation {
  const withoutPrefix = hash.replace(/^#\/?/, '');
  const question = withoutPrefix.indexOf('?');
  const pathPart = question === -1 ? withoutPrefix : withoutPrefix.slice(0, question);
  const query = question === -1 ? '' : withoutPrefix.slice(question + 1);
  const path = pathPart.replace(/\/+$/, '');
  const stateValue = new URLSearchParams(query).get('state');

  return {
    route: path === '' ? defaultRoute() : (ROUTE_BY_PATH.get(path) ?? null),
    state: isViewStateKind(stateValue) ? stateValue : 'empty',
  };
}
