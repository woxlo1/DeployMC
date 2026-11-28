import { useEffect, useMemo, useState } from 'react';
import { applyTheme, persistThemeMode, readThemeMode, type ThemeMode } from './app/theme';
import { AppShell } from './components/AppShell';
import { ToastProvider } from './components/ToastProvider';
import { createTranslator, detectLocale, isLocale, type Locale } from './i18n';
import { SHELL_KEYS } from './i18n/keys';

const LOCALE_STORAGE_KEY = 'deploymc.locale';

function readInitialLocale(): Locale {
  try {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    if (isLocale(stored)) {
      return stored;
    }
  } catch {
    // Storage can be unavailable in private or hardened browser contexts.
  }
  return detectLocale(window.navigator.language);
}

function persistLocale(locale: Locale): void {
  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Persisting the locale is best effort; the session still works without it.
  }
}

export function App() {
  const [locale, setLocale] = useState<Locale>(readInitialLocale);
  const [theme, setTheme] = useState<ThemeMode>(readThemeMode);
  const t = useMemo(() => createTranslator(locale), [locale]);

  useEffect(() => {
    document.documentElement.lang = locale;
    document.title = t(SHELL_KEYS.appTitle);
    persistLocale(locale);
  }, [locale, t]);

  useEffect(() => {
    applyTheme(theme);
    persistThemeMode(theme);

    if (theme !== 'system' || typeof window.matchMedia !== 'function') {
      return;
    }
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    if (typeof query.addEventListener !== 'function') {
      return;
    }
    const onPreferenceChange = () => applyTheme(theme);
    query.addEventListener('change', onPreferenceChange);
    return () => query.removeEventListener('change', onPreferenceChange);
  }, [theme]);

  return (
    <ToastProvider t={t}>
      <AppShell
        locale={locale}
        theme={theme}
        t={t}
        onLocaleChange={setLocale}
        onThemeChange={setTheme}
      />
    </ToastProvider>
  );
}
