import type { ViewStateKind } from '../app/viewState';
import type { Translator } from '../i18n';
import { SHELL_KEYS } from '../i18n/keys';

interface ViewStateProps {
  readonly kind: ViewStateKind;
  readonly view: string;
  readonly t: Translator;
  readonly requestId?: string;
  readonly onRetry?: () => void;
}

/** The single loading/empty/error state contract used by every shell view. */
export function ViewState({ kind, view, t, requestId, onRetry }: ViewStateProps) {
  if (kind === 'loading') {
    return (
      <div className="view-state view-state--loading" data-view-state="loading" aria-live="polite">
        <span className="view-state__spinner" aria-hidden="true" />
        <p className="view-state__title">{t(SHELL_KEYS.stateLoading)}</p>
        <p>{t(SHELL_KEYS.stateLoadingBody, { view })}</p>
      </div>
    );
  }

  if (kind === 'error') {
    return (
      <div className="view-state view-state--error" data-view-state="error" role="alert">
        <span className="view-state__icon" aria-hidden="true">
          !
        </span>
        <p className="view-state__title">{t(SHELL_KEYS.stateError)}</p>
        <p>{t(SHELL_KEYS.stateErrorBody)}</p>
        {requestId === undefined ? null : (
          <p className="view-state__request-id">{t(SHELL_KEYS.requestId, { requestId })}</p>
        )}
        {onRetry === undefined ? null : (
          <button type="button" onClick={onRetry}>
            {t(SHELL_KEYS.stateRetry)}
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="view-state view-state--empty" data-view-state="empty">
      <span className="view-state__icon" aria-hidden="true">
        –
      </span>
      <p className="view-state__title">{t(SHELL_KEYS.stateEmpty)}</p>
      <p>{t(SHELL_KEYS.stateEmptyBody, { view })}</p>
    </div>
  );
}
