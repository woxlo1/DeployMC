import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import type { FileEntry } from '../api/files';
import { queryCache } from '../api/queryCache';
import { routeHash } from '../app/routes';

const TOKEN = 'wp43-ui-fake-token';
const ALL_PERMISSIONS = ['files.read', 'files.write', 'files.delete', 'files.upload'];
const DRAG_TYPE = 'application/x-deploymc-paths';

interface RecordedCall {
  readonly url: string;
  readonly init: RequestInit | undefined;
}

function entry(path: string, kind: FileEntry['kind'] = 'file'): FileEntry {
  return {
    path,
    kind,
    size: 12,
    revision: '"sha256:abc"',
    lastModified: '2026-11-28T00:00:00Z',
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req_files_ui01' },
  });
}

function noContent(): Response {
  return new Response(null, {
    status: 204,
    headers: { 'X-Request-Id': 'req_files_ui01' },
  });
}

function identity(permissions: readonly string[]) {
  return {
    schemaVersion: '1.0',
    identity: { id: 'owner', kind: 'token' },
    roles: ['admin'],
    permissions: [...permissions],
  };
}

function explanation(reason: string | null) {
  return {
    schemaVersion: '1.0',
    identity: { id: 'owner', kind: 'token' },
    roles: ['admin'],
    permissions: ALL_PERMISSIONS,
    explain: {
      permission: 'files.write',
      decision: reason === null ? 'allow' : 'deny',
      reason,
      path: { requested: true, resolved: true, withinScope: reason === null },
      token: { present: true, containsPermission: true },
      roles: { assigned: 1, granting: 1, denyRuleMatches: 0, allowRuleMatches: 1 },
    },
  };
}

function list(path: string, items: readonly FileEntry[]) {
  return { path, items, nextCursor: null, total: items.length };
}

function writeResult(path: string) {
  return { entry: entry(path), validation: null };
}

describe('file explorer UI', () => {
  let calls: RecordedCall[];
  let listItems: FileEntry[];
  let explainReason: string | null;
  let permissions: readonly string[];

  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.lang = 'en';
    calls = [];
    listItems = [entry('plugins', 'directory'), entry('notes.txt')];
    explainReason = null;
    permissions = ALL_PERMISSIONS;
    queryCache.clear();

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        const url = String(input);
        const method = init?.method ?? 'GET';
        calls.push({ url, init });

        if (url === '/api/v1/me') {
          return json(identity(permissions));
        }
        if (url.startsWith('/api/v1/me?explain=')) {
          return json(explanation(explainReason));
        }
        if (method === 'GET' && url.startsWith('/api/v1/files?')) {
          const path = new URL(url, 'http://localhost').searchParams.get('path') ?? '.';
          return json(list(path, path === '.' ? listItems : []));
        }
        if (method === 'POST' && url === '/api/v1/files') {
          const body = JSON.parse(String(init?.body)) as { path?: string };
          return json(writeResult(body.path ?? 'new.yml'), 201);
        }
        if (method === 'POST' && url === '/api/v1/files/move') {
          const body = JSON.parse(String(init?.body)) as { to?: string };
          return json(entry(body.to ?? 'plugins/notes.txt'));
        }
        if (method === 'DELETE' && url.startsWith('/api/v1/files?')) {
          return noContent();
        }
        if (method === 'POST' && url.startsWith('/api/v1/files/upload')) {
          return json(writeResult('plugins/sample.txt'), 201);
        }
        throw new Error(`Unexpected request: ${method} ${url}`);
      }),
    );
  });

  afterEach(() => {
    cleanup();
    queryCache.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  async function signIn(): Promise<void> {
    window.location.hash = routeHash('identity');
    render(<App />);
    fireEvent.change(screen.getByLabelText('Bearer token'), { target: { value: TOKEN } });
    fireEvent.click(screen.getByRole('button', { name: 'Use token' }));
    await waitFor(() => expect(screen.getByText('owner')).toBeInTheDocument());
    window.location.hash = routeHash('files');
    await waitFor(() => expect(screen.getByText('Sandbox files')).toBeInTheDocument());
  }

  it('shows an identity gate without a token', () => {
    window.location.hash = routeHash('files');
    render(<App />);

    expect(screen.getByText('Sign in with a token to browse files.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open identity' })).toHaveAttribute(
      'href',
      '#/identity',
    );
    expect(screen.queryByRole('treeitem')).not.toBeInTheDocument();
  });

  it('explains a read denial without showing file operations', async () => {
    permissions = ['server.read'];
    await signIn();

    expect(screen.getByText('Your identity cannot read this sandbox.')).toBeInTheDocument();
    expect(screen.queryByRole('treeitem')).not.toBeInTheDocument();
  });

  it('renders lazy-loaded tree rows with icons and restriction reasons', async () => {
    explainReason = 'PATH_SCOPE';
    await signIn();

    await waitFor(() => expect(screen.getAllByRole('treeitem')).toHaveLength(2));
    const pluginRow = screen.getByLabelText(/notes\.txt/);
    expect(pluginRow).toHaveTextContent('12 bytes');
    expect(screen.getByLabelText(/plugins — Folder/)).toBeInTheDocument();
    await waitFor(() =>
      expect(pluginRow).toHaveTextContent('This item is outside your allowed path scope.'),
    );
    expect(pluginRow).toHaveTextContent('Read-only');
    expect(calls.some((call) => call.url.includes('explain=files.write'))).toBe(true);
    expect(document.body.textContent).not.toContain(TOKEN);
  });

  it('creates a file through a validated name dialog', async () => {
    await signIn();
    await waitFor(() => expect(screen.getAllByRole('treeitem')).toHaveLength(2));

    fireEvent.click(screen.getByRole('button', { name: 'New file' }));
    const input = screen.getByRole('dialog').querySelector('input');
    expect(input).not.toBeNull();
    fireEvent.change(input as HTMLInputElement, { target: { value: 'created.yml' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(
        calls.some((call) => call.url === '/api/v1/files' && call.init?.method === 'POST'),
      ).toBe(true),
    );
    const createCall = calls.find(
      (call) => call.url === '/api/v1/files' && call.init?.method === 'POST',
    );
    expect(JSON.parse(String(createCall?.init?.body))).toEqual({
      path: 'created.yml',
      kind: 'file',
      content: '',
    });
    expect(await screen.findByText('Item created')).toBeInTheDocument();
  });

  it('requires confirmation before deleting a selected item', async () => {
    await signIn();
    await waitFor(() => expect(screen.getAllByRole('treeitem')).toHaveLength(2));

    const row = screen.getByLabelText(/notes\.txt/);
    fireEvent.contextMenu(row, { clientX: 12, clientY: 20 });
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }));

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('Delete 1 item?');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() =>
      expect(
        calls.some((call) => call.init?.method === 'DELETE' && call.url.includes('path=notes.txt')),
      ).toBe(true),
    );
    expect(await screen.findByText('Item deleted')).toBeInTheDocument();
  });

  it('moves a dragged item only after dropping it on a folder and confirming', async () => {
    await signIn();
    await waitFor(() => expect(screen.getAllByRole('treeitem')).toHaveLength(2));

    const directory = screen.getByLabelText(/plugins — Folder/);
    fireEvent.drop(directory, {
      dataTransfer: {
        getData: (type: string) => (type === DRAG_TYPE ? JSON.stringify(['notes.txt']) : ''),
        setData: vi.fn(),
        files: [],
        dropEffect: 'none',
        effectAllowed: 'none',
      },
    });

    expect(screen.getByRole('dialog')).toHaveTextContent('Move 1 item to plugins?');
    fireEvent.click(screen.getByRole('button', { name: 'Move' }));

    await waitFor(() => expect(calls.some((call) => call.url === '/api/v1/files/move')).toBe(true));
    const moveCall = calls.find((call) => call.url === '/api/v1/files/move');
    expect(JSON.parse(String(moveCall?.init?.body))).toEqual({
      from: 'notes.txt',
      to: 'plugins/notes.txt',
      overwrite: false,
    });
  });

  it('uploads from the toolbar and shows a local read progress state', async () => {
    await signIn();
    await waitFor(() => expect(screen.getAllByRole('treeitem')).toHaveLength(2));

    const input = document.querySelector('input[type="file"]');
    expect(input).not.toBeNull();
    const file = new File(['sample'], 'sample.txt', { type: 'text/plain' });
    Object.defineProperty(input as HTMLInputElement, 'files', {
      configurable: true,
      value: [file],
    });
    fireEvent.change(input as HTMLInputElement);

    await waitFor(() =>
      expect(
        calls.some((call) => call.url.startsWith('/api/v1/files/upload?path=sample.txt')),
      ).toBe(true),
    );
    expect(await screen.findByText('Uploaded sample.txt')).toBeInTheDocument();
    expect(await screen.findByText('File uploaded')).toBeInTheDocument();
    expect(input).toHaveValue('');
  });
});
