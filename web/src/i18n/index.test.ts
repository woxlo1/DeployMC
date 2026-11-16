import { describe, expect, it } from 'vitest';
import { catalogs, createTranslator, detectLocale, isLocale } from './index';

describe('translation catalog', () => {
  it('excludes metadata entries from the message catalog', () => {
    for (const catalog of Object.values(catalogs)) {
      expect(Object.keys(catalog).some((key) => key.startsWith('@'))).toBe(false);
    }
  });

  it('translates simple messages', () => {
    expect(createTranslator('en')('app.title')).toBe('DeployMC');
    expect(createTranslator('ja')('app.status.preAlpha')).toBe('プレアルファ：基盤開発中です。');
  });

  it('formats English plural categories', () => {
    const t = createTranslator('en');

    expect(t('app.plugins.count', { count: 1 })).toBe('1 plugin');
    expect(t('app.plugins.count', { count: 2 })).toBe('2 plugins');
  });

  it('formats Japanese plural categories', () => {
    expect(createTranslator('ja')('app.plugins.count', { count: 2 })).toBe('2 個のプラグイン');
  });

  it('falls back to the key when no catalog contains it', () => {
    expect(createTranslator('ja')('app.missing')).toBe('app.missing');
  });

  it('rejects plural formatting without a numeric value', () => {
    expect(() => createTranslator('en')('app.plugins.count')).toThrow(
      'Plural value for "count" must be a finite number',
    );
  });
});

describe('locale helpers', () => {
  it('detects Japanese variants and falls back to English', () => {
    expect(detectLocale('ja-JP')).toBe('ja');
    expect(detectLocale('JA')).toBe('ja');
    expect(detectLocale('en-GB')).toBe('en');
    expect(detectLocale('fr-FR')).toBe('en');
  });

  it('validates supported locale identifiers', () => {
    expect(isLocale('en')).toBe(true);
    expect(isLocale('ja')).toBe(true);
    expect(isLocale('fr')).toBe(false);
    expect(isLocale(null)).toBe(false);
  });
});
