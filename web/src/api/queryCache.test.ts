import { describe, expect, it, vi } from 'vitest';
import { QueryCache, queryKey } from './queryCache';

describe('query cache', () => {
  it('canonicalizes parameters so key order does not fragment the cache', () => {
    const first = queryKey('identity-a', 'listFiles', { path: 'plugins', limit: 25 });
    const second = queryKey('identity-a', 'listFiles', { limit: 25, path: 'plugins' });
    expect(first).toBe(second);
    expect(first).not.toContain('Bearer');
    expect(first).toBe('identity-a|listFiles|[["limit","25"],["path","plugins"]]');
  });

  it('deduplicates concurrent loads and reuses a fresh entry', async () => {
    const loader = vi.fn().mockResolvedValue({ entries: ['one'] });
    const cache = new QueryCache({ now: () => 1_000 });

    const [first, second] = await Promise.all([
      cache.read('identity-a', 'listFiles', { path: 'plugins' }, loader),
      cache.read('identity-a', 'listFiles', { path: 'plugins' }, loader),
    ]);
    expect(first).toBe(second);
    expect(loader).toHaveBeenCalledTimes(1);

    await cache.read('identity-a', 'listFiles', { path: 'plugins' }, loader);
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it('expires entries and refreshes after the TTL', async () => {
    let now = 1_000;
    const cache = new QueryCache({ now: () => now, ttlMs: 100 });
    const loader = vi.fn().mockResolvedValueOnce('first').mockResolvedValueOnce('second');

    expect(await cache.read('identity-a', 'getServer', {}, loader)).toBe('first');
    now = 1_101;
    expect(cache.peek('identity-a', 'getServer')).toBeNull();
    expect(await cache.read('identity-a', 'getServer', {}, loader)).toBe('second');
  });

  it('never shares entries between opaque identity scopes', async () => {
    const cache = new QueryCache();
    await cache.read('identity-a', 'getServer', {}, async () => 'a');
    await cache.read('identity-b', 'getServer', {}, async () => 'b');

    expect(cache.size).toBe(2);
    expect(await cache.read('identity-a', 'getServer', {}, async () => 'replaced')).toBe('a');
    expect(await cache.read('identity-b', 'getServer', {}, async () => 'replaced')).toBe('b');
  });

  it('invalidates one scope without clearing others', async () => {
    const cache = new QueryCache();
    await cache.read('identity-a', 'getServer', {}, async () => 'a');
    await cache.read('identity-a', 'listFiles', { path: 'plugins' }, async () => 'files');
    await cache.read('identity-b', 'getServer', {}, async () => 'b');

    expect(cache.invalidate('identity-a|')).toBe(2);
    expect(cache.size).toBe(1);
    expect(await cache.read('identity-b', 'getServer', {}, async () => 'replaced')).toBe('b');
  });

  it('evicts the oldest entry when the bound is exceeded', async () => {
    const cache = new QueryCache({ maxEntries: 2 });
    await cache.read('identity-a', 'getServer', {}, async () => 1);
    await cache.read('identity-a', 'getDoctorReport', {}, async () => 2);
    await cache.read('identity-a', 'getCurrentIdentity', {}, async () => 3);

    expect(cache.size).toBe(2);
    expect(cache.peek('identity-a', 'getServer')).toBeNull();
    expect(cache.peek('identity-a', 'getDoctorReport')).not.toBeNull();
  });

  it('drops an in-flight entry when its loader fails', async () => {
    const cache = new QueryCache();
    await expect(
      cache.read('identity-a', 'getServer', {}, async () => {
        throw new Error('request failed');
      }),
    ).rejects.toThrow('request failed');

    expect(cache.size).toBe(0);
    expect(await cache.read('identity-a', 'getServer', {}, async () => 'recovered')).toBe(
      'recovered',
    );
  });
});
