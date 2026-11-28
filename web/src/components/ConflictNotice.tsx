import type { ApiRequestError } from '../api/client';
import type { Translator } from '../i18n';
import { CONFLICT_KEYS, SHELL_KEYS } from '../i18n/keys';

interface ConflictNoticeProps {
  readonly error: ApiRequestError | null;
  readonly t: Translator;
  readonly onReload: () => void;
  readonly onRetry?: () => void;
  readonly onDismiss: () => void;
}

/**
 * Optimistic-concurrency recovery surface. It shows only the current revision and request id;
 * server prose, file paths, and tokens never enter this component.
 */
export function ConflictNotice({ error, t, onReload, onRetry, onDismiss }: ConflictNoticeProps) {
  if (error === null || error.reason !== 'conflict') {
    return null;
  }

  return (
    <section className="app__panel conflict" role="alert" aria-labelledby="conflict-title">
      <h2 id="conflict-title">{t(CONFLICT_KEYS.title)}</h2>
      <p>{t(CONFLICT_KEYS.body)}</p>
      {error.currentETag === null ? null : (
        <p>{t(CONFLICT_KEYS.currentETag, { etag: error.currentETag })}</p>
      )}
      {error.requestId === null ? null : (
        <p>{t(SHELL_KEYS.requestId, { requestId: error.requestId })}</p>
      )}
      <div className="conflict__actions">
        <button type="button" onClick={onReload}>
          {t(CONFLICT_KEYS.reload)}
        </button>
        {onRetry === undefined ? null : (
          <button type="button" onClick={onRetry}>
            {t(CONFLICT_KEYS.retry)}
          </button>
        )}
        <button type="button" onClick={onDismiss}>
          {t(CONFLICT_KEYS.dismiss)}
        </button>
      </div>
    </section>
  );
}
