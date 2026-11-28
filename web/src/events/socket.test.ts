import { describe, expect, it, vi } from 'vitest';
import {
  EVENT_CHANNELS,
  SERVER_EVENT_TYPES,
  WS_ENDPOINT,
  WS_PROTOCOL_VERSION,
} from '../api/generated-events';
import { WebSocketClient, parseEnvelope, type EventEnvelope, type WebSocketLike } from './socket';

interface ScheduledJob {
  readonly callback: () => void;
  readonly delayMs: number;
  cancelled: boolean;
  done: boolean;
}

function createScheduler() {
  const jobs: ScheduledJob[] = [];
  return {
    schedule: (callback: () => void, delayMs: number) => {
      const job: ScheduledJob = { callback, delayMs, cancelled: false, done: false };
      jobs.push(job);
      return () => {
        job.cancelled = true;
      };
    },
    pending: () => jobs.filter((job) => !job.cancelled && !job.done),
    runNext: () => {
      const job = jobs.find((entry) => !entry.cancelled && !entry.done);
      if (job === undefined) {
        throw new Error('no scheduled reconnect');
      }
      job.done = true;
      job.callback();
      return job.delayMs;
    },
  };
}

class FakeSocket implements WebSocketLike {
  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: ((event: { code: number; reason: string; wasClean: boolean }) => void) | null = null;
  readonly sent: string[] = [];

  constructor(readonly url: string) {}

  open(): void {
    this.readyState = 1;
    this.onopen?.();
  }

  emit(value: unknown): void {
    this.onmessage?.({ data: typeof value === 'string' ? value : JSON.stringify(value) });
  }

  close(code = 1000, reason = 'client-disconnect'): void {
    this.readyState = 3;
    this.onclose?.({ code, reason, wasClean: code === 1000 });
  }

  send(data: string): void {
    this.sent.push(data);
  }
}

function envelope(overrides: Partial<EventEnvelope> = {}): EventEnvelope {
  return {
    id: 'evt_0123456789abcdef',
    type: 'console.line',
    sequence: 0,
    protocolVersion: WS_PROTOCOL_VERSION,
    timestamp: '2026-11-28T05:00:00Z',
    payload: { level: 'INFO', message: 'server started' },
    ...overrides,
  };
}

describe('WebSocket event envelope', () => {
  it('accepts every generated server event type', () => {
    for (const type of SERVER_EVENT_TYPES) {
      expect(parseEnvelope(JSON.stringify(envelope({ type })))).toMatchObject({ type });
    }
  });

  it('rejects malformed, unknown, and oversized envelopes', () => {
    expect(parseEnvelope('not json')).toBeNull();
    expect(parseEnvelope(JSON.stringify({ ...envelope(), type: 'custom.event' }))).toBeNull();
    expect(parseEnvelope(JSON.stringify({ ...envelope(), protocolVersion: '2' }))).toBeNull();
    expect(parseEnvelope(JSON.stringify({ ...envelope(), sequence: 1.5 }))).toBeNull();
    expect(parseEnvelope(JSON.stringify({ ...envelope(), sequence: -1 }))).toBeNull();
    expect(parseEnvelope(JSON.stringify({ ...envelope(), timestamp: '2026-11-28' }))).toBeNull();
    expect(parseEnvelope(JSON.stringify({ ...envelope(), extra: true }))).toBeNull();
    expect(
      parseEnvelope(JSON.stringify(envelope({ payload: { blob: 'x'.repeat(40_000) } }))),
    ).toBeNull();
  });
});

describe('WebSocketClient', () => {
  it('uses the generated endpoint and resubscribes after reconnect', () => {
    const scheduler = createScheduler();
    const sockets: FakeSocket[] = [];
    const factory = vi.fn((url: string) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    });
    const onEvent = vi.fn();
    const onGap = vi.fn();
    const client = new WebSocketClient({
      socketFactory: factory,
      schedule: scheduler.schedule,
      minBackoffMs: 100,
      maxBackoffMs: 100,
      maxAttempts: 3,
      onEvent,
      onGap,
    });

    client.subscribe(['console', 'files']);
    client.connect();
    expect(factory).toHaveBeenCalledWith(WS_ENDPOINT);

    sockets[0]!.open();
    expect(JSON.parse(sockets[0]!.sent[0]!)).toMatchObject({
      type: 'subscribe',
      channels: ['console', 'files'],
    });

    const first = envelope();
    sockets[0]!.emit(first);
    sockets[0]!.emit(first);
    expect(onEvent).toHaveBeenCalledTimes(1);

    sockets[0]!.close(1006, 'network');
    expect(client.getState()).toMatchObject({ state: 'backoff', attempt: 1, delayMs: 100 });
    expect(scheduler.runNext()).toBe(100);
    expect(sockets).toHaveLength(2);

    sockets[1]!.open();
    expect(JSON.parse(sockets[1]!.sent[0]!)).toMatchObject({
      type: 'subscribe',
      channels: ['console', 'files'],
    });

    // The sequence is per connection, while ids are de-duplicated across reconnects.
    sockets[1]!.emit(first);
    expect(onEvent).toHaveBeenCalledTimes(1);
    sockets[1]!.emit(envelope({ id: 'evt_second_9876543210', sequence: 0 }));
    sockets[1]!.emit(envelope({ id: 'evt_third_abcdefghijkl', sequence: 3 }));
    expect(onGap).toHaveBeenCalledWith({ lastSequence: 0, receivedSequence: 3 });
    expect(onEvent).toHaveBeenCalledTimes(3);
  });

  it('applies bounded exponential backoff and stops after the attempt limit', () => {
    const scheduler = createScheduler();
    const onFatal = vi.fn();
    const factory = vi.fn(() => {
      throw new Error('socket unavailable');
    });
    const client = new WebSocketClient({
      socketFactory: factory,
      schedule: scheduler.schedule,
      minBackoffMs: 100,
      maxBackoffMs: 400,
      maxAttempts: 3,
      onFatal,
    });

    client.connect();
    expect(scheduler.runNext()).toBe(100);
    expect(scheduler.runNext()).toBe(200);
    expect(scheduler.runNext()).toBe(400);
    expect(client.getState().state).toBe('fatal');
    expect(onFatal).toHaveBeenCalledWith(0, 'max-attempts');
    expect(factory).toHaveBeenCalledTimes(4);
  });

  it('stops reconnecting after an RBAC close code', () => {
    const scheduler = createScheduler();
    const sockets: FakeSocket[] = [];
    const onFatal = vi.fn();
    const client = new WebSocketClient({
      socketFactory: (url) => {
        const socket = new FakeSocket(url);
        sockets.push(socket);
        return socket;
      },
      schedule: scheduler.schedule,
      onFatal,
    });

    client.connect();
    sockets[0]!.open();
    sockets[0]!.close(4403, 'rbac.permission-revoked');

    expect(client.getState().state).toBe('fatal');
    expect(onFatal).toHaveBeenCalledWith(4403, 'rbac.permission-revoked');
    expect(scheduler.pending()).toHaveLength(0);
  });

  it('cancels reconnect and ignores stale events after an explicit disconnect', () => {
    const scheduler = createScheduler();
    const sockets: FakeSocket[] = [];
    const onEvent = vi.fn();
    const factory = vi.fn((url: string) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    });
    const client = new WebSocketClient({
      socketFactory: factory,
      schedule: scheduler.schedule,
      onEvent,
    });

    client.connect();
    sockets[0]!.close(1006, 'network');
    client.disconnect();

    expect(scheduler.pending()).toHaveLength(0);
    expect(() => scheduler.runNext()).toThrow('no scheduled reconnect');
    sockets[0]!.emit(envelope());
    expect(onEvent).not.toHaveBeenCalled();
    expect(factory).toHaveBeenCalledTimes(1);
  });

  it('supports unsubscribe and ping control messages from the generated contract', () => {
    const sockets: FakeSocket[] = [];
    const client = new WebSocketClient({
      socketFactory: (url) => {
        const socket = new FakeSocket(url);
        sockets.push(socket);
        return socket;
      },
    });

    client.subscribe([...EVENT_CHANNELS]);
    client.connect();
    sockets[0]!.open();
    expect(JSON.parse(sockets[0]!.sent[0]!).channels).toEqual([...EVENT_CHANNELS]);

    client.unsubscribe(['presence']);
    expect(JSON.parse(sockets[0]!.sent[1]!)).toMatchObject({
      type: 'unsubscribe',
      channels: ['presence'],
    });
  });
});
