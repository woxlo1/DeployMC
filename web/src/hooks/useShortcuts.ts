import { useEffect, useRef } from 'react';
import { comboFromKeyboardEvent, type ShortcutDefinition } from '../app/shortcuts';

const EDITABLE_TAGS = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

export type ShortcutHandler = (id: string) => void;

/** Registers a closed set of shortcuts and ignores them while the user is typing. */
export function useShortcuts(
  definitions: readonly ShortcutDefinition[],
  handler: ShortcutHandler,
): void {
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const combo = comboFromKeyboardEvent(event);
      const target = event.target;
      const editing =
        target instanceof HTMLElement &&
        (target.isContentEditable || EDITABLE_TAGS.has(target.tagName.toUpperCase()));
      if (editing && combo !== 'escape') {
        return;
      }

      const match = definitions.find((definition) => definition.combo === combo);
      if (match === undefined) {
        return;
      }
      event.preventDefault();
      handlerRef.current(match.id);
    };

    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [definitions]);
}
