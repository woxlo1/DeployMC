import { Component, type ReactNode } from 'react';
import { createRequestId } from '../app/requestId';
import type { Translator } from '../i18n';
import { SHELL_KEYS } from '../i18n/keys';

interface ErrorBoundaryProps {
  readonly t: Translator;
  readonly onReset?: () => void;
  readonly children?: ReactNode;
}

interface ErrorBoundaryState {
  readonly hasError: boolean;
  readonly requestId: string | null;
}

/** Catches render failures without printing raw exception text or paths to the user. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false, requestId: null };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true, requestId: createRequestId() };
  }

  private readonly handleReset = () => {
    this.setState({ hasError: false, requestId: null });
    this.props.onReset?.();
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    const requestId = this.state.requestId ?? createRequestId();
    return (
      <div className="error-boundary" role="alert" data-testid="error-boundary">
        <p className="error-boundary__title">{tOrKey(this.props.t, SHELL_KEYS.errorTitle)}</p>
        <p>{tOrKey(this.props.t, SHELL_KEYS.errorBody)}</p>
        <details>
          <summary>{tOrKey(this.props.t, SHELL_KEYS.errorDetails)}</summary>
          <p className="error-boundary__code">{tOrKey(this.props.t, SHELL_KEYS.errorCode)}</p>
          <p className="error-boundary__request-id">
            {tOrKey(this.props.t, SHELL_KEYS.requestId, { requestId })}
          </p>
        </details>
        <button type="button" onClick={this.handleReset}>
          {tOrKey(this.props.t, SHELL_KEYS.errorReload)}
        </button>
      </div>
    );
  }
}

function tOrKey(
  t: Translator,
  key: string,
  values?: Readonly<Record<string, string | number>>,
): string {
  return values === undefined ? t(key) : t(key, values);
}
