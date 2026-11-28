import { useEffect, useRef, type KeyboardEvent as ReactKeyboardEvent } from 'react';
import type { ShortcutDefinition } from '../app/shortcuts';
import type { Translator } from '../i18n';
import { SHELL_KEYS } from '../i18n/keys';

interface ShortcutHelpDialogProps {
  readonly t: Translator;
  readonly definitions: readonly ShortcutDefinition[];
  readonly onClose: () => void;
}

const FOCUSABLES = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

/** Modal shortcut reference with focus restoration and a simple focus trap. */
export function ShortcutHelpDialog({ t, definitions, onClose }: ShortcutHelpDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    closeRef.current?.focus();
    return () => previous?.focus();
  }, []);

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
      return;
    }
    if (event.key !== 'Tab' || dialogRef.current === null) {
      return;
    }

    const focusables = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLES));
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (first === undefined || last === undefined) {
      return;
    }
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  return (
    <div
      className="dialog-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={dialogRef}
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="shortcut-help-title"
        onKeyDown={handleKeyDown}
      >
        <h2 id="shortcut-help-title">{t(SHELL_KEYS.shortcutsTitle)}</h2>
        <p>{t(SHELL_KEYS.shortcutsDescription)}</p>
        <table className="shortcut-table">
          <thead>
            <tr>
              <th scope="col">{t(SHELL_KEYS.shortcutsAction)}</th>
              <th scope="col">{t(SHELL_KEYS.shortcutsKeys)}</th>
            </tr>
          </thead>
          <tbody>
            {definitions.map((definition) => (
              <tr key={definition.id}>
                <td>{t(definition.labelKey)}</td>
                <td>
                  <kbd>{definition.combo}</kbd>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <button ref={closeRef} type="button" onClick={onClose}>
          {t(SHELL_KEYS.shortcutsClose)}
        </button>
      </div>
    </div>
  );
}
