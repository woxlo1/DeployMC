import { describe, expect, it } from 'vitest';
import { parseHashLocation, ROUTES, routeHash } from './routes';

describe('hash routing', () => {
  it('defaults an empty hash to the dashboard', () => {
    const location = parseHashLocation('');

    expect(location.route?.id).toBe('dashboard');
    expect(location.state).toBe('empty');
  });

  it('parses a known route with an explicit state preview', () => {
    const location = parseHashLocation('#/files?state=error');

    expect(location.route?.id).toBe('files');
    expect(location.state).toBe('error');
  });

  it('keeps an unknown route explicit instead of redirecting', () => {
    expect(parseHashLocation('#/does-not-exist').route).toBeNull();
  });

  it('falls back to the empty state for an invalid preview value', () => {
    expect(parseHashLocation('#/audit?state=unknown').state).toBe('empty');
  });

  it('builds hashes without rewrites for every screen', () => {
    for (const route of ROUTES) {
      expect(routeHash(route.id)).toBe(`#/${route.path}`);
      expect(routeHash(route.id, 'loading')).toBe(`#/${route.path}?state=loading`);
    }
  });
});
