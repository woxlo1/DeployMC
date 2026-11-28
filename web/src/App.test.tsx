import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { App } from './App';
import { ROUTES, routeHash } from './app/routes';
import { THEME_STORAGE_KEY } from './app/theme';

describe('App shell', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.lang = 'en';
    delete document.documentElement.dataset.theme;
    document.documentElement.style.colorScheme = '';
    window.location.hash = routeHash('dashboard');
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the English shell layout and default dashboard', () => {
    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'DeployMC' })).toBeInTheDocument();
    expect(
      screen.getByText('Secure, collaborative plugin deployment for Minecraft servers.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Pre-alpha');
    expect(screen.getByText('3 plugins')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Files' })).toHaveAttribute('href', '#/files');
    expect(document.documentElement.dataset.theme).toBe('system');
  });

  it('switches to Japanese and persists the choice', () => {
    render(<App />);

    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'ja' },
    });

    expect(
      screen.getByText('Minecraft サーバー向けの、安全で協調的なプラグイン配備ツール。'),
    ).toBeInTheDocument();
    expect(screen.getByText('3 個のプラグイン')).toBeInTheDocument();
    expect(window.localStorage.getItem('deploymc.locale')).toBe('ja');
    expect(document.documentElement.lang).toBe('ja');
  });

  it('restores a stored locale', () => {
    window.localStorage.setItem('deploymc.locale', 'ja');

    render(<App />);

    expect(screen.getByText('プレアルファ：基盤開発中です。')).toBeInTheDocument();
  });

  it('persists a manual theme override', () => {
    render(<App />);

    fireEvent.change(screen.getByRole('combobox', { name: 'Theme' }), {
      target: { value: 'dark' },
    });

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('supports loading, empty, and error previews for every screen', () => {
    for (const route of ROUTES) {
      for (const state of ['loading', 'empty', 'error'] as const) {
        window.location.hash = routeHash(route.id, state);
        render(<App />);

        expect(
          document.querySelector(`[data-route="${route.id}"] [data-view-state="${state}"]`),
        ).not.toBeNull();

        if (state === 'loading') {
          expect(screen.getByText('Loading…')).toBeInTheDocument();
        } else if (state === 'empty') {
          expect(screen.getByText('Nothing to show yet')).toBeInTheDocument();
        } else {
          expect(screen.getByText('This view could not load')).toBeInTheDocument();
          expect(screen.getByText(/Request ID: req_/)).toBeInTheDocument();
        }
        cleanup();
      }
    }
  });

  it('shows an explicit invalid-route view and can return to the dashboard', async () => {
    window.location.hash = '#/missing';

    render(<App />);

    expect(screen.getByRole('heading', { name: 'Unknown section' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Go to dashboard' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    });
  });

  it('opens the shortcut dialog with ? and closes it with Escape', async () => {
    render(<App />);

    fireEvent.keyDown(document, { key: '?', code: 'Slash', shiftKey: true });

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Keyboard shortcuts' })).toBeInTheDocument();
    expect(screen.getByText('alt+2')).toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('restores focus after closing the shortcut dialog', () => {
    render(<App />);
    const openButton = screen.getByRole('button', { name: 'Open keyboard shortcuts' });
    openButton.focus();

    fireEvent.click(openButton);
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();

    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(openButton).toHaveFocus();
  });

  it('navigates with an Alt plus digit shortcut', async () => {
    render(<App />);

    fireEvent.keyDown(document, { key: '2', code: 'Digit2', altKey: true });

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Files' })).toBeInTheDocument();
    });
    expect(window.location.hash).toBe('#/files');
  });

  it('shows and dismisses a global toast', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Show notification' }));
    expect(screen.getByText('The interface shell is ready.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByText('The interface shell is ready.')).not.toBeInTheDocument();
  });
});
