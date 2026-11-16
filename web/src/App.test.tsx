import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { App } from './App';

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.lang = 'en';
  });

  it('renders the English catalog by default', () => {
    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'DeployMC' })).toBeInTheDocument();
    expect(
      screen.getByText('Secure, collaborative plugin deployment for Minecraft servers.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Pre-alpha');
    expect(screen.getByText('3 plugins')).toBeInTheDocument();
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
});
