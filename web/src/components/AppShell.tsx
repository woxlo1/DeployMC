import { useCallback, useMemo, useRef, useState, type ChangeEvent } from 'react';
import { createRequestId } from '../app/requestId';
import { ROUTES, routeHash, type RouteId } from '../app/routes';
import { isThemeMode, THEME_MODES, type ThemeMode } from '../app/theme';
import { useAuth } from '../auth/store';
import { authStatusLabelKey } from '../auth/status';
import { useHashLocation } from '../hooks/useHashLocation';
import { useShortcuts } from '../hooks/useShortcuts';
import { isLocale, SUPPORTED_LOCALES, type Locale, type Translator } from '../i18n';
import { AUTH_KEYS, SHELL_KEYS } from '../i18n/keys';
import { useToast } from '../toast/context';
import { DoctorPanel } from './DoctorPanel';
import { ErrorBoundary } from './ErrorBoundary';
import { EventStatus } from './EventStatus';
import { IdentityPanel } from './IdentityPanel';
import { PlaceholderView } from './PlaceholderView';
import { ShortcutHelpDialog } from './ShortcutHelpDialog';

const LANGUAGE_LABEL_KEYS: Readonly<Record<Locale, string>> = {
  en: SHELL_KEYS.languageEn,
  ja: SHELL_KEYS.languageJa,
};

const THEME_LABEL_KEYS: Readonly<Record<ThemeMode, string>> = {
  light: SHELL_KEYS.themeLight,
  dark: SHELL_KEYS.themeDark,
  system: SHELL_KEYS.themeSystem,
};

interface AppShellProps {
  readonly locale: Locale;
  readonly theme: ThemeMode;
  readonly t: Translator;
  readonly onLocaleChange: (locale: Locale) => void;
  readonly onThemeChange: (theme: ThemeMode) => void;
}

export function AppShell({ locale, theme, t, onLocaleChange, onThemeChange }: AppShellProps) {
  const location = useHashLocation();
  const toast = useToast();
  const auth = useAuth();
  const mainRef = useRef<HTMLElement>(null);
  const [helpOpen, setHelpOpen] = useState(false);

  const navigate = useCallback((id: RouteId) => {
    const next = routeHash(id);
    if (window.location.hash !== next) {
      window.location.hash = next;
    }
  }, []);

  const definitions = useMemo(
    () => [
      ...ROUTES.filter((route) => route.shortcut !== undefined).map((route) => ({
        id: `route:${route.id}`,
        combo: route.shortcut ?? '',
        labelKey: route.labelKey,
      })),
      { id: 'help', combo: 'shift+?', labelKey: SHELL_KEYS.shortcutsOpen },
      { id: 'close', combo: 'escape', labelKey: SHELL_KEYS.shortcutsClose },
    ],
    [],
  );

  const handleShortcut = useCallback(
    (id: string) => {
      if (id === 'help') {
        setHelpOpen(true);
        return;
      }
      if (id === 'close') {
        setHelpOpen(false);
        return;
      }
      if (helpOpen || !id.startsWith('route:')) {
        return;
      }
      const target = id.slice('route:'.length);
      const match = ROUTES.find((route) => route.id === target);
      if (match !== undefined) {
        navigate(match.id);
      }
    },
    [helpOpen, navigate],
  );

  useShortcuts(definitions, handleShortcut);

  const errorRequestId = useMemo(
    () => (location.state === 'error' ? createRequestId() : undefined),
    [location.state],
  );

  const retryView = useCallback(() => {
    if (location.route !== null) {
      navigate(location.route.id);
    }
  }, [location.route, navigate]);

  const handleLocaleChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const next = event.target.value;
    if (isLocale(next)) {
      onLocaleChange(next);
    }
  };

  const handleThemeChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const next = event.target.value;
    if (isThemeMode(next)) {
      onThemeChange(next);
    }
  };

  const activeRouteLabel =
    location.route === null ? t(SHELL_KEYS.routeInvalidTitle) : t(location.route.labelKey);
  const authStatusValues = auth.identity === null ? undefined : { id: auth.identity.identity.id };

  return (
    <div className="shell">
      <button className="skip-link" type="button" onClick={() => mainRef.current?.focus()}>
        {t(SHELL_KEYS.skip)}
      </button>

      <header className="shell__header" aria-label={t(SHELL_KEYS.headerLabel)}>
        <div className="shell__identity">
          <h1>{t(SHELL_KEYS.appTitle)}</h1>
          <p className="shell__tagline">{t(SHELL_KEYS.appTagline)}</p>
          <p
            className="shell__auth"
            data-auth-status={auth.status}
            aria-label={t(AUTH_KEYS.headerLabel)}
          >
            {t(authStatusLabelKey(auth.status), authStatusValues)}
          </p>
        </div>
        <div className="shell__controls">
          <label className="shell__control">
            <span>{t(SHELL_KEYS.appLanguage)}</span>
            <select
              aria-label={t(SHELL_KEYS.appLanguage)}
              value={locale}
              onChange={handleLocaleChange}
            >
              {SUPPORTED_LOCALES.map((supported) => (
                <option key={supported} value={supported}>
                  {t(LANGUAGE_LABEL_KEYS[supported])}
                </option>
              ))}
            </select>
          </label>
          <label className="shell__control">
            <span>{t(SHELL_KEYS.theme)}</span>
            <select aria-label={t(SHELL_KEYS.theme)} value={theme} onChange={handleThemeChange}>
              {THEME_MODES.map((mode) => (
                <option key={mode} value={mode}>
                  {t(THEME_LABEL_KEYS[mode])}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="shell__button"
            onClick={() => setHelpOpen(true)}
            aria-label={t(SHELL_KEYS.shortcutsOpen)}
          >
            {t(SHELL_KEYS.shortcuts)}
          </button>
          <button
            type="button"
            className="shell__button"
            onClick={() => toast.show({ messageKey: SHELL_KEYS.toastSample, tone: 'info' })}
          >
            {t(SHELL_KEYS.notifications)}
          </button>
        </div>
      </header>

      <aside className="shell__sidebar" aria-label={t(SHELL_KEYS.navLabel)}>
        <nav>
          <h2>{t(SHELL_KEYS.navLabel)}</h2>
          <ul>
            {ROUTES.map((route) => (
              <li key={route.id}>
                <a
                  href={routeHash(route.id)}
                  aria-current={location.route?.id === route.id ? 'page' : undefined}
                >
                  {t(route.labelKey)}
                </a>
              </li>
            ))}
          </ul>
        </nav>
      </aside>

      <main
        ref={mainRef}
        className="shell__content"
        aria-label={t(SHELL_KEYS.contentLabel)}
        tabIndex={-1}
      >
        <ErrorBoundary t={t} onReset={() => navigate('dashboard')}>
          {location.route === null ? (
            <section className="view" data-route="invalid" aria-labelledby="invalid-route-title">
              <h2 id="invalid-route-title">{t(SHELL_KEYS.routeInvalidTitle)}</h2>
              <p>{t(SHELL_KEYS.routeInvalidBody)}</p>
              <button type="button" onClick={() => navigate('dashboard')}>
                {t(SHELL_KEYS.routeInvalidReturn)}
              </button>
            </section>
          ) : (
            <PlaceholderView
              route={location.route}
              state={location.state}
              t={t}
              requestId={errorRequestId}
              onRetry={retryView}
            >
              {location.route.id === 'dashboard' ? (
                <p className="dashboard-summary__count">
                  {t(SHELL_KEYS.appPluginsCount, { count: 3 })}
                </p>
              ) : undefined}
              {location.route.id === 'identity' ? <IdentityPanel t={t} /> : undefined}
              {location.route.id === 'console' ? (
                <EventStatus
                  t={t}
                  onFatal={(code) => {
                    if (code === 4401) {
                      auth.reportUnauthorized();
                    }
                  }}
                />
              ) : undefined}
              {location.route.id === 'doctor' ? <DoctorPanel locale={locale} t={t} /> : undefined}
            </PlaceholderView>
          )}
        </ErrorBoundary>
      </main>

      <footer className="shell__status" role="status" aria-label={t(SHELL_KEYS.statusLabel)}>
        <span>{t(SHELL_KEYS.appStatusPreAlpha)}</span>
        <span>{t(SHELL_KEYS.statusReady)}</span>
        <span>{t(SHELL_KEYS.statusRoute, { route: activeRouteLabel })}</span>
      </footer>

      {helpOpen ? (
        <ShortcutHelpDialog t={t} definitions={definitions} onClose={() => setHelpOpen(false)} />
      ) : null}
    </div>
  );
}
