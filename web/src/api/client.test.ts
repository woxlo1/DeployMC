import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiClient, ApiRequestError, CLIENT_SPEC } from './client';
import { API_OPERATION_IDS, API_SPEC_SHA256 } from './generated';

const TOKEN = 'wp42-fake-token-91ac';

function response(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(status === 204 || status === 304 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req_0123456789', ...headers },
  });
}

function problem(status: number, code: string): Response {
  return response(
    {
      type: 'urn:deploymc:error:test',
      title: 'Localized secret title',
      status,
      code,
      requestId: 'req_problem_1234',
      detail: TOKEN,
    },
    status,
    {
      'Content-Type': 'application/problem+json',
      ETag: '"sha256:current"',
      'Retry-After': '7',
    },
  );
}

function clientWith(fetchImpl: typeof fetch): ApiClient {
  return new ApiClient({ fetch: fetchImpl });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('generated REST client', () => {
  it('uses the generated operations, version, and source digest', () => {
    expect(API_OPERATION_IDS).toContain('getCurrentIdentity');
    expect(API_OPERATION_IDS).toContain('listFiles');
    expect(CLIENT_SPEC.sha256).toBe(API_SPEC_SHA256);
    expect(CLIENT_SPEC.version).toBe('0.1.0');
  });

  it('builds a URL only from generated parameters and sends a one-call token', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(response({ entries: [] }));
    const api = clientWith(fetchImpl);

    const result = await api.request('listFiles', {
      token: TOKEN,
      params: { path: 'plugins', limit: 25, cursor: 'abc', unexpected: 'ignored' },
    });

    expect(result.status).toBe(200);
    expect(result.requestId).toBe('req_0123456789');
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/files?path=plugins&cursor=abc&limit=25');
    expect(init.method).toBe('GET');
    expect((init.headers as Headers).get('Authorization')).toBe(`Bearer ${TOKEN}`);
    // listFiles does not declare a conditional header in the specification.
    expect((init.headers as Headers).get('If-Match')).toBeNull();
    expect(init.credentials).toBe('omit');
    expect(init.referrerPolicy).toBe('no-referrer');
  });

  it('encodes path parameters and rejects missing ones', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(response({ id: '2026-01' }));
    const api = clientWith(fetchImpl);

    await api.request('getSnapshot', { token: TOKEN, params: { id: '2026/01' } });
    expect(fetchImpl.mock.calls[0]?.[0]).toBe('/api/v1/snapshots/2026%2F01');

    await expect(api.request('getSnapshot', { token: TOKEN })).rejects.toMatchObject({
      reason: 'invalid-response',
    });
  });

  it('classifies 401 without exposing Problem prose or the token', async () => {
    const api = clientWith(vi.fn().mockResolvedValue(problem(401, 'auth.invalid-token')));

    await expect(api.request('getCurrentIdentity', { token: TOKEN })).rejects.toMatchObject({
      reason: 'unauthorized',
      status: 401,
      problemCode: 'auth.invalid-token',
      requestId: 'req_problem_1234',
    });
    try {
      await api.request('getCurrentIdentity', { token: TOKEN });
      throw new Error('expected the request to fail');
    } catch (error) {
      expect(error).toBeInstanceOf(ApiRequestError);
      expect((error as ApiRequestError).message).not.toContain(TOKEN);
      expect((error as ApiRequestError).message).not.toContain('secret');
    }
  });

  it('reports conflict with the current revision', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(problem(412, 'files.revision-conflict'));
    const api = clientWith(fetchImpl);

    await expect(
      api.request('writeFileContent', {
        token: TOKEN,
        params: { path: 'plugins/example.yml' },
        ifMatch: '"sha256:stale"',
        body: 'value: 1',
      }),
    ).rejects.toMatchObject({
      reason: 'conflict',
      status: 412,
      problemCode: 'files.revision-conflict',
      currentETag: '"sha256:current"',
    });
    const [, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Headers).get('If-Match')).toBe('"sha256:stale"');
  });

  it('maps 428 and 429 to explicit client reasons', async () => {
    const api = clientWith(
      vi
        .fn()
        .mockResolvedValueOnce(problem(428, 'request.precondition-required'))
        .mockResolvedValueOnce(problem(429, 'request.rate-limited')),
    );

    await expect(api.request('deleteFileEntry', { token: TOKEN })).rejects.toMatchObject({
      reason: 'precondition-required',
    });
    await expect(api.request('getServer', { token: TOKEN })).rejects.toMatchObject({
      reason: 'rate-limited',
      retryAfterSeconds: 7,
    });
  });

  it('uses a caller validator and rejects invalid or token-echoing payloads', async () => {
    const api = clientWith(
      vi
        .fn()
        .mockResolvedValueOnce(response({ ok: 1 }))
        .mockResolvedValueOnce(response({ id: 'viewer', secret: TOKEN })),
    );

    await expect(
      api.request('getHealth', {
        validator: (value): value is { ok: number } =>
          typeof value === 'object' && value !== null && 'ok' in value,
      }),
    ).resolves.toMatchObject({ data: { ok: 1 } });

    await expect(api.request('getCurrentIdentity', { token: TOKEN })).rejects.toMatchObject({
      reason: 'invalid-response',
    });
  });

  it('returns a not-modified result for 304 and null for 204', async () => {
    const api = clientWith(
      vi
        .fn()
        .mockResolvedValueOnce(response(null, 304, { ETag: '"sha256:current"' }))
        .mockResolvedValueOnce(response(null, 204)),
    );

    await expect(api.request('getServer', { token: TOKEN })).resolves.toMatchObject({
      data: null,
      notModified: true,
      etag: '"sha256:current"',
    });
    await expect(api.request('deleteFileEntry', { token: TOKEN })).resolves.toMatchObject({
      data: null,
      status: 204,
      notModified: false,
    });
  });

  it('maps network failures and aborts to stable reasons', async () => {
    const network = clientWith(vi.fn().mockRejectedValue(new Error('network detail')));
    await expect(network.request('getServer', { token: TOKEN })).rejects.toMatchObject({
      reason: 'network-error',
    });

    const abort = clientWith(vi.fn().mockRejectedValue(new DOMException('aborted', 'AbortError')));
    await expect(abort.request('getServer', { token: TOKEN })).rejects.toMatchObject({
      reason: 'aborted',
    });
  });
});
