import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WS_ENDPOINT, WS_PROTOCOL_VERSION } from '../api/generated-events';
import { createTranslator } from '../i18n';
import type { WebSocketLike } from '../events/socket';
import { EventStatus } from './EventStatus';

const t = createTranslator('en');

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

function envelope(overrides: Record<string, unknown> = {}) {
  return {
    id: 'evt_ui_0123456789',
    type: 'console.line',
    sequence: 0,
    protocolVersion: WS_PROTOCOL_VERSION,
    timestamp: '2026-11-28T05:00:00Z',
    payload: { level: 'INFO', message: 'server started' },
    ...overrides,
  };
}

function socketState(container: HTMLElement): string | null {
  return container.querySelector('.events__status')?.getAttribute('data-socket-state') ?? null;
}

describe('event status panel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('shows connection state, event type, sequence, gap, and invalid-envelope warnings', () => {
    const sockets: FakeSocket[] = [];
    const onGap = vi.fn();
    const factory = vi.fn((url: string) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    });
    const { container } = render(<EventStatus t={t} socketFactory={factory} onGap={onGap} />);

    expect(socketState(container)).toBe('idle');
    fireEvent.click(screen.getByRole('button', { name: 'Connect' }));
    expect(factory).toHaveBeenCalledWith(WS_ENDPOINT);

    act(() => sockets[0]!.open());
    expect(socketState(container)).toBe('connected');
    expect(JSON.parse(sockets[0]!.sent[0]!)).toMatchObject({ type: 'subscribe' });

    act(() => sockets[0]!.emit(envelope()));
    expect(screen.getAllByText('Console line').length).toBeGreaterThan(0);
    expect(screen.getAllByText('0').length).toBeGreaterThan(0);
    expect(container.textContent).not.toContain('server started');

    act(() => sockets[0]!.emit(envelope({ id: 'evt_ui_gap_9876543210', sequence: 2 })));
    expect(onGap).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/event gap was detected/i)).toBeInTheDocument();

    act(() => sockets[0]!.emit('not json'));
    expect(screen.getByText(/invalid event envelope/i)).toBeInTheDocument();
  });

  it('reconnects after a non-fatal close and resubscribes all channels', () => {
    const sockets: FakeSocket[] = [];
    const factory = vi.fn((url: string) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    });
    const { container } = render(<EventStatus t={t} socketFactory={factory} />);

    fireEvent.click(screen.getByRole('button', { name: 'Connect' }));
    act(() => sockets[0]!.open());
    act(() => sockets[0]!.close(1006, 'network'));
    expect(socketState(container)).toBe('backoff');
    expect(screen.getAllByText('Reconnecting in 1s, attempt 1').length).toBeGreaterThan(0);

    act(() => vi.advanceTimersByTime(500));
    expect(factory).toHaveBeenCalledTimes(2);
    act(() => sockets[1]!.open());

    expect(socketState(container)).toBe('connected');
    expect(JSON.parse(sockets[1]!.sent[0]!)).toMatchObject({
      type: 'subscribe',
      channels: ['console', 'deployments', 'files', 'presence'],
    });
  });

  it('stops after a policy close and reports the fatal code', () => {
    const sockets: FakeSocket[] = [];
    const factory = vi.fn((url: string) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    });
    const onFatal = vi.fn();
    const { container } = render(<EventStatus t={t} socketFactory={factory} onFatal={onFatal} />);

    fireEvent.click(screen.getByRole('button', { name: 'Connect' }));
    act(() => sockets[0]!.open());
    act(() => sockets[0]!.close(4403, 'rbac.permission-revoked'));

    expect(socketState(container)).toBe('fatal');
    expect(onFatal).toHaveBeenCalledWith(4403, 'rbac.permission-revoked');
    act(() => vi.advanceTimersByTime(30_000));
    expect(factory).toHaveBeenCalledTimes(1);
  });
});
