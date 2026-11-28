import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { EVENT_CHANNELS, type EventChannel, type ServerEventType } from '../api/generated-events';
import { WebSocketClient, type SocketFactory, type SocketState } from '../events/socket';
import type { Translator } from '../i18n';
import { EVENT_KEYS } from '../i18n/keys';

interface EventStatusProps {
  readonly t: Translator;
  readonly socketFactory?: SocketFactory;
  readonly onGap?: () => void;
  readonly onFatal?: (code: number, reason: string) => void;
}

const CHANNEL_LABEL_KEYS: Readonly<Record<EventChannel, string>> = {
  console: EVENT_KEYS.channelConsole,
  deployments: EVENT_KEYS.channelDeployments,
  files: EVENT_KEYS.channelFiles,
  presence: EVENT_KEYS.channelPresence,
};

const TYPE_LABEL_KEYS: Readonly<Record<ServerEventType, string>> = {
  'console.line': EVENT_KEYS.typeConsoleLine,
  'console.gap': EVENT_KEYS.typeConsoleGap,
  'events.gap': EVENT_KEYS.typeEventsGap,
  'deployment.state': EVENT_KEYS.typeDeploymentState,
  'file.changed': EVENT_KEYS.typeFileChanged,
  'presence.changed': EVENT_KEYS.typePresenceChanged,
  'server.notice': EVENT_KEYS.typeServerNotice,
};

function stateLabelKey(state: SocketState): string {
  switch (state) {
    case 'connecting':
      return EVENT_KEYS.statusConnecting;
    case 'connected':
      return EVENT_KEYS.statusConnected;
    case 'backoff':
      return EVENT_KEYS.statusBackoff;
    case 'fatal':
      return EVENT_KEYS.statusFatal;
    default:
      return EVENT_KEYS.statusIdle;
  }
}

/**
 * Event-stream control surface. It displays connection state and event types only; payload data
 * is intentionally not rendered because WebSocket events can carry operational detail.
 */
export function EventStatus({ t, socketFactory, onGap, onFatal }: EventStatusProps) {
  const clientRef = useRef<WebSocketClient | null>(null);
  const [selected, setSelected] = useState<readonly EventChannel[]>([...EVENT_CHANNELS]);
  const [state, setState] = useState<SocketState>('idle');
  const [attempt, setAttempt] = useState(0);
  const [delayMs, setDelayMs] = useState(0);
  const [lastType, setLastType] = useState<ServerEventType | null>(null);
  const [sequence, setSequence] = useState<number | null>(null);
  const [gapCount, setGapCount] = useState(0);
  const [invalidCount, setInvalidCount] = useState(0);

  const handleFatal = useCallback(
    (code: number, reason: string) => {
      onFatal?.(code, reason);
    },
    [onFatal],
  );

  const handleGap = useCallback(() => {
    setGapCount((current) => current + 1);
    onGap?.();
  }, [onGap]);

  const options = useMemo(
    () => ({
      socketFactory,
      onStateChange: (next: SocketState, nextAttempt: number, nextDelayMs: number) => {
        setState(next);
        setAttempt(nextAttempt);
        setDelayMs(nextDelayMs);
      },
      onEvent: (event: { type: ServerEventType; sequence: number }) => {
        setLastType(event.type);
        setSequence(event.sequence);
      },
      onGap: handleGap,
      onInvalidEnvelope: () => setInvalidCount((current) => current + 1),
      onFatal: handleFatal,
    }),
    [handleFatal, handleGap, socketFactory],
  );

  useEffect(() => {
    const client = new WebSocketClient(options);
    clientRef.current = client;
    return () => {
      client.disconnect();
      if (clientRef.current === client) {
        clientRef.current = null;
      }
    };
  }, [options]);

  function toggle(channel: EventChannel) {
    const client = clientRef.current;
    setSelected((current) => {
      const next = current.includes(channel)
        ? current.filter((entry) => entry !== channel)
        : [...current, channel];
      if (client !== null) {
        if (next.includes(channel)) {
          client.subscribe([channel]);
        } else {
          client.unsubscribe([channel]);
        }
      }
      return next;
    });
  }

  const statusValues =
    state === 'backoff'
      ? {
          seconds: String(Math.max(1, Math.ceil(delayMs / 1000))),
          attempt: String(Math.max(1, attempt)),
        }
      : undefined;

  return (
    <section className="app__panel events" aria-labelledby="events-heading">
      <h2 id="events-heading">{t(EVENT_KEYS.heading)}</h2>
      <p className="events__status" data-socket-state={state} role="status">
        {t(stateLabelKey(state), statusValues)}
      </p>
      <fieldset className="events__channels">
        <legend>{t(EVENT_KEYS.channelsLabel)}</legend>
        {EVENT_CHANNELS.map((channel) => (
          <label key={channel}>
            <input
              type="checkbox"
              checked={selected.includes(channel)}
              onChange={() => toggle(channel)}
            />
            {t(CHANNEL_LABEL_KEYS[channel])}
          </label>
        ))}
      </fieldset>
      <div className="events__actions">
        <button
          type="button"
          disabled={state === 'connecting' || state === 'connected' || state === 'backoff'}
          onClick={() => {
            const client = clientRef.current;
            if (client === null) return;
            client.subscribe(selected);
            client.connect();
          }}
        >
          {t(EVENT_KEYS.connect)}
        </button>
        <button
          type="button"
          disabled={state === 'idle' || state === 'fatal'}
          onClick={() => clientRef.current?.disconnect()}
        >
          {t(EVENT_KEYS.disconnect)}
        </button>
      </div>
      <dl className="events__details">
        <div>
          <dt>{t(EVENT_KEYS.statusLabel)}</dt>
          <dd>{t(stateLabelKey(state), statusValues)}</dd>
        </div>
        <div>
          <dt>{t(EVENT_KEYS.lastEvent)}</dt>
          <dd>{lastType === null ? t(EVENT_KEYS.noEvents) : t(TYPE_LABEL_KEYS[lastType])}</dd>
        </div>
        <div>
          <dt>{t(EVENT_KEYS.sequence)}</dt>
          <dd>{sequence === null ? t(EVENT_KEYS.noEvents) : sequence}</dd>
        </div>
      </dl>
      {gapCount === 0 ? null : (
        <p role="status" className="events__gap">
          {t(EVENT_KEYS.gap)}
        </p>
      )}
      {invalidCount === 0 ? null : (
        <p role="status" className="events__invalid">
          {t(EVENT_KEYS.invalidEnvelope)}
        </p>
      )}
    </section>
  );
}
