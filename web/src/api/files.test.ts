import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from './client';
import {
  basename,
  breadcrumbSegments,
  createEntry,
  deleteEntry,
  explainPermission,
  invalidateFileCache,
  isWithin,
  isValidRelativePath,
  joinPath,
  listFilesPage,
  moveEntry,
  parentPath,
  uploadEntry,
  validateName,
} from './files';
import { queryCache } from './queryCache';

const TOKEN = 'wp43-fake-token-91ac';
const REQUEST_ID = 'req_files_test01';

function response(body: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'X-Request-Id': REQUEST_ID,
    },
  });
}

function listPayload(items: unknown[] = []) {
  return { path: '.', items, nextCursor: null, total: items.length };
}

function entry(overrides: Record<string, unknown> = {}) {
  return {
    path: 'plugins/example.yml',
    kind: 'file',
    size: 12,
    revision: '"sha256:abc"',
    lastModified: '2026-11-28T00:00:00Z',
    ...overrides,
  };
}

function writeResult(path = 'plugins/example.yml') {
  return { entry: entry({ path }), validation: null };
}

function explanationPayload(permission = 'files.write', reason: string | null = 'PATH_SCOPE') {
  return {
    schemaVersion: '1.0',
    identity: { id: 'owner', kind: 'token' },
    roles: ['admin'],
    permissions: [permission],
    explain: {
      permission,
      decision: reason === null ? 'allow' : 'deny',
      reason,
      path: { requested: true, resolved: true, withinScope: false },
      token: { present: true, containsPermission: true },
      roles: { assigned: 1, granting: 1, denyRuleMatches: 0, allowRuleMatches: 1 },
    },
  };
}

afterEach(() => {
  queryCache.clear();
  vi.restoreAllMocks();
});

describe('file path guards', () => {
  it('accepts sandbox-relative paths and rejects traversal or host-shaped input', () => {
    expect(isValidRelativePath('.')).toBe(true);
    expect(isValidRelativePath('plugins/example.yml')).toBe(true);
    expect(isValidRelativePath('../secret')).toBe(false);
    expect(isValidRelativePath('/etc/passwd')).toBe(false);
    expect(isValidRelativePath('plugins\\example.yml')).toBe(false);
    expect(isValidRelativePath('C:plugins/example.yml')).toBe(false);
    expect(isValidRelativePath('plugins//example.yml')).toBe(false);
    expect(isValidRelativePath('plugins/%2e%2e/secret')).toBe(false);
  });

  it('validates names before joining them to a parent', () => {
    expect(validateName('plugin.yml')).toBe('plugin.yml');
    expect(validateName('plugin.yml ')).toBeNull();
    expect(validateName('..')).toBeNull();
    expect(validateName('CON')).toBeNull();
    expect(validateName('a/b')).toBeNull();
    expect(joinPath('.', 'plugins')).toBe('plugins');
    expect(joinPath('plugins', 'plugin.yml')).toBe('plugins/plugin.yml');
    expect(joinPath('plugins', '../secret')).toBeNull();
    expect(joinPath('../plugins', 'plugin.yml')).toBeNull();
  });

  it('derives breadcrumbs and containment without leaving the sandbox', () => {
    expect(basename('plugins/example.yml')).toBe('example.yml');
    expect(parentPath('plugins/example.yml')).toBe('plugins');
    expect(parentPath('plugins')).toBe('.');
    expect(breadcrumbSegments('a/b/c')).toEqual(['a', 'a/b', 'a/b/c']);
    expect(breadcrumbSegments('../a')).toEqual([]);
    expect(isWithin('a/b/c', 'a')).toBe(true);
    expect(isWithin('ab/c', 'a')).toBe(false);
    expect(isWithin('../a', '.')).toBe(false);
  });
});

describe('file REST access', () => {
  it('lists a bounded page and rejects malformed entries', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(response(listPayload([entry()])))
      .mockResolvedValueOnce(response(listPayload([entry({ path: '../secret' })])));
    const api = { fetch: fetchImpl };
    vi.stubGlobal('fetch', api.fetch);

    await expect(listFilesPage(TOKEN, '.')).resolves.toMatchObject({
      path: '.',
      total: 1,
      items: [{ path: 'plugins/example.yml' }],
    });
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/files?path=.&depth=1&limit=50');
    expect((init.headers as Headers).get('Authorization')).toBe(`Bearer ${TOKEN}`);

    await expect(listFilesPage(TOKEN, '.')).rejects.toMatchObject({ reason: 'invalid-response' });
  });

  it('does not send a request for an invalid local path', async () => {
    const fetchImpl = vi.fn();
    vi.stubGlobal('fetch', fetchImpl);
    await expect(listFilesPage(TOKEN, '../secret')).rejects.toBeInstanceOf(ApiRequestError);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('parses a server explanation without retaining the full identity payload', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(explanationPayload())));
    await expect(explainPermission(TOKEN, 'files.write', 'plugins')).resolves.toEqual({
      permission: 'files.write',
      decision: 'deny',
      reason: 'PATH_SCOPE',
      pathRequested: true,
      pathResolved: true,
      pathWithinScope: false,
    });
  });

  it('sends create, move, and delete operations from validated paths', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(response(writeResult('plugins/new.yml'), 201))
      .mockResolvedValueOnce(response(entry({ path: 'plugins/moved.yml' })))
      .mockResolvedValueOnce(response(null, 204));
    vi.stubGlobal('fetch', fetchImpl);

    await expect(createEntry(TOKEN, 'plugins/new.yml', 'file')).resolves.toMatchObject({
      path: 'plugins/new.yml',
    });
    await expect(moveEntry(TOKEN, 'plugins/new.yml', 'plugins/moved.yml')).resolves.toMatchObject({
      path: 'plugins/moved.yml',
    });
    await expect(deleteEntry(TOKEN, 'plugins/moved.yml')).resolves.toBeUndefined();

    const calls = fetchImpl.mock.calls as Array<[string, RequestInit]>;
    expect(calls[0]?.[1].method).toBe('POST');
    expect(calls[0]?.[1].body).toBe(
      JSON.stringify({ path: 'plugins/new.yml', kind: 'file', content: '' }),
    );
    expect(calls[1]?.[1].body).toBe(
      JSON.stringify({
        from: 'plugins/new.yml',
        to: 'plugins/moved.yml',
        overwrite: false,
      }),
    );
    expect(calls[2]?.[0]).toBe('/api/v1/files?path=plugins%2Fmoved.yml&recursive=false');
  });

  it('uploads bytes with local progress and honors cancellation', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(response(writeResult()));
    vi.stubGlobal('fetch', fetchImpl);
    const file = new File(['value: 1'], 'example.yml', { type: 'text/plain' });
    const progress: Array<{ phase: string; loaded: number; total: number }> = [];

    await expect(
      uploadEntry(TOKEN, 'plugins/example.yml', file, {
        onProgress: (value) => progress.push(value),
      }),
    ).resolves.toMatchObject({ path: 'plugins/example.yml' });

    expect(progress.at(-1)).toMatchObject({ phase: 'complete', loaded: 8, total: 8 });
    const [, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Headers).get('Content-Type')).toBe('application/octet-stream');
    expect((init.body as ArrayBuffer).byteLength).toBe(8);

    const controller = new AbortController();
    controller.abort();
    await expect(
      uploadEntry(TOKEN, 'plugins/example.yml', file, { signal: controller.signal }),
    ).rejects.toMatchObject({ reason: 'aborted' });
  });

  it('invalidates only file and explanation cache prefixes', async () => {
    await queryCache.read('owner', 'listFiles', { path: '.' }, async () => listPayload());
    await queryCache.read('owner', 'getCurrentIdentity', { explain: 'files.write' }, async () => ({
      permission: 'files.write',
    }));
    await queryCache.read('owner', 'getServer', {}, async () => ({ deployMcVersion: 'x' }));

    expect(invalidateFileCache('owner')).toBe(2);
    expect(queryCache.size).toBe(1);
  });
});
