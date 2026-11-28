import { describe, expect, it } from 'vitest';
import { comboFromKeyboardEvent, matchesShortcut } from './shortcuts';

function keyEvent(init: KeyboardEventInit): KeyboardEvent {
  return new KeyboardEvent('keydown', init);
}

describe('keyboard shortcut normalization', () => {
  it('uses the key code for digit shortcuts', () => {
    const event = keyEvent({ key: '¡', code: 'Digit1', altKey: true });

    expect(comboFromKeyboardEvent(event)).toBe('alt+1');
  });

  it('normalizes the shortcut help and escape combos', () => {
    expect(comboFromKeyboardEvent(keyEvent({ key: '?', shiftKey: true }))).toBe('shift+?');
    expect(comboFromKeyboardEvent(keyEvent({ key: 'Escape' }))).toBe('escape');
  });

  it('preserves modifier order for combined shortcuts', () => {
    const event = keyEvent({ key: 'k', ctrlKey: true, shiftKey: true });

    expect(comboFromKeyboardEvent(event)).toBe('ctrl+shift+k');
  });

  it('matches a registered definition exactly', () => {
    const definition = { id: 'files', combo: 'alt+2', labelKey: 'shell.nav.files' };

    expect(matchesShortcut(keyEvent({ key: '2', code: 'Digit2', altKey: true }), definition)).toBe(
      true,
    );
    expect(matchesShortcut(keyEvent({ key: '2', code: 'Digit2' }), definition)).toBe(false);
  });
});
