import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../api/client';
import { createTranslator } from '../i18n';
import { ConflictNotice } from './ConflictNotice';

const t = createTranslator('en');

function conflict(): ApiRequestError {
  return new ApiRequestError('conflict', {
    status: 412,
    problemCode: 'files.revision-conflict',
    requestId: 'req_conflict_123',
    currentETag: '"sha256:current"',
  });
}

describe('optimistic concurrency notice', () => {
  afterEach(cleanup);

  it('explains a conflict with only stable revision data', () => {
    render(
      <ConflictNotice
        error={conflict()}
        t={t}
        onReload={vi.fn()}
        onRetry={vi.fn()}
        onDismiss={vi.fn()}
      />,
    );

    expect(
      screen.getByRole('heading', { name: 'The file changed before the save' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Current revision: "sha256:current"/)).toBeInTheDocument();
    expect(screen.getByText(/req_conflict_123/)).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('files.revision-conflict');
  });

  it('invokes recovery actions without applying a stale write', () => {
    const onReload = vi.fn();
    const onRetry = vi.fn();
    const onDismiss = vi.fn();
    render(
      <ConflictNotice
        error={conflict()}
        t={t}
        onReload={onReload}
        onRetry={onRetry}
        onDismiss={onDismiss}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Reload current revision' }));
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));

    expect(onReload).toHaveBeenCalledTimes(1);
    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('renders nothing for a non-conflict error', () => {
    const { container } = render(
      <ConflictNotice
        error={new ApiRequestError('network-error')}
        t={t}
        onReload={vi.fn()}
        onDismiss={vi.fn()}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
