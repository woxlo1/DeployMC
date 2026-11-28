import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { App } from '../App';
import { createTranslator } from '../i18n';
import { routeHash } from '../app/routes';
import { IdentityPanel } from '../components/IdentityPanel';
import { ToastProvider } from '../components/ToastProvider';

const TOKEN = 'wp42-auth-fake-token';
const t = createTranslator('en');

function identityResponse(): Response {
  return new Response(
    JSON.stringify({
      schemaVersion: '1.0',
      identity: { id: 'viewer@example.invalid', kind: 'token' },
      roles: ['reader'],
      permissions: ['server.read', 'files.read'],
    }),
    {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req_identity_ok' },
    },
  );
}

function unauthorizedResponse(): Response {
  return new Response(
    JSON.stringify({
      type: 'urn:deploymc:error:auth',
      title: 'Localized detail must not be shown',
      status: 401,
      code: 'auth.invalid-token',
      requestId: 'req_identity_401',
    }),
    {
      status: 401,
      headers: {
        'Content-Type': 'application/problem+json',
        'X-Request-Id': 'req_identity_401',
      },
    },
  );
}

function renderPanel() {
  return render(
    <ToastProvider t={t}>
      <AuthProvider>
        <IdentityPanel t={t} />
      </AuthProvider>
    </ToastProvider>,
  );
}

function signIn(token: string) {
  fireEvent.change(screen.getByLabelText('Bearer token'), { target: { value: token } });
  fireEvent.click(screen.getByRole('button', { name: 'Use token' }));
}

describe('in-memory authentication', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('verifies an identity, displays it, and never persists the token', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(identityResponse());
    vi.stubGlobal('fetch', fetchImpl);
    renderPanel();

    signIn(TOKEN);

    expect(await screen.findByText('viewer@example.invalid')).toBeInTheDocument();
    expect(screen.getByText('API token')).toBeInTheDocument();
    expect(screen.getByText('1 role')).toBeInTheDocument();
    expect(screen.getByText('2 permissions')).toBeInTheDocument();
    expect(screen.getByText('Signed in as viewer@example.invalid')).toBeInTheDocument();

    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/me');
    expect((init.headers as Headers).get('Authorization')).toBe(`Bearer ${TOKEN}`);

    expect(document.body.textContent).not.toContain(TOKEN);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain(TOKEN);
    expect(window.location.href).not.toContain(TOKEN);
  });

  it('clears state and shows a redacted toast after a 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(unauthorizedResponse()));
    renderPanel();

    signIn(TOKEN);

    expect(await screen.findByText('Token rejected')).toBeInTheDocument();
    expect(screen.getByText('The token was rejected. Sign in again.')).toBeInTheDocument();
    expect(screen.getByText('No identity details are available.')).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('Localized detail must not be shown');
    expect(document.body.textContent).not.toContain(TOKEN);
  });

  it('rejects a malformed token before making a request', async () => {
    const fetchImpl = vi.fn();
    vi.stubGlobal('fetch', fetchImpl);
    renderPanel();

    signIn('short');

    await waitFor(() =>
      expect(screen.getByText('Identity could not be verified')).toBeInTheDocument(),
    );
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('shows the verified identity in the shell header on the identity route', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(identityResponse()));
    window.location.hash = routeHash('identity');
    render(<App />);

    signIn(TOKEN);

    const statuses = await screen.findAllByText('Signed in as viewer@example.invalid');
    expect(statuses.length).toBeGreaterThanOrEqual(1);
    expect(statuses.some((node) => node.getAttribute('data-auth-status') === 'authenticated')).toBe(
      true,
    );
  });
});
