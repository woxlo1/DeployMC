import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createTranslator } from '../i18n';
import { ErrorBoundary } from './ErrorBoundary';

let shouldThrow = true;
let previousOnError: typeof window.onerror = null;

function FragileChild() {
  if (shouldThrow) {
    throw new Error('secret-host.example/token');
  }
  return <p>Recovered</p>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    shouldThrow = true;
    previousOnError = window.onerror;
    window.onerror = () => true;
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    window.onerror = previousOnError;
    vi.restoreAllMocks();
  });

  it('shows a request id without leaking the exception text', () => {
    render(
      <ErrorBoundary t={createTranslator('en')}>
        <FragileChild />
      </ErrorBoundary>,
    );

    expect(screen.getByTestId('error-boundary')).toBeInTheDocument();
    expect(screen.getByText(/Request ID: req_/)).toBeInTheDocument();
    expect(screen.queryByText(/secret-host\.example/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByText('Details'));
    expect(screen.getByText('UI_RENDER_FAILED')).toBeInTheDocument();
  });

  it('renders children again after the user reloads the view', () => {
    const onReset = vi.fn();
    render(
      <ErrorBoundary t={createTranslator('en')} onReset={onReset}>
        <FragileChild />
      </ErrorBoundary>,
    );

    shouldThrow = false;
    fireEvent.click(screen.getByRole('button', { name: 'Reload view' }));

    expect(onReset).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Recovered')).toBeInTheDocument();
  });
});
