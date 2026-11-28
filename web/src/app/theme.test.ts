import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  applyTheme,
  isThemeMode,
  persistThemeMode,
  readThemeMode,
  resolveTheme,
  THEME_STORAGE_KEY,
} from './theme';

function stubColorScheme(matches: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: (query: string) => ({
      matches,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
}

describe('theme mode', () => {
  afterEach(() => {
    window.localStorage.clear();
    delete document.documentElement.dataset.theme;
    document.documentElement.style.colorScheme = '';
    vi.unstubAllGlobals();
  });

  it('validates only the closed mode set', () => {
    expect(isThemeMode('light')).toBe(true);
    expect(isThemeMode('dark')).toBe(true);
    expect(isThemeMode('system')).toBe(true);
    expect(isThemeMode('auto')).toBe(false);
    expect(isThemeMode(null)).toBe(false);
  });

  it('reads, persists, and validates the stored mode', () => {
    expect(readThemeMode()).toBe('system');
    persistThemeMode('dark');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(readThemeMode()).toBe('dark');

    window.localStorage.setItem(THEME_STORAGE_KEY, 'invalid');
    expect(readThemeMode()).toBe('system');
  });

  it('resolves system mode from the color-scheme preference', () => {
    stubColorScheme(true);
    expect(resolveTheme('system')).toBe('dark');
    stubColorScheme(false);
    expect(resolveTheme('system')).toBe('light');
    expect(resolveTheme('dark')).toBe('dark');
  });

  it('applies the mode to the document without writing storage', () => {
    stubColorScheme(true);
    applyTheme('system');
    expect(document.documentElement.dataset.theme).toBe('system');
    expect(document.documentElement.style.colorScheme).toBe('dark');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();

    applyTheme('light');
    expect(document.documentElement.style.colorScheme).toBe('light');
  });
});
