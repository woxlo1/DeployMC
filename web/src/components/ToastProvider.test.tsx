import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { createTranslator } from '../i18n';
import { useToast } from '../toast/context';
import { ToastProvider } from './ToastProvider';

function TriggerButtons() {
  const toast = useToast();
  return (
    <>
      <button
        type="button"
        onClick={() =>
          toast.show({
            messageKey: 'shell.toast.sample',
            tone: 'error',
            requestId: 'req_test',
          })
        }
      >
        Trigger error
      </button>
      <button
        type="button"
        onClick={() => toast.show({ messageKey: 'shell.toast.sample', tone: 'info' })}
      >
        Trigger info
      </button>
    </>
  );
}

describe('ToastProvider', () => {
  it('exposes tone, request id, and live-region roles', () => {
    render(
      <ToastProvider t={createTranslator('en')}>
        <TriggerButtons />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Trigger error' }));
    expect(screen.getByRole('alert')).toHaveTextContent('The interface shell is ready.');
    expect(screen.getByText('Request ID: req_test')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Trigger info' }));
    expect(screen.getAllByText('The interface shell is ready.')).toHaveLength(2);
    expect(screen.getAllByRole('alert')).toHaveLength(1);
  });
});
