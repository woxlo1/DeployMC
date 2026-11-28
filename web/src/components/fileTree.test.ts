import { describe, expect, it } from 'vitest';
import type { FileEntry } from '../api/files';
import {
  TREE_OVERSCAN,
  TREE_ROW_HEIGHT,
  buildTreeRows,
  fileIconKind,
  updateSelection,
  virtualWindow,
} from './fileTree';

function entry(path: string, kind: FileEntry['kind'] = 'file'): FileEntry {
  return {
    path,
    kind,
    size: 0,
    revision: '"sha256:abc"',
    lastModified: '2026-11-28T00:00:00Z',
  };
}

describe('file tree rows', () => {
  it('orders directories first and expands only requested parents', () => {
    const rows = buildTreeRows(
      {
        '.': [entry('z.yml'), entry('plugins', 'directory')],
        plugins: [entry('plugins/plugin.yml')],
      },
      new Set(['plugins']),
    );

    expect(rows.map((row) => row.entry.path)).toEqual(['plugins', 'plugins/plugin.yml', 'z.yml']);
    expect(rows.map((row) => row.depth)).toEqual([0, 1, 0]);
    expect(rows[1]?.parent).toBe('plugins');
  });

  it('stops at a cyclic parent reference', () => {
    const rows = buildTreeRows(
      {
        '.': [entry('loop', 'directory')],
        loop: [entry('loop', 'directory')],
      },
      new Set(['loop']),
    );
    expect(rows.map((row) => row.entry.path)).toEqual(['loop', 'loop']);
  });
});

describe('virtual window', () => {
  it('returns a bounded overscanned window', () => {
    const result = virtualWindow(100, 440, 88, TREE_ROW_HEIGHT, TREE_OVERSCAN);
    expect(result).toEqual({ start: 6, end: 16 });
  });

  it('clamps invalid and out-of-range input', () => {
    expect(virtualWindow(0, 0, 320)).toEqual({ start: 0, end: 0 });
    expect(virtualWindow(2, Number.NaN, -1)).toEqual({ start: 0, end: 2 });
    expect(virtualWindow(3, 10_000, 320)).toEqual({ start: 3, end: 3 });
  });
});

describe('file icons', () => {
  it('categorizes names without inspecting content', () => {
    expect(fileIconKind(entry('plugins', 'directory'))).toBe('directory');
    expect(fileIconKind(entry('plugin.jar'))).toBe('jar');
    expect(fileIconKind(entry('bundle.zip'))).toBe('archive');
    expect(fileIconKind(entry('server.yml'))).toBe('config');
    expect(fileIconKind(entry('readme.md'))).toBe('text');
    expect(fileIconKind(entry('binary'))).toBe('file');
  });
});

describe('selection', () => {
  const rows = buildTreeRows({ '.': [entry('a.yml'), entry('b.yml'), entry('c.yml')] }, new Set());

  it('applies single, additive, and range selection', () => {
    const single = updateSelection(new Set(), rows, null, 'a.yml', {
      additive: false,
      range: false,
    });
    expect([...single.selected]).toEqual(['a.yml']);

    const additive = updateSelection(single.selected, rows, single.anchor, 'c.yml', {
      additive: true,
      range: false,
    });
    expect([...additive.selected]).toEqual(['a.yml', 'c.yml']);

    const range = updateSelection(new Set(), rows, 'a.yml', 'c.yml', {
      additive: false,
      range: true,
    });
    expect([...range.selected]).toEqual(['a.yml', 'b.yml', 'c.yml']);
    expect(range.anchor).toBe('a.yml');
  });

  it('toggles an additive selection and ignores an unknown range anchor', () => {
    const toggled = updateSelection(new Set(['a.yml']), rows, 'a.yml', 'a.yml', {
      additive: true,
      range: false,
    });
    expect(toggled.selected.size).toBe(0);

    const ignored = updateSelection(new Set(['a.yml']), rows, 'missing', 'b.yml', {
      additive: false,
      range: true,
    });
    expect([...ignored.selected]).toEqual(['b.yml']);
  });
});
