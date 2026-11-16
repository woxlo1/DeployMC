import en from '../locales/en.json';
import ja from '../locales/ja.json';

export const SUPPORTED_LOCALES = ['en', 'ja'] as const;

export type Locale = (typeof SUPPORTED_LOCALES)[number];

type RawCatalog = Record<string, unknown>;

export type Catalog = Readonly<Record<string, string>>;

export type TranslationValues = Readonly<Record<string, string | number>>;

const PLURAL_PATTERN = /\{(\w+),\s*plural,\s*((?:[^{}]|\{[^{}]*\})*)\}/g;
const BRANCH_PATTERN = /(\w+)\s*\{([^{}]*)\}/g;
const INTERPOLATION_PATTERN = /\{(\w+)\}/g;

function toCatalog(raw: RawCatalog): Catalog {
  const entries = Object.entries(raw).filter(
    (entry): entry is [string, string] => !entry[0].startsWith('@') && typeof entry[1] === 'string',
  );
  return Object.freeze(Object.fromEntries(entries));
}

export const catalogs: Readonly<Record<Locale, Catalog>> = {
  en: toCatalog(en as RawCatalog),
  ja: toCatalog(ja as RawCatalog),
};

export function isLocale(value: string | null): value is Locale {
  return value !== null && (SUPPORTED_LOCALES as readonly string[]).includes(value);
}

export function detectLocale(language: string): Locale {
  return language.toLowerCase().startsWith('ja') ? 'ja' : 'en';
}

function formatPlural(
  locale: Locale,
  name: string,
  body: string,
  values: TranslationValues,
): string {
  const value = values[name];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(`Plural value for "${name}" must be a finite number`);
  }

  const branches = new Map<string, string>();
  for (const match of body.matchAll(BRANCH_PATTERN)) {
    const selector = match[1];
    const message = match[2];
    if (selector !== undefined && message !== undefined) {
      branches.set(selector, message);
    }
  }

  const selector = new Intl.PluralRules(locale).select(value);
  const branch = branches.get(selector) ?? branches.get('other');
  if (branch === undefined) {
    throw new Error(`Plural "${name}" has no "other" branch`);
  }
  return branch.replaceAll('#', String(value));
}

function formatMessage(locale: Locale, template: string, values: TranslationValues): string {
  const withPlurals = template.replace(PLURAL_PATTERN, (_match, name: string, body: string) =>
    formatPlural(locale, name, body, values),
  );
  return withPlurals.replace(INTERPOLATION_PATTERN, (match, name: string) => {
    const value = values[name];
    return value === undefined ? match : String(value);
  });
}

export type Translator = (key: string, values?: TranslationValues) => string;

export function createTranslator(locale: Locale): Translator {
  return (key, values = {}) => {
    const template = catalogs[locale][key] ?? catalogs.en[key] ?? key;
    return formatMessage(locale, template, values);
  };
}
