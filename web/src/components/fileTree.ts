import { basename, type FileEntry } from '../api/files';

export interface TreeRow {
  readonly entry: FileEntry;
  readonly parent: string;
  readonly depth: number;
}

export type EntriesByParent = Readonly<Record<string, readonly FileEntry[]>>;
export type FileIconKind = 'directory' | 'archive' | 'jar' | 'config' | 'text' | 'file';

export const TREE_ROW_HEIGHT = 44;
export const TREE_OVERSCAN = 4;

const CONFIG_EXTENSIONS = new Set(['yml', 'yaml', 'json', 'toml', 'properties', 'conf', 'ini']);
const TEXT_EXTENSIONS = new Set(['txt', 'md', 'log', 'yml', 'yaml', 'json', 'toml', 'properties']);
const ARCHIVE_EXTENSIONS = new Set(['zip', 'tar', 'gz', 'tgz', 'rar', '7z']);

/** Builds a deterministic, cycle-safe row list from already validated entries. */
export function buildTreeRows(
  entriesByParent: EntriesByParent,
  expanded: ReadonlySet<string>,
): readonly TreeRow[] {
  const rows: TreeRow[] = [];
  const visited = new Set<string>();

  function visit(parent: string, depth: number): void {
    if (visited.has(parent)) {
      return;
    }
    visited.add(parent);
    const entries = [...(entriesByParent[parent] ?? [])].sort((left, right) => {
      if (left.kind !== right.kind) {
        return left.kind === 'directory' ? -1 : 1;
      }
      return basename(left.path).localeCompare(basename(right.path));
    });
    for (const entry of entries) {
      rows.push({ entry, parent, depth });
      if (entry.kind === 'directory' && expanded.has(entry.path)) {
        visit(entry.path, depth + 1);
      }
    }
  }

  visit('.', 0);
  return rows;
}

export interface VirtualWindow {
  readonly start: number;
  readonly end: number;
}

/** Returns a bounded render window for a fixed-height virtualized list. */
export function virtualWindow(
  count: number,
  scrollTop: number,
  viewportHeight: number,
  rowHeight: number = TREE_ROW_HEIGHT,
  overscan: number = TREE_OVERSCAN,
): VirtualWindow {
  if (count <= 0) {
    return { start: 0, end: 0 };
  }
  const safeScroll = Number.isFinite(scrollTop) && scrollTop > 0 ? scrollTop : 0;
  const safeViewport = Number.isFinite(viewportHeight) && viewportHeight > 0 ? viewportHeight : 320;
  const start = Math.min(count, Math.max(0, Math.floor(safeScroll / rowHeight) - overscan));
  const end = Math.min(
    count,
    Math.max(start, Math.ceil((safeScroll + safeViewport) / rowHeight) + overscan),
  );
  return { start, end };
}

export function fileIconKind(entry: FileEntry): FileIconKind {
  if (entry.kind === 'directory') {
    return 'directory';
  }
  const name = basename(entry.path).toLowerCase();
  const dot = name.lastIndexOf('.');
  const extension = dot <= 0 ? '' : name.slice(dot + 1);
  if (extension === 'jar') {
    return 'jar';
  }
  if (ARCHIVE_EXTENSIONS.has(extension)) {
    return 'archive';
  }
  if (CONFIG_EXTENSIONS.has(extension)) {
    return 'config';
  }
  if (TEXT_EXTENSIONS.has(extension)) {
    return 'text';
  }
  return 'file';
}

export interface SelectionModifiers {
  readonly additive: boolean;
  readonly range: boolean;
}

/** Applies single, additive, and shift-range selection to an ordered row list. */
export function updateSelection(
  current: ReadonlySet<string>,
  rows: readonly TreeRow[],
  anchorPath: string | null,
  targetPath: string,
  modifiers: SelectionModifiers,
): { readonly selected: ReadonlySet<string>; readonly anchor: string } {
  if (modifiers.range && anchorPath !== null) {
    const anchorIndex = rows.findIndex((row) => row.entry.path === anchorPath);
    const targetIndex = rows.findIndex((row) => row.entry.path === targetPath);
    if (anchorIndex >= 0 && targetIndex >= 0) {
      const [from, to] =
        anchorIndex <= targetIndex ? [anchorIndex, targetIndex] : [targetIndex, anchorIndex];
      const next = modifiers.additive ? new Set(current) : new Set<string>();
      for (let index = from; index <= to; index += 1) {
        const path = rows[index]?.entry.path;
        if (path !== undefined) {
          next.add(path);
        }
      }
      return { selected: next, anchor: anchorPath };
    }
  }
  if (modifiers.additive) {
    const next = new Set(current);
    if (next.has(targetPath)) {
      next.delete(targetPath);
    } else {
      next.add(targetPath);
    }
    return { selected: next, anchor: targetPath };
  }
  return { selected: new Set([targetPath]), anchor: targetPath };
}
