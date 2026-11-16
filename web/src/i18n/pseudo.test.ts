import { describe, expect, it } from 'vitest';
import { pseudoLocalize } from './pseudo';

describe('pseudoLocalize', () => {
  it('expands and wraps the message', () => {
    const source = 'Secure deployment';
    const pseudo = pseudoLocalize(source);

    expect(pseudo).not.toBe(source);
    expect(pseudo.startsWith('[')).toBe(true);
    expect(pseudo.endsWith(']')).toBe(true);
    expect(pseudo.length).toBeGreaterThan(source.length);
  });

  it('preserves ICU placeholders verbatim', () => {
    expect(pseudoLocalize('Hello {name}')).toContain('{name}');
    expect(pseudoLocalize('{count, plural, one {# plugin} other {# plugins}}')).toContain(
      '{count, plural, one {# plugin} other {# plugins}}',
    );
  });
});
