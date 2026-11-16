import { useEffect, useMemo, useState } from 'react';
import { createTranslator, detectLocale, isLocale, SUPPORTED_LOCALES, type Locale } from './i18n';

const STORAGE_KEY = 'deploymc.locale';

const LANGUAGE_LABEL_KEYS: Readonly<Record<Locale, string>> = {
  en: 'language.en',
  ja: 'language.ja',
};

function readInitialLocale(): Locale {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (isLocale(stored)) {
      return stored;
    }
  } catch {
    // Storage can be unavailable in private or hardened browser contexts.
  }
  return detectLocale(window.navigator.language);
}

export function App() {
  const [locale, setLocale] = useState<Locale>(readInitialLocale);
  const t = useMemo(() => createTranslator(locale), [locale]);

  useEffect(() => {
    document.documentElement.lang = locale;
    document.title = t('app.title');
    try {
      window.localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      // Persisting the locale is best effort; the session still works without it.
    }
  }, [locale, t]);

  return (
    <main className="app">
      <header className="app__header">
        <h1>{t('app.title')}</h1>
        <p className="app__tagline">{t('app.tagline')}</p>
        <p className="app__status" role="status">
          {t('app.status.preAlpha')}
        </p>
      </header>

      <section className="app__panel" aria-labelledby="locale-heading">
        <h2 id="locale-heading">{t('app.language')}</h2>
        <select
          aria-label={t('app.language')}
          value={locale}
          onChange={(event) => {
            const next = event.target.value;
            if (isLocale(next)) {
              setLocale(next);
            }
          }}
        >
          {SUPPORTED_LOCALES.map((supported) => (
            <option key={supported} value={supported}>
              {t(LANGUAGE_LABEL_KEYS[supported])}
            </option>
          ))}
        </select>
        <p>{t('app.plugins.count', { count: 3 })}</p>
      </section>
    </main>
  );
}
