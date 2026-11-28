/**
 * Spec-driven REST client.
 *
 * The operation table, paths, parameters, response statuses and component types are generated
 * from specification/api/openapi.yaml. This module adds the runtime boundary: a bearer token is a
 * per-call argument only, errors expose stable codes instead of localized prose, and no response
 * field is used for program logic unless a caller-supplied validator accepts it.
 */
import {
  API_OPERATIONS,
  API_SPEC_SHA256,
  API_SPEC_VERSION,
  type ApiComponentName,
  type ApiComponents,
  type ApiOperationId,
  type ApiSuccessSchemaMap,
} from './generated';

export type ApiFailureReason =
  | 'unauthorized'
  | 'forbidden'
  | 'conflict'
  | 'precondition-required'
  | 'rate-limited'
  | 'server-error'
  | 'network-error'
  | 'invalid-response'
  | 'aborted';

export interface ApiFailureDetails {
  readonly status?: number;
  readonly problemCode?: string | null;
  readonly requestId?: string | null;
  readonly currentETag?: string | null;
  readonly retryAfterSeconds?: number | null;
}

interface MutableFailureDetails {
  status: number;
  problemCode: string | null;
  requestId: string | null;
  retryAfterSeconds: number | null;
  currentETag: string | null;
}

export class ApiRequestError extends Error {
  readonly reason: ApiFailureReason;
  readonly status: number | null;
  readonly problemCode: string | null;
  readonly requestId: string | null;
  readonly currentETag: string | null;
  readonly retryAfterSeconds: number | null;

  constructor(reason: ApiFailureReason, details: ApiFailureDetails = {}) {
    // Never put a token, Problem prose, file path, or raw server detail in an Error message.
    super(reason);
    this.name = 'ApiRequestError';
    this.reason = reason;
    this.status = details.status ?? null;
    this.problemCode = details.problemCode ?? null;
    this.requestId = details.requestId ?? null;
    this.currentETag = details.currentETag ?? null;
    this.retryAfterSeconds = details.retryAfterSeconds ?? null;
  }
}

export interface ApiResponse<T> {
  readonly data: T | null;
  readonly status: number;
  readonly etag: string | null;
  readonly requestId: string | null;
  readonly notModified: boolean;
}

type SchemaValue<N extends ApiComponentName> = ApiComponents[N];

export type ApiResponseFor<O extends ApiOperationId> = ApiResponse<
  ApiSuccessSchemaMap[O] extends infer N extends ApiComponentName ? SchemaValue<N> : unknown
>;

export type ApiParameterValue = string | number | boolean | null | undefined;

export interface ApiRequestOptions<T = unknown> {
  /** Supplied by the caller for one request only. Never read from storage or retained. */
  readonly token?: string;
  /** Values are accepted only for parameters declared by the generated operation descriptor. */
  readonly params?: Readonly<Record<string, ApiParameterValue>>;
  readonly body?: string | ArrayBuffer;
  readonly contentType?: string;
  readonly ifMatch?: string;
  readonly ifNoneMatch?: string;
  readonly range?: string;
  readonly idempotencyKey?: string;
  readonly signal?: AbortSignal;
  readonly validator?: (value: unknown) => value is T;
}

export interface ApiClientOptions {
  readonly baseUrl?: string;
  readonly fetch?: typeof globalThis.fetch;
}

const PROBLEM_CODE = /^[a-z][a-z0-9]*(?:\.[a-z][a-z0-9-]+)+$/;
const REQUEST_ID = /^[^\s]{8,128}$/;
const ETag_PATTERN = /^(?:W\/)?"[^"]+"$/;
const MAX_PROBLEM_BYTES = 4096;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function headerValue(headers: Headers, name: string): string | null {
  const value = headers.get(name);
  return value === null || value === '' ? null : value;
}

function boundedProblemCode(value: unknown): string | null {
  return typeof value === 'string' && PROBLEM_CODE.test(value) ? value : null;
}

function boundedRequestId(value: unknown, fallback: string | null): string | null {
  if (typeof value === 'string' && REQUEST_ID.test(value)) {
    return value;
  }
  return fallback !== null && REQUEST_ID.test(fallback) ? fallback : null;
}

function boundedRetryAfter(value: string | null): number | null {
  if (value === null || !/^\d{1,9}$/.test(value)) {
    return null;
  }
  const seconds = Number(value);
  return Number.isSafeInteger(seconds) && seconds <= 604800 ? seconds : null;
}

async function readProblem(
  response: Response,
  headerRequestId: string | null,
): Promise<MutableFailureDetails> {
  const details: MutableFailureDetails = {
    status: response.status,
    problemCode: null,
    requestId: headerRequestId,
    retryAfterSeconds: boundedRetryAfter(headerValue(response.headers, 'Retry-After')),
    currentETag: null,
  };
  const contentType = headerValue(response.headers, 'Content-Type');
  if (contentType === null || !contentType.includes('application/problem+json')) {
    return details;
  }

  // Problem prose is deliberately ignored: it is localized display text, never program input.
  try {
    const text = await response.clone().text();
    if (text.length <= MAX_PROBLEM_BYTES) {
      const value: unknown = JSON.parse(text);
      if (isRecord(value)) {
        details.problemCode = boundedProblemCode(value.code);
        details.requestId = boundedRequestId(value.requestId, headerRequestId);
      }
    }
  } catch {
    // A malformed Problem Details body must not change the classification derived from status.
  }

  return details;
}

function reasonForStatus(status: number): ApiFailureReason {
  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  if (status === 409 || status === 412) return 'conflict';
  if (status === 428) return 'precondition-required';
  if (status === 429) return 'rate-limited';
  return 'server-error';
}

function substitutePath(path: string, params: Readonly<Record<string, ApiParameterValue>>): string {
  return path.replace(/\{([A-Za-z0-9_-]+)\}/g, (_match, name: string) => {
    const value = params[name];
    if (typeof value !== 'string' && typeof value !== 'number') {
      throw new ApiRequestError('invalid-response');
    }
    const text = String(value);
    if (text === '' || text.length > 512) {
      throw new ApiRequestError('invalid-response');
    }
    return encodeURIComponent(text);
  });
}

export class ApiClient {
  private readonly baseUrl: string;
  private readonly injectedFetch: typeof globalThis.fetch | undefined;

  constructor(options: ApiClientOptions = {}) {
    this.baseUrl = options.baseUrl ?? '';
    this.injectedFetch = options.fetch;
  }

  async request<T = unknown>(
    operationId: ApiOperationId,
    options: ApiRequestOptions<T> = {},
  ): Promise<ApiResponse<T>> {
    const descriptor = API_OPERATIONS[operationId];
    if (descriptor === undefined) {
      throw new ApiRequestError('invalid-response');
    }

    const params = options.params ?? {};
    const path = substitutePath(descriptor.path, params);
    const query = new URLSearchParams();
    for (const parameter of descriptor.parameters) {
      if (parameter.in !== 'query') continue;
      const value = params[parameter.name];
      if (value === undefined || value === null || value === '') continue;
      query.append(parameter.name, String(value));
    }
    const queryText = query.toString();
    const target = queryText === '' ? path : `${path}${path.includes('?') ? '&' : '?'}${queryText}`;
    const url = this.baseUrl === '' ? target : new URL(target, this.baseUrl).toString();

    const headers = new Headers();
    headers.set('Accept', descriptor.successSchema === null ? '*/*' : 'application/json');
    headers.set('Cache-Control', 'no-store');
    if (descriptor.authenticated && typeof options.token === 'string' && options.token !== '') {
      headers.set('Authorization', `Bearer ${options.token}`);
    }

    const suppliedHeaders: Readonly<Record<string, string | undefined>> = {
      'If-Match': options.ifMatch,
      'If-None-Match': options.ifNoneMatch,
      Range: options.range,
      'Idempotency-Key': options.idempotencyKey,
    };
    for (const parameter of descriptor.parameters) {
      if (parameter.in !== 'header') continue;
      const fromParams = params[parameter.name];
      const value =
        typeof fromParams === 'string'
          ? fromParams
          : (suppliedHeaders[parameter.name] ?? undefined);
      if (typeof value === 'string' && value !== '') {
        headers.set(parameter.name, value);
      }
    }
    if (descriptor.hasRequestBody) {
      if (options.body === undefined) {
        throw new ApiRequestError('invalid-response');
      }
      headers.set('Content-Type', options.contentType ?? 'application/json');
    }

    // Resolve the transport for each call. Tests and hosts can replace the global fetch after
    // this module is imported; no token or response is retained between calls.
    const fetchImpl = this.injectedFetch ?? globalThis.fetch;
    if (typeof fetchImpl !== 'function') {
      throw new ApiRequestError('network-error');
    }

    let response: Response;
    try {
      response = await fetchImpl(url, {
        method: descriptor.method,
        headers,
        ...(options.body === undefined ? {} : { body: options.body }),
        cache: 'no-store',
        credentials: 'omit',
        referrerPolicy: 'no-referrer',
        ...(options.signal === undefined ? {} : { signal: options.signal }),
      });
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new ApiRequestError('aborted');
      }
      throw new ApiRequestError('network-error');
    }

    const requestId = boundedRequestId(headerValue(response.headers, 'X-Request-Id'), null);
    const etagRaw = headerValue(response.headers, 'ETag');
    const etag = etagRaw !== null && ETag_PATTERN.test(etagRaw) ? etagRaw : null;

    // A 304 is a successful conditional response even though Response.ok is false for 3xx.
    if (response.status === 304) {
      return { data: null, status: 304, etag, requestId, notModified: true };
    }

    if (!response.ok) {
      const details = await readProblem(response, requestId);
      details.currentETag = etag;
      throw new ApiRequestError(reasonForStatus(response.status), details);
    }

    if (response.status === 204) {
      return { data: null, status: 204, etag, requestId, notModified: false };
    }

    let data: T | null = null;
    if (descriptor.successSchema !== null) {
      let payload: unknown;
      try {
        payload = await response.json();
      } catch {
        throw new ApiRequestError('invalid-response', { status: response.status, requestId });
      }
      if (options.validator !== undefined) {
        if (!options.validator(payload)) {
          throw new ApiRequestError('invalid-response', { status: response.status, requestId });
        }
        data = payload;
      } else {
        data = payload as T;
      }
      if (
        typeof options.token === 'string' &&
        options.token.length > 0 &&
        JSON.stringify(data).includes(options.token)
      ) {
        throw new ApiRequestError('invalid-response', { status: response.status, requestId });
      }
    }

    return { data, status: response.status, etag, requestId, notModified: false };
  }
}

export const apiClient = new ApiClient();

export const CLIENT_SPEC = Object.freeze({
  version: API_SPEC_VERSION,
  sha256: API_SPEC_SHA256,
});
