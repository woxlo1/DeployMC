/**
 * WebSocket client for the generated DeployMC event contract.
 *
 * The client resubscribes after every successful open, de-duplicates envelopes by id, detects
 * sequence gaps, and uses bounded exponential backoff. It does not send bearer tokens in URLs or
 * subprotocols: native browser WebSockets cannot set an Authorization header, so authentication
 * must be ambient at the upgrade (for example a host-provisioned tunnel session). A factory hook
 * lets a host supply an authenticated socket without leaking credentials into this module.
 */
import { createRequestId } from '../app/requestId';
import {
  EVENT_CHANNELS,
  SERVER_EVENT_TYPES,
  WS_ENDPOINT,
  WS_PROTOCOL_VERSION,
  type ControlMessageType,
  type EventChannel,
  type ServerEventType,
} from '../api/generated-events';

export type SocketState = 'idle' | 'connecting' | 'connected' | 'backoff' | 'fatal';

export interface EventEnvelope {
  readonly id: string;
  readonly type: ServerEventType;
  readonly sequence: number;
  readonly protocolVersion: typeof WS_PROTOCOL_VERSION;
  readonly timestamp: string;
  readonly payload: unknown;
}

export interface EventGap {
  readonly lastSequence: number;
  readonly receivedSequence: number;
}

export interface WebSocketLike {
  readonly readyState: number;
  onopen: (() => void) | null;
  onmessage: ((event: { data: string }) => void) | null;
  onerror: (() => void) | null;
  onclose: ((event: { code: number; reason: string; wasClean: boolean }) => void) | null;
  send(data: string): void;
  close(code?: number, reason?: string): void;
}

export type SocketFactory = (url: string) => WebSocketLike;

export interface WebSocketClientOptions {
  readonly url?: string;
  readonly socketFactory?: SocketFactory;
  readonly minBackoffMs?: number;
  readonly maxBackoffMs?: number;
  readonly maxAttempts?: number;
  readonly schedule?: (callback: () => void, delayMs: number) => () => void;
  readonly onStateChange?: (state: SocketState, attempt: number, delayMs: number) => void;
  readonly onEvent?: (event: EventEnvelope) => void;
  readonly onGap?: (gap: EventGap) => void;
  readonly onInvalidEnvelope?: () => void;
  readonly onFatal?: (code: number, reason: string) => void;
}

const OPEN = 1;
const MAX_MESSAGE_LENGTH = 65_536;
const MAX_PAYLOAD_LENGTH = 32_768;
const MAX_SEEN_IDS = 512;
const DEFAULT_MIN_BACKOFF_MS = 500;
const DEFAULT_MAX_BACKOFF_MS = 30_000;
const DEFAULT_MAX_ATTEMPTS = 6;
const FATAL_CLOSE_CODES: ReadonlySet<number> = new Set([1008, 4401, 4403]);
const ENVELOPE_FIELDS: ReadonlySet<string> = new Set([
  'id',
  'type',
  'sequence',
  'protocolVersion',
  'timestamp',
  'payload',
]);
const EVENT_TYPES: ReadonlySet<string> = new Set(SERVER_EVENT_TYPES);
const CHANNELS: ReadonlySet<string> = new Set(EVENT_CHANNELS);
const UTC_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasControlCharacter(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 0x1f || code === 0x7f) {
      return true;
    }
  }
  return false;
}

function boundedId(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.length >= 8 &&
    value.length <= 128 &&
    !hasControlCharacter(value)
  );
}

function boundedTimestamp(value: unknown): value is string {
  return typeof value === 'string' && UTC_INSTANT.test(value) && Number.isFinite(Date.parse(value));
}

export function parseEnvelope(raw: string): EventEnvelope | null {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > MAX_MESSAGE_LENGTH) {
    return null;
  }

  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!isRecord(value)) {
    return null;
  }
  if (!Object.keys(value).every((key) => ENVELOPE_FIELDS.has(key))) {
    return null;
  }
  if (
    !boundedId(value.id) ||
    typeof value.type !== 'string' ||
    !EVENT_TYPES.has(value.type) ||
    !Number.isSafeInteger(value.sequence) ||
    (value.sequence as number) < 0 ||
    value.protocolVersion !== WS_PROTOCOL_VERSION ||
    !boundedTimestamp(value.timestamp) ||
    !isRecord(value.payload)
  ) {
    return null;
  }
  try {
    if (JSON.stringify(value.payload).length > MAX_PAYLOAD_LENGTH) {
      return null;
    }
  } catch {
    return null;
  }

  return {
    id: value.id,
    type: value.type as ServerEventType,
    sequence: value.sequence as number,
    protocolVersion: WS_PROTOCOL_VERSION,
    timestamp: value.timestamp,
    payload: value.payload,
  };
}

function defaultSchedule(callback: () => void, delayMs: number): () => void {
  const handle = setTimeout(callback, delayMs);
  return () => clearTimeout(handle);
}

function defaultSocketFactory(url: string): WebSocketLike {
  if (typeof WebSocket === 'undefined') {
    throw new Error('WebSocket transport is unavailable');
  }
  return new WebSocket(url) as unknown as WebSocketLike;
}

export class WebSocketClient {
  private readonly url: string;
  private readonly socketFactory: SocketFactory;
  private readonly minBackoffMs: number;
  private readonly maxBackoffMs: number;
  private readonly maxAttempts: number;
  private readonly schedule: (callback: () => void, delayMs: number) => () => void;
  private readonly onStateChange: WebSocketClientOptions['onStateChange'];
  private readonly onEvent: WebSocketClientOptions['onEvent'];
  private readonly onGap: WebSocketClientOptions['onGap'];
  private readonly onInvalidEnvelope: WebSocketClientOptions['onInvalidEnvelope'];
  private readonly onFatal: WebSocketClientOptions['onFatal'];
  private readonly desiredChannels = new Set<EventChannel>();
  private readonly seenIds = new Set<string>();
  private readonly seenOrder: string[] = [];

  private socket: WebSocketLike | null = null;
  private cancelReconnect: (() => void) | null = null;
  private state: SocketState = 'idle';
  private attempt = 0;
  private delayMs = 0;
  private generation = 0;
  private lastSequence = -1;

  constructor(options: WebSocketClientOptions = {}) {
    this.url = options.url ?? WS_ENDPOINT;
    this.socketFactory = options.socketFactory ?? defaultSocketFactory;
    this.minBackoffMs = options.minBackoffMs ?? DEFAULT_MIN_BACKOFF_MS;
    this.maxBackoffMs = options.maxBackoffMs ?? DEFAULT_MAX_BACKOFF_MS;
    this.maxAttempts = options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS;
    this.schedule = options.schedule ?? defaultSchedule;
    this.onStateChange = options.onStateChange;
    this.onEvent = options.onEvent;
    this.onGap = options.onGap;
    this.onInvalidEnvelope = options.onInvalidEnvelope;
    this.onFatal = options.onFatal;

    if (
      !Number.isInteger(this.minBackoffMs) ||
      this.minBackoffMs < 1 ||
      !Number.isInteger(this.maxBackoffMs) ||
      this.maxBackoffMs < this.minBackoffMs ||
      !Number.isInteger(this.maxAttempts) ||
      this.maxAttempts < 1
    ) {
      throw new Error('Invalid WebSocket backoff configuration');
    }
  }

  getState(): { state: SocketState; attempt: number; delayMs: number } {
    return { state: this.state, attempt: this.attempt, delayMs: this.delayMs };
  }

  subscribe(channels: readonly EventChannel[]): void {
    const added = channels.filter((channel) => {
      if (!CHANNELS.has(channel) || this.desiredChannels.has(channel)) {
        return false;
      }
      this.desiredChannels.add(channel);
      return true;
    });
    if (added.length > 0) {
      this.sendControl('subscribe', added);
    }
  }

  unsubscribe(channels: readonly EventChannel[]): void {
    const removed = channels.filter((channel) => {
      if (!this.desiredChannels.has(channel)) {
        return false;
      }
      this.desiredChannels.delete(channel);
      return true;
    });
    if (removed.length > 0) {
      this.sendControl('unsubscribe', removed);
    }
  }

  connect(): void {
    if (this.state === 'connecting' || this.state === 'connected' || this.state === 'backoff') {
      return;
    }
    this.generation += 1;
    this.attempt = 0;
    this.delayMs = 0;
    this.openSocket(this.generation);
  }

  disconnect(): void {
    this.generation += 1;
    this.cancelReconnect?.();
    this.cancelReconnect = null;
    const socket = this.socket;
    this.socket = null;
    if (socket !== null && socket.readyState < 2) {
      socket.close(1000, 'client-disconnect');
    }
    this.setState('idle', 0, 0);
  }

  private setState(state: SocketState, attempt: number, delayMs: number): void {
    this.state = state;
    this.attempt = attempt;
    this.delayMs = delayMs;
    this.onStateChange?.(state, attempt, delayMs);
  }

  private openSocket(generation: number): void {
    if (generation !== this.generation) {
      return;
    }
    this.setState('connecting', this.attempt, 0);

    let socket: WebSocketLike;
    try {
      socket = this.socketFactory(this.url);
    } catch {
      this.scheduleReconnect(generation);
      return;
    }
    this.socket = socket;

    socket.onopen = () => {
      if (generation !== this.generation) {
        return;
      }
      this.attempt = 0;
      this.delayMs = 0;
      this.lastSequence = -1;
      this.setState('connected', 0, 0);
      if (this.desiredChannels.size > 0) {
        this.sendControl('subscribe', [...this.desiredChannels]);
      }
    };

    socket.onmessage = (event) => {
      if (generation !== this.generation) {
        return;
      }
      const envelope = parseEnvelope(event.data);
      if (envelope === null) {
        this.onInvalidEnvelope?.();
        return;
      }
      if (this.seenIds.has(envelope.id)) {
        return;
      }
      if (this.lastSequence >= 0 && envelope.sequence > this.lastSequence + 1) {
        this.onGap?.({ lastSequence: this.lastSequence, receivedSequence: envelope.sequence });
      }
      this.lastSequence = envelope.sequence;
      this.rememberId(envelope.id);
      this.onEvent?.(envelope);
    };

    socket.onclose = (event) => {
      if (generation !== this.generation) {
        return;
      }
      this.socket = null;
      if (FATAL_CLOSE_CODES.has(event.code)) {
        this.setState('fatal', this.attempt, 0);
        this.onFatal?.(event.code, event.reason);
        return;
      }
      this.scheduleReconnect(generation);
    };
  }

  private rememberId(id: string): void {
    this.seenIds.add(id);
    this.seenOrder.push(id);
    while (this.seenOrder.length > MAX_SEEN_IDS) {
      const removed = this.seenOrder.shift();
      if (removed !== undefined) {
        this.seenIds.delete(removed);
      }
    }
  }

  private backoffDelay(nextAttempt: number): number {
    let delay = this.minBackoffMs;
    for (let index = 1; index < nextAttempt && delay < this.maxBackoffMs; index += 1) {
      delay = Math.min(this.maxBackoffMs, delay * 2);
    }
    return delay;
  }

  private scheduleReconnect(generation: number): void {
    if (generation !== this.generation) {
      return;
    }
    const nextAttempt = this.attempt + 1;
    if (nextAttempt > this.maxAttempts) {
      this.setState('fatal', this.attempt, 0);
      this.onFatal?.(0, 'max-attempts');
      return;
    }
    const delay = this.backoffDelay(nextAttempt);
    this.setState('backoff', nextAttempt, delay);
    this.cancelReconnect?.();
    this.cancelReconnect = this.schedule(() => {
      this.cancelReconnect = null;
      if (generation !== this.generation) {
        return;
      }
      this.attempt = nextAttempt;
      this.openSocket(generation);
    }, delay);
  }

  private sendControl(type: ControlMessageType, channels: readonly EventChannel[]): void {
    const socket = this.socket;
    if (socket === null || socket.readyState !== OPEN) {
      return;
    }
    const id = createRequestId();
    const message = type === 'ping' ? { type, id } : { type, id, channels: [...channels] };
    socket.send(JSON.stringify(message));
  }
}
