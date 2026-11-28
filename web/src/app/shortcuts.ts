export interface ShortcutDefinition {
  readonly id: string;
  readonly combo: string;
  readonly labelKey: string;
}

/**
 * Normalizes a keyboard event into a stable combo string.
 *
 * Digits use `event.code` so `Alt+1` remains predictable on layouts where the shifted key produces
 * a different character.
 */
export function comboFromKeyboardEvent(event: KeyboardEvent): string {
  const parts: string[] = [];
  if (event.ctrlKey) {
    parts.push('ctrl');
  }
  if (event.metaKey) {
    parts.push('meta');
  }
  if (event.altKey) {
    parts.push('alt');
  }
  if (event.shiftKey) {
    parts.push('shift');
  }

  const digit = /^digit([0-9])$/.exec(event.code.toLowerCase());
  let key = digit?.[1] ?? event.key.toLowerCase();
  if (key === 'escape') {
    key = 'escape';
  } else if (key === ' ') {
    key = 'space';
  }
  parts.push(key);
  return parts.join('+');
}

export function matchesShortcut(event: KeyboardEvent, definition: ShortcutDefinition): boolean {
  return comboFromKeyboardEvent(event) === definition.combo;
}
