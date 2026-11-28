import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { Translator } from '../i18n';
import { SHELL_KEYS } from '../i18n/keys';
import { ToastContext, type ToastApi, type ToastItem, type ToastRequest } from '../toast/context';

const TOAST_TIMEOUT_MS = 6000;
let toastSequence = 0;

interface ToastProviderProps {
  readonly t: Translator;
  readonly children: ReactNode;
}

export function ToastProvider({ t, children }: ToastProviderProps) {
  const [toasts, setToasts] = useState<readonly ToastItem[]>([]);
  const timers = useRef<Map<string, number>>(new Map());

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
    const timer = timers.current.get(id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const show = useCallback(
    (request: ToastRequest) => {
      toastSequence += 1;
      const id = `toast-${toastSequence}`;
      setToasts((current) => [...current, { ...request, id }]);
      const timer = window.setTimeout(() => dismiss(id), TOAST_TIMEOUT_MS);
      timers.current.set(id, timer);
    },
    [dismiss],
  );

  useEffect(
    () => () => {
      for (const timer of timers.current.values()) {
        window.clearTimeout(timer);
      }
      timers.current.clear();
    },
    [],
  );

  const api = useMemo<ToastApi>(() => ({ show, dismiss }), [show, dismiss]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <section
        className="toast-region"
        aria-label={t(SHELL_KEYS.toastRegion)}
        aria-live="polite"
        aria-atomic="true"
      >
        {toasts.map((toast) => {
          const tone = toast.tone ?? 'info';
          return (
            <div
              key={toast.id}
              className={`toast toast--${tone}`}
              data-tone={tone}
              role={tone === 'error' ? 'alert' : undefined}
            >
              <p className="toast__message">{t(toast.messageKey, toast.values)}</p>
              {toast.requestId === undefined ? null : (
                <p className="toast__request-id">
                  {t(SHELL_KEYS.requestId, { requestId: toast.requestId })}
                </p>
              )}
              <button type="button" onClick={() => dismiss(toast.id)}>
                {t(SHELL_KEYS.toastDismiss)}
              </button>
            </div>
          );
        })}
      </section>
    </ToastContext.Provider>
  );
}
