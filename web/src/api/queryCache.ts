/**
 * Bounded in-memory query cache.
 *
 * Keys are derived from an opaque identity scope, a generated operation id, and declared request
 * parameters. A bearer token is never part of a key and never stored. Entries expire, the cache is
 * bounded, and each scope can be invalidated independently so one identity cannot observe another
 * identity's cached representation.
 */
import type { ApiParameterValue } from './client';
import type { ApiOperationId } from './generated';

export interface QueryCacheOptions {
  readonly maxEntries?: number;
  readonly ttlMs?: number;
  readonly now?: () => number;
}

export interface CachedQuery<T> {
  readonly data: T;
  readonly etag: string | null;
  readonly storedAt: number;
  readonly expiresAt: number;
}

interface MutableEntry<T> extends CachedQuery<T> {
  readonly key: string;
}

const DEFAULT_MAX_ENTRIES = 128;
const DEFAULT_TTL_MS = 30_000;
const MAX_SCOPE_LENGTH = 320;

function canonicalParams(params: Readonly<Record<string, ApiParameterValue>>): string {
  const entries = Object.entries(params)
    .filter((entry): entry is [string, string | number | boolean] => {
      const value = entry[1];
      return value !== undefined && value !== null && value !== '';
    })
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, value]) => [name, String(value)] as const);
  return JSON.stringify(entries);
}

export function queryKey(
  scope: string,
  operationId: ApiOperationId,
  params: Readonly<Record<string, ApiParameterValue>> = {},
): string {
  if (scope.length === 0 || scope.length > MAX_SCOPE_LENGTH) {
    throw new Error('A query cache scope must be a bounded opaque identity id');
  }
  return `${scope}|${operationId}|${canonicalParams(params)}`;
}

export class QueryCache {
  private readonly entries = new Map<string, MutableEntry<unknown>>();
  private readonly inflight = new Map<string, Promise<unknown>>();
  private readonly maxEntries: number;
  private readonly ttlMs: number;
  private readonly now: () => number;

  constructor(options: QueryCacheOptions = {}) {
    this.maxEntries = options.maxEntries ?? DEFAULT_MAX_ENTRIES;
    this.ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
    this.now = options.now ?? Date.now;
    if (!Number.isInteger(this.maxEntries) || this.maxEntries < 1) {
      throw new Error('maxEntries must be a positive integer');
    }
    if (!Number.isFinite(this.ttlMs) || this.ttlMs < 1) {
      throw new Error('ttlMs must be positive');
    }
  }

  get size(): number {
    return this.entries.size;
  }

  peek<T>(
    scope: string,
    operationId: ApiOperationId,
    params: Readonly<Record<string, ApiParameterValue>> = {},
  ): CachedQuery<T> | null {
    const key = queryKey(scope, operationId, params);
    const entry = this.entries.get(key);
    if (entry === undefined || entry.expiresAt <= this.now()) {
      if (entry !== undefined) this.entries.delete(key);
      return null;
    }
    return entry as CachedQuery<T>;
  }

  async read<T>(
    scope: string,
    operationId: ApiOperationId,
    params: Readonly<Record<string, ApiParameterValue>>,
    loader: () => Promise<T>,
    ttlMs: number = this.ttlMs,
  ): Promise<T> {
    const key = queryKey(scope, operationId, params);
    const cached = this.peek<T>(scope, operationId, params);
    if (cached !== null) {
      return cached.data;
    }

    const pending = this.inflight.get(key);
    if (pending !== undefined) {
      return pending as Promise<T>;
    }

    const request = loader().then(
      (data) => {
        this.inflight.delete(key);
        const timestamp = this.now();
        this.entries.set(key, {
          key,
          data,
          etag: null,
          storedAt: timestamp,
          expiresAt: timestamp + ttlMs,
        });
        while (this.entries.size > this.maxEntries) {
          const oldest = this.entries.keys().next();
          if (oldest.done === true) break;
          this.entries.delete(oldest.value);
        }
        return data;
      },
      (error: unknown) => {
        this.inflight.delete(key);
        throw error;
      },
    );
    this.inflight.set(key, request);
    return request;
  }

  invalidate(prefix: string): number {
    let removed = 0;
    for (const [key] of this.entries) {
      if (key.startsWith(prefix)) {
        this.entries.delete(key);
        removed += 1;
      }
    }
    return removed;
  }

  clear(): number {
    const removed = this.entries.size;
    this.entries.clear();
    return removed;
  }
}

export const queryCache = new QueryCache();
