import type { ReactNode } from 'react';
import type { RouteDefinition } from '../app/routes';
import type { ViewStateKind } from '../app/viewState';
import type { Translator } from '../i18n';
import { ViewState } from './ViewState';

interface PlaceholderViewProps {
  readonly route: RouteDefinition;
  readonly state: ViewStateKind;
  readonly t: Translator;
  readonly requestId?: string;
  readonly onRetry?: () => void;
  readonly children?: ReactNode;
}

/**
 * Every route uses this host. Later work packages can replace `children` with real content while
 * retaining the shared heading and loading/empty/error state contract.
 */
export function PlaceholderView({
  route,
  state,
  t,
  requestId,
  onRetry,
  children,
}: PlaceholderViewProps) {
  const headingId = `view-heading-${route.id}`;
  const view = t(route.labelKey);

  return (
    <section className="view" data-route={route.id} aria-labelledby={headingId}>
      <h2 id={headingId}>{view}</h2>
      {children}
      <ViewState kind={state} view={view} t={t} requestId={requestId} onRetry={onRetry} />
    </section>
  );
}
