import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type FormEvent,
  type KeyboardEvent,
  type MouseEvent as ReactMouseEvent,
} from 'react';
import type { ApiFailureReason, ApiRequestError } from '../api/client';
import {
  DIRECTORY_DEPTH_LIMIT,
  ROOT_PATH,
  basename,
  breadcrumbSegments,
  createEntry,
  deleteEntry,
  explainPermission,
  invalidateFileCache,
  isFileRequestError,
  isWithin,
  isValidRelativePath,
  joinPath,
  listFilesPage,
  moveEntry,
  parentPath,
  uploadEntry,
  type Explanation,
  type ExplanationReason,
  type FileEntry,
  type FileList,
  type UploadProgress,
} from '../api/files';
import { useAuth } from '../auth/store';
import type { Translator } from '../i18n';
import { FILE_KEYS, SHELL_KEYS } from '../i18n/keys';
import { queryCache } from '../api/queryCache';
import { useToast } from '../toast/context';
import { ConflictNotice } from './ConflictNotice';
import {
  TREE_ROW_HEIGHT,
  buildTreeRows,
  fileIconKind,
  updateSelection,
  virtualWindow,
  type EntriesByParent,
  type FileIconKind,
  type TreeRow,
} from './fileTree';

interface FileExplorerProps {
  readonly t: Translator;
}

interface NameDialog {
  readonly mode: 'create-file' | 'create-folder' | 'rename';
  readonly parent: string;
  readonly source?: FileEntry;
  readonly value: string;
}

interface ConfirmDialog {
  readonly mode: 'delete' | 'move';
  readonly paths: readonly string[];
  readonly target?: string;
}

interface ContextMenu {
  readonly x: number;
  readonly y: number;
  readonly path: string;
}

interface UploadState {
  readonly id: string;
  readonly name: string;
  readonly phase: UploadProgress['phase'] | 'done' | 'failed' | 'canceled';
  readonly loaded: number;
  readonly total: number;
  readonly messageKey?: string;
}

const ICONS: Readonly<Record<FileIconKind, string>> = {
  directory: '▸',
  jar: '◆',
  archive: '▣',
  config: '⚙',
  text: '≡',
  file: '•',
};

const ICON_LABELS: Readonly<Record<FileIconKind, string>> = {
  directory: FILE_KEYS.iconDirectory,
  jar: FILE_KEYS.iconJar,
  archive: FILE_KEYS.iconArchive,
  config: FILE_KEYS.iconConfig,
  text: FILE_KEYS.iconText,
  file: FILE_KEYS.iconFile,
};

const REASON_KEYS: Readonly<Record<ExplanationReason, string>> = {
  NO_ROLE: FILE_KEYS.reasonNoRole,
  NOT_GRANTED: FILE_KEYS.reasonNotGranted,
  TOKEN_SCOPE_EXCEEDED: FILE_KEYS.reasonTokenScope,
  PATH_DENIED: FILE_KEYS.reasonPathDenied,
  PATH_SCOPE: FILE_KEYS.reasonPathScope,
};

const ERROR_KEYS: Readonly<Record<ApiFailureReason, string>> = {
  unauthorized: FILE_KEYS.errorUnauthorized,
  forbidden: FILE_KEYS.errorForbidden,
  conflict: FILE_KEYS.errorConflict,
  'precondition-required': FILE_KEYS.errorConflict,
  'rate-limited': FILE_KEYS.errorRateLimited,
  'server-error': FILE_KEYS.errorGeneric,
  'network-error': FILE_KEYS.errorNetwork,
  'invalid-response': FILE_KEYS.errorInvalidResponse,
  aborted: FILE_KEYS.errorAborted,
};

const DRAG_TYPE = 'application/x-deploymc-paths';
const MAX_SEARCH_RESULTS = 200;
const MAX_SELECTION = 50;
const MAX_UPLOADS = 8;
const MAX_EXPLANATIONS = 32;
const CACHE_TTL_MS = 30_000;

function listParams(path: string) {
  return { path, depth: 1, limit: 50 } as const;
}

function errorKey(error: unknown): string {
  return isFileRequestError(error) ? ERROR_KEYS[error.reason] : FILE_KEYS.errorGeneric;
}

function readDragPaths(event: DragEvent): readonly string[] {
  const raw = event.dataTransfer?.getData(DRAG_TYPE);
  if (raw === undefined || raw === '' || raw.length > 8192) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((value): value is string => typeof value === 'string' && isValidRelativePath(value))
      .slice(0, MAX_SELECTION);
  } catch {
    return [];
  }
}

function reasonLabel(reason: ExplanationReason | null, t: Translator): string {
  return reason === null ? t(FILE_KEYS.reasonUnknown) : t(REASON_KEYS[reason]);
}

export function FileExplorer({ t }: FileExplorerProps) {
  const auth = useAuth();
  const toast = useToast();
  const [currentPath, setCurrentPath] = useState<string>(ROOT_PATH);
  const [entriesByParent, setEntriesByParent] = useState<EntriesByParent>({});
  const [loadingPaths, setLoadingPaths] = useState<ReadonlySet<string>>(new Set());
  const [pathErrors, setPathErrors] = useState<Readonly<Record<string, ApiRequestError | null>>>(
    {},
  );
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set());
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [anchor, setAnchor] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [searchResults, setSearchResults] = useState<readonly FileEntry[] | null>(null);
  const [searchError, setSearchError] = useState<ApiRequestError | null>(null);
  const [menu, setMenu] = useState<ContextMenu | null>(null);
  const [nameDialog, setNameDialog] = useState<NameDialog | null>(null);
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialog | null>(null);
  const [conflict, setConflict] = useState<ApiRequestError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [explanations, setExplanations] = useState<Readonly<Record<string, Explanation>>>({});
  const [uploads, setUploads] = useState<readonly UploadState[]>([]);
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(320);
  const viewportRef = useRef<HTMLDivElement>(null);
  const uploadControllers = useRef(new Map<string, AbortController>());

  const permissions = auth.identity?.permissions ?? [];
  const canRead = permissions.includes('files.read');
  const canWrite = permissions.includes('files.write');
  const canDelete = permissions.includes('files.delete');
  const canUpload = permissions.includes('files.upload');
  const authenticated = auth.status === 'authenticated' && auth.hasToken && auth.token !== null;

  const handleError = useCallback(
    (error: unknown): void => {
      if (isFileRequestError(error)) {
        if (error.reason === 'unauthorized') {
          auth.reportUnauthorized();
        }
        if (error.reason === 'conflict') {
          setConflict(error);
        }
        if (error.reason !== 'aborted') {
          setNotice(errorKey(error));
        }
      } else {
        setNotice(FILE_KEYS.errorGeneric);
      }
    },
    [auth],
  );

  const loadDirectory = useCallback(
    async (path: string, refresh = false): Promise<void> => {
      if (!authenticated || !canRead || auth.token === null || !isValidRelativePath(path)) {
        return;
      }
      if (refresh) {
        invalidateFileCache(auth.scope);
      }
      setLoadingPaths((previous) => new Set(previous).add(path));
      try {
        const data = await queryCache.read<FileList>(
          auth.scope,
          'listFiles',
          listParams(path),
          () => listFilesPage(auth.token as string, path, { limit: 50 }),
          CACHE_TTL_MS,
        );
        setEntriesByParent((previous) => ({ ...previous, [path]: data.items }));
        setPathErrors((previous) => ({ ...previous, [path]: null }));
      } catch (error) {
        setPathErrors((previous) => ({
          ...previous,
          [path]: isFileRequestError(error) ? error : null,
        }));
        handleError(error);
      } finally {
        setLoadingPaths((previous) => {
          const next = new Set(previous);
          next.delete(path);
          return next;
        });
      }
    },
    [auth.scope, auth.token, authenticated, canRead, handleError],
  );

  useEffect(() => {
    if (authenticated && canRead) {
      void loadDirectory(currentPath);
    }
  }, [authenticated, canRead, currentPath, loadDirectory]);

  useEffect(() => {
    const element = viewportRef.current;
    if (element === null) {
      return;
    }
    setViewportHeight(element.clientHeight > 0 ? element.clientHeight : 320);
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const height = entries[0]?.contentRect.height;
      if (height !== undefined && height > 0) {
        setViewportHeight(height);
      }
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(
    () => () => {
      for (const controller of uploadControllers.current.values()) {
        controller.abort();
      }
      uploadControllers.current.clear();
    },
    [],
  );

  const normalizedSearch = search.trim().toLocaleLowerCase();
  useEffect(() => {
    if (normalizedSearch === '') {
      setSearchResults(null);
      setSearchError(null);
      return;
    }
    if (!authenticated || !canRead || auth.token === null) {
      return;
    }
    let active = true;
    const token = auth.token;
    queryCache
      .read<FileList>(
        auth.scope,
        'listFiles',
        { path: ROOT_PATH, depth: DIRECTORY_DEPTH_LIMIT, limit: 200 },
        () => listFilesPage(token, ROOT_PATH, { depth: DIRECTORY_DEPTH_LIMIT, limit: 200 }),
        CACHE_TTL_MS,
      )
      .then((data) => {
        if (!active) {
          return;
        }
        const matches = data.items
          .filter((entry) => basename(entry.path).toLocaleLowerCase().includes(normalizedSearch))
          .slice(0, MAX_SEARCH_RESULTS);
        setSearchResults(matches);
        setSearchError(null);
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }
        setSearchResults([]);
        setSearchError(isFileRequestError(error) ? error : null);
        handleError(error);
      });
    return () => {
      active = false;
    };
  }, [auth.scope, auth.token, authenticated, canRead, normalizedSearch, handleError]);

  const treeRows = useMemo(
    () => buildTreeRows(entriesByParent, expanded),
    [entriesByParent, expanded],
  );
  const rows = useMemo<readonly TreeRow[]>(() => {
    if (searchResults === null) {
      return treeRows;
    }
    return searchResults.map((entry) => ({
      entry,
      parent: parentPath(entry.path),
      depth: 0,
    }));
  }, [searchResults, treeRows]);

  const entryIndex = useMemo(() => {
    const index = new Map<string, FileEntry>();
    for (const row of rows) {
      index.set(row.entry.path, row.entry);
    }
    return index;
  }, [rows]);

  const window = virtualWindow(rows.length, scrollTop, viewportHeight);
  const visibleRows = useMemo(
    () => rows.slice(window.start, window.end),
    [rows, window.start, window.end],
  );
  const visibleKey = visibleRows.map((row) => row.entry.path).join('|');

  useEffect(() => {
    if (!authenticated || !canWrite || auth.token === null || visibleKey === '') {
      return;
    }
    let active = true;
    const token = auth.token;
    for (const path of visibleKey.split('|').slice(0, MAX_EXPLANATIONS)) {
      if (explanations[path] !== undefined) {
        continue;
      }
      void queryCache
        .read<Explanation>(
          auth.scope,
          'getCurrentIdentity',
          { explain: 'files.write', path },
          () => explainPermission(token, 'files.write', path),
          CACHE_TTL_MS,
        )
        .then((explanation) => {
          if (active) {
            setExplanations((previous) => ({ ...previous, [path]: explanation }));
          }
        })
        .catch(() => undefined);
    }
    return () => {
      active = false;
    };
  }, [auth.scope, auth.token, authenticated, canWrite, explanations, visibleKey]);

  useEffect(() => {
    if (menu === null) {
      return;
    }
    const close = () => setMenu(null);
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [menu]);

  const refreshAffected = useCallback(
    (paths: readonly string[]) => {
      invalidateFileCache(auth.scope);
      const parents = new Set(paths.map((path) => parentPath(path)));
      if (currentPath !== ROOT_PATH) {
        parents.add(currentPath);
      }
      for (const parent of parents) {
        void loadDirectory(parent, true);
      }
      if (currentPath !== ROOT_PATH) {
        void loadDirectory(currentPath, true);
      }
    },
    [auth.scope, currentPath, loadDirectory],
  );

  function openCreate(mode: 'create-file' | 'create-folder', parent: string): void {
    if (!canWrite || !isValidRelativePath(parent)) {
      return;
    }
    setNameDialog({ mode, parent, value: '' });
  }

  function openRename(entry: FileEntry): void {
    if (!canWrite) {
      return;
    }
    setNameDialog({
      mode: 'rename',
      parent: parentPath(entry.path),
      source: entry,
      value: basename(entry.path),
    });
  }

  function openDelete(paths: readonly string[]): void {
    if (!canDelete || paths.length === 0) {
      return;
    }
    setConfirmDialog({ mode: 'delete', paths: paths.slice(0, MAX_SELECTION) });
  }

  function openMove(paths: readonly string[], target: string): void {
    if (!canWrite || !isValidRelativePath(target) || paths.length === 0) {
      return;
    }
    const candidates = paths.filter((path) => isValidRelativePath(path) && !isWithin(target, path));
    if (candidates.length === 0) {
      return;
    }
    setConfirmDialog({
      mode: 'move',
      paths: candidates.slice(0, MAX_SELECTION),
      target,
    });
  }

  async function submitNameDialog(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (nameDialog === null || auth.token === null) {
      return;
    }
    const destination = joinPath(nameDialog.parent, nameDialog.value);
    if (destination === null) {
      setNotice(FILE_KEYS.invalidName);
      return;
    }
    try {
      if (nameDialog.mode === 'rename' && nameDialog.source !== undefined) {
        const ifMatch = nameDialog.source.revision ?? undefined;
        await moveEntry(auth.token, nameDialog.source.path, destination, { ifMatch });
        toast.show({ messageKey: FILE_KEYS.renamed, tone: 'success' });
        refreshAffected([nameDialog.source.path, destination]);
      } else {
        await createEntry(
          auth.token,
          destination,
          nameDialog.mode === 'create-folder' ? 'directory' : 'file',
        );
        toast.show({ messageKey: FILE_KEYS.created, tone: 'success' });
        refreshAffected([destination]);
      }
      setNameDialog(null);
      setNotice(null);
      setConflict(null);
    } catch (error) {
      handleError(error);
    }
  }

  async function submitConfirmDialog(): Promise<void> {
    if (confirmDialog === null || auth.token === null) {
      return;
    }
    try {
      if (confirmDialog.mode === 'delete') {
        for (const path of confirmDialog.paths) {
          const entry = entryIndex.get(path);
          if (entry?.kind === 'directory') {
            const listing = await listFilesPage(auth.token, path, { limit: 1 });
            await deleteEntry(auth.token, path, {
              recursive: true,
              expectedEntryCount: listing.total,
            });
          } else {
            await deleteEntry(auth.token, path);
          }
        }
        toast.show({ messageKey: FILE_KEYS.deleted, tone: 'success' });
      } else if (confirmDialog.target !== undefined) {
        for (const path of confirmDialog.paths) {
          const destination = joinPath(confirmDialog.target, basename(path));
          if (destination === null) {
            continue;
          }
          await moveEntry(auth.token, path, destination, {
            ifMatch: entryIndex.get(path)?.revision ?? undefined,
          });
        }
        toast.show({ messageKey: FILE_KEYS.moved, tone: 'success' });
      }
      refreshAffected(confirmDialog.paths);
      setConfirmDialog(null);
      setNotice(null);
      setConflict(null);
    } catch (error) {
      handleError(error);
    }
  }

  async function uploadFiles(files: readonly File[], directory: string): Promise<void> {
    if (!canUpload || auth.token === null || !isValidRelativePath(directory)) {
      return;
    }
    for (const file of files.slice(0, MAX_UPLOADS)) {
      const id = `upload-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
      const target = joinPath(directory, file.name);
      const controller = new AbortController();
      uploadControllers.current.set(id, controller);
      if (target === null) {
        setUploads((previous) => [
          ...previous,
          {
            id,
            name: file.name,
            phase: 'failed',
            loaded: 0,
            total: file.size,
            messageKey: FILE_KEYS.invalidName,
          },
        ]);
        uploadControllers.current.delete(id);
        continue;
      }
      setUploads((previous) => [
        ...previous,
        { id, name: file.name, phase: 'reading', loaded: 0, total: file.size },
      ]);
      try {
        await uploadEntry(auth.token, target, file, {
          signal: controller.signal,
          onProgress: (progress: UploadProgress) => {
            setUploads((previous) =>
              previous.map((upload) => (upload.id === id ? { ...upload, ...progress } : upload)),
            );
          },
        });
        setUploads((previous) =>
          previous.map((upload) =>
            upload.id === id
              ? { ...upload, phase: 'done', messageKey: FILE_KEYS.uploadDone }
              : upload,
          ),
        );
        toast.show({ messageKey: FILE_KEYS.uploaded, tone: 'success' });
        refreshAffected([target]);
      } catch (error) {
        const aborted = isFileRequestError(error) && error.reason === 'aborted';
        setUploads((previous) =>
          previous.map((upload) =>
            upload.id === id
              ? {
                  ...upload,
                  phase: aborted ? 'canceled' : 'failed',
                  messageKey: aborted ? FILE_KEYS.uploadCanceled : errorKey(error),
                }
              : upload,
          ),
        );
        if (!aborted) {
          handleError(error);
        }
      } finally {
        uploadControllers.current.delete(id);
      }
    }
  }

  function handleFiles(event: ChangeEvent<HTMLInputElement>): void {
    const files = Array.from(event.target.files ?? []);
    event.target.value = '';
    void uploadFiles(files, currentPath);
  }

  function handleRowClick(event: ReactMouseEvent, row: TreeRow): void {
    setMenu(null);
    const next = updateSelection(selected, rows, anchor, row.entry.path, {
      additive: event.ctrlKey || event.metaKey,
      range: event.shiftKey,
    });
    setSelected(next.selected);
    setAnchor(next.anchor);
    if (row.entry.kind === 'directory' && !event.ctrlKey && !event.metaKey && !event.shiftKey) {
      toggleDirectory(row.entry.path);
    }
  }

  function toggleDirectory(path: string): void {
    setExpanded((previous) => {
      const next = new Set(previous);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
        void loadDirectory(path);
      }
      return next;
    });
  }

  function handleContextMenu(event: ReactMouseEvent, path: string): void {
    event.preventDefault();
    if (!selected.has(path)) {
      setSelected(new Set([path]));
      setAnchor(path);
    }
    setMenu({ x: event.clientX, y: event.clientY, path });
  }

  function handleRowKeyDown(event: KeyboardEvent<HTMLDivElement>, row: TreeRow): void {
    if (event.key === 'Enter' && row.entry.kind === 'directory') {
      event.preventDefault();
      toggleDirectory(row.entry.path);
    }
    if (event.key === 'ContextMenu' || (event.key === 'F10' && event.shiftKey)) {
      event.preventDefault();
      setMenu({ x: 0, y: 0, path: row.entry.path });
    }
  }

  function handleDragStart(event: DragEvent<HTMLDivElement>, path: string): void {
    const paths = selected.has(path) ? [...selected] : [path];
    event.dataTransfer.setData(DRAG_TYPE, JSON.stringify(paths.slice(0, MAX_SELECTION)));
    event.dataTransfer.effectAllowed = 'move';
  }

  function handleDrop(event: DragEvent, directory: string): void {
    event.preventDefault();
    event.stopPropagation();
    setMenu(null);
    const dragged = readDragPaths(event);
    if (dragged.length > 0) {
      openMove(dragged, directory);
      return;
    }
    const files = Array.from(event.dataTransfer?.files ?? []);
    if (files.length > 0) {
      void uploadFiles(files, directory);
    }
  }

  function handleBreadcrumb(path: string): void {
    setSearch('');
    setCurrentPath(path);
    setSelected(new Set());
    setAnchor(null);
    setScrollTop(0);
  }

  const rootError = pathErrors[currentPath] ?? null;
  const loading = loadingPaths.has(currentPath);
  const restrictionFor = (path: string): { badge: string; reason: string } | null => {
    const explanation = explanations[path];
    const reason = explanation?.reason ?? (canWrite ? null : 'NOT_GRANTED');
    if (reason === null || explanation?.decision === 'allow') {
      if (!canWrite && explanation === undefined) {
        return { badge: FILE_KEYS.badgeReadonly, reason: reasonLabel('NOT_GRANTED', t) };
      }
      return null;
    }
    return {
      badge: reason === 'PATH_DENIED' ? FILE_KEYS.badgeProtected : FILE_KEYS.badgeReadonly,
      reason: reasonLabel(reason, t),
    };
  };

  return (
    <section className="file-explorer" aria-labelledby="file-explorer-heading">
      <h3 id="file-explorer-heading">{t(FILE_KEYS.heading)}</h3>

      {!authenticated ? (
        <div className="file-explorer__gate" role="status">
          <p>{t(FILE_KEYS.signInRequired)}</p>
          <a href="#/identity">{t(FILE_KEYS.signInLink)}</a>
        </div>
      ) : !canRead ? (
        <p className="file-explorer__gate" role="alert">
          {t(FILE_KEYS.readDenied)}
        </p>
      ) : (
        <>
          <div className="file-explorer__toolbar">
            <label className="file-explorer__search">
              <span>{t(FILE_KEYS.searchLabel)}</span>
              <input
                type="search"
                value={search}
                placeholder={t(FILE_KEYS.searchPlaceholder)}
                onChange={(event) => {
                  setSearch(event.target.value);
                  setScrollTop(0);
                }}
              />
            </label>
            <button type="button" onClick={() => void loadDirectory(currentPath, true)}>
              {t(FILE_KEYS.refresh)}
            </button>
            <button
              type="button"
              disabled={!canWrite}
              onClick={() => openCreate('create-file', currentPath)}
            >
              {t(FILE_KEYS.newFile)}
            </button>
            <button
              type="button"
              disabled={!canWrite}
              onClick={() => openCreate('create-folder', currentPath)}
            >
              {t(FILE_KEYS.newFolder)}
            </button>
            <label className="file-explorer__upload">
              <span>{t(FILE_KEYS.uploadButton)}</span>
              <input
                type="file"
                multiple
                disabled={!canUpload}
                onChange={handleFiles}
                aria-label={t(FILE_KEYS.uploadButton)}
              />
            </label>
          </div>

          {searchResults === null ? (
            <nav className="file-explorer__breadcrumbs" aria-label={t(FILE_KEYS.root)}>
              <button type="button" onClick={() => handleBreadcrumb(ROOT_PATH)}>
                {t(FILE_KEYS.root)}
              </button>
              {breadcrumbSegments(currentPath).map((path) => (
                <span key={path}>
                  <span aria-hidden="true">/</span>
                  <button type="button" onClick={() => handleBreadcrumb(path)}>
                    {basename(path)}
                  </button>
                </span>
              ))}
            </nav>
          ) : (
            <p className="file-explorer__search-summary">
              {t(FILE_KEYS.searchLabel)}: {search}
            </p>
          )}

          {selected.size > 0 ? (
            <div className="file-explorer__selection">
              <span>{t(FILE_KEYS.selected, { count: selected.size })}</span>
              <button type="button" onClick={() => setSelected(new Set())}>
                {t(FILE_KEYS.clearSelection)}
              </button>
              <button type="button" disabled={!canDelete} onClick={() => openDelete([...selected])}>
                {t(FILE_KEYS.menuDelete)}
              </button>
            </div>
          ) : null}

          <ConflictNotice
            error={conflict}
            t={t}
            onReload={() => void loadDirectory(currentPath, true)}
            onDismiss={() => setConflict(null)}
          />

          {notice === null ? null : (
            <p className="file-explorer__notice" role="alert">
              {t(notice)}
            </p>
          )}

          {rootError === null ? null : (
            <div className="file-explorer__error" role="alert">
              <p>{t(FILE_KEYS.errorTitle)}</p>
              <p>{t(FILE_KEYS.errorBody)}</p>
              {rootError.requestId === null ? null : (
                <p>{t(SHELL_KEYS.requestId, { requestId: rootError.requestId })}</p>
              )}
              <button type="button" onClick={() => void loadDirectory(currentPath, true)}>
                {t(FILE_KEYS.retry)}
              </button>
            </div>
          )}

          <p className="file-explorer__hint">{t(FILE_KEYS.dropHint)}</p>
          <p className="file-explorer__hint">{t(FILE_KEYS.dropMoveHint)}</p>

          <div
            ref={viewportRef}
            className="file-explorer__viewport"
            onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => handleDrop(event, currentPath)}
          >
            {loading && rows.length === 0 ? (
              <p className="file-explorer__state" role="status">
                {t(FILE_KEYS.loading)}
              </p>
            ) : null}
            {!loading && rows.length === 0 && rootError === null ? (
              <div className="file-explorer__state">
                <p>{searchResults !== null ? t(FILE_KEYS.searchLabel) : t(FILE_KEYS.emptyTitle)}</p>
                <p>{t(FILE_KEYS.emptyBody)}</p>
              </div>
            ) : null}
            {searchError === null ? null : (
              <p className="file-explorer__state" role="alert">
                {t(errorKey(searchError))}
              </p>
            )}
            <div
              className="file-explorer__spacer"
              style={{ height: rows.length * TREE_ROW_HEIGHT }}
            >
              {visibleRows.map((row, offset) => {
                const index = window.start + offset;
                const icon = fileIconKind(row.entry);
                const restriction = restrictionFor(row.entry.path);
                const name = basename(row.entry.path);
                return (
                  <div
                    key={row.entry.path}
                    role="treeitem"
                    aria-level={row.depth + 1}
                    aria-selected={selected.has(row.entry.path)}
                    aria-expanded={
                      row.entry.kind === 'directory' ? expanded.has(row.entry.path) : undefined
                    }
                    aria-label={`${name} — ${t(ICON_LABELS[icon])}${
                      restriction === null
                        ? ''
                        : ` — ${t(restriction.badge)}: ${restriction.reason}`
                    }`}
                    className="file-explorer__row"
                    data-path={row.entry.path}
                    data-selected={selected.has(row.entry.path) ? 'true' : undefined}
                    draggable
                    tabIndex={index === 0 ? 0 : -1}
                    style={{ top: index * TREE_ROW_HEIGHT, height: TREE_ROW_HEIGHT }}
                    onClick={(event) => handleRowClick(event, row)}
                    onContextMenu={(event) => handleContextMenu(event, row.entry.path)}
                    onKeyDown={(event) => handleRowKeyDown(event, row)}
                    onDragStart={(event) => handleDragStart(event, row.entry.path)}
                    onDragOver={(event) => {
                      if (row.entry.kind === 'directory') {
                        event.preventDefault();
                        event.dataTransfer.dropEffect = 'move';
                      }
                    }}
                    onDrop={(event) => {
                      if (row.entry.kind === 'directory') {
                        handleDrop(event, row.entry.path);
                      }
                    }}
                  >
                    <span className="file-explorer__indent" style={{ width: row.depth * 16 }} />
                    <span
                      className={`file-explorer__icon file-explorer__icon--${icon}`}
                      aria-hidden="true"
                    >
                      {ICONS[icon]}
                    </span>
                    <span className="file-explorer__name">{name}</span>
                    <span className="file-explorer__meta">
                      {t(FILE_KEYS.sizeBytes, { count: row.entry.size })}
                    </span>
                    <span className="file-explorer__meta">
                      {t(FILE_KEYS.modified, { value: row.entry.lastModified })}
                    </span>
                    {restriction === null ? null : (
                      <span className="file-explorer__restriction">
                        <strong>{t(restriction.badge)}</strong>
                        <span>{restriction.reason}</span>
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {explanations !== undefined && Object.keys(explanations).length >= MAX_EXPLANATIONS ? (
            <p className="file-explorer__hint">
              {t(FILE_KEYS.limitReached, { count: MAX_EXPLANATIONS })}
            </p>
          ) : null}

          {uploads.length === 0 ? null : (
            <ul className="file-explorer__uploads">
              {uploads.map((upload) => (
                <li key={upload.id} data-phase={upload.phase}>
                  <span>{upload.name}</span>
                  {upload.phase === 'reading' ? (
                    <progress value={upload.loaded} max={upload.total || 1} />
                  ) : null}
                  <span>
                    {t(
                      upload.messageKey ??
                        (upload.phase === 'uploading'
                          ? FILE_KEYS.uploadSending
                          : upload.phase === 'done'
                            ? FILE_KEYS.uploadDone
                            : FILE_KEYS.uploadCanceled),
                      {
                        name: upload.name,
                        percent:
                          upload.total > 0 ? Math.round((upload.loaded / upload.total) * 100) : 0,
                      },
                    )}
                  </span>
                  {(upload.phase === 'reading' || upload.phase === 'uploading') &&
                  uploadControllers.current.has(upload.id) ? (
                    <button
                      type="button"
                      onClick={() => uploadControllers.current.get(upload.id)?.abort()}
                    >
                      {t(FILE_KEYS.cancelUpload)}
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}

          {menu === null ? null : (
            <div
              className="file-explorer__menu"
              role="menu"
              aria-label={t(FILE_KEYS.menuLabel)}
              style={{ left: Math.max(menu.x, 0), top: Math.max(menu.y, 0) }}
              onMouseDown={(event) => event.stopPropagation()}
            >
              <button
                type="button"
                role="menuitem"
                disabled={!canWrite}
                onClick={() => {
                  const entry = entryIndex.get(menu.path);
                  const directory =
                    entry?.kind === 'directory' ? entry.path : parentPath(menu.path);
                  setMenu(null);
                  openCreate('create-file', directory);
                }}
              >
                {t(FILE_KEYS.menuNewFile)}
              </button>
              <button
                type="button"
                role="menuitem"
                disabled={!canWrite}
                onClick={() => {
                  const entry = entryIndex.get(menu.path);
                  const directory =
                    entry?.kind === 'directory' ? entry.path : parentPath(menu.path);
                  setMenu(null);
                  openCreate('create-folder', directory);
                }}
              >
                {t(FILE_KEYS.menuNewFolder)}
              </button>
              <button
                type="button"
                role="menuitem"
                disabled={!canWrite}
                onClick={() => {
                  const entry = entryIndex.get(menu.path);
                  setMenu(null);
                  if (entry !== undefined) {
                    openRename(entry);
                  }
                }}
              >
                {t(FILE_KEYS.menuRename)}
              </button>
              <button
                type="button"
                role="menuitem"
                disabled={!canDelete}
                onClick={() => {
                  const paths = selected.has(menu.path) ? [...selected] : [menu.path];
                  setMenu(null);
                  openDelete(paths);
                }}
              >
                {t(FILE_KEYS.menuDelete)}
              </button>
            </div>
          )}

          {nameDialog === null ? null : (
            <div className="file-explorer__dialog-backdrop">
              <form
                className="file-explorer__dialog"
                role="dialog"
                aria-modal="true"
                onSubmit={(event) => void submitNameDialog(event)}
              >
                <h4>
                  {t(
                    nameDialog.mode === 'rename'
                      ? FILE_KEYS.renameTitle
                      : nameDialog.mode === 'create-folder'
                        ? FILE_KEYS.createFolderTitle
                        : FILE_KEYS.createFileTitle,
                  )}
                </h4>
                <label>
                  <span>{t(FILE_KEYS.nameLabel)}</span>
                  <input
                    autoFocus
                    value={nameDialog.value}
                    onChange={(event) =>
                      setNameDialog({ ...nameDialog, value: event.target.value })
                    }
                  />
                </label>
                <div className="file-explorer__dialog-actions">
                  <button type="submit">
                    {t(nameDialog.mode === 'rename' ? FILE_KEYS.renameAction : FILE_KEYS.create)}
                  </button>
                  <button type="button" onClick={() => setNameDialog(null)}>
                    {t(FILE_KEYS.cancel)}
                  </button>
                </div>
              </form>
            </div>
          )}

          {confirmDialog === null ? null : (
            <div className="file-explorer__dialog-backdrop">
              <div className="file-explorer__dialog" role="dialog" aria-modal="true">
                <h4>
                  {t(confirmDialog.mode === 'delete' ? FILE_KEYS.deleteTitle : FILE_KEYS.moveTitle)}
                </h4>
                <p>
                  {t(confirmDialog.mode === 'delete' ? FILE_KEYS.deleteBody : FILE_KEYS.moveBody, {
                    count: confirmDialog.paths.length,
                    target: confirmDialog.target ?? '',
                  })}
                </p>
                <div className="file-explorer__dialog-actions">
                  <button type="button" onClick={() => void submitConfirmDialog()}>
                    {t(
                      confirmDialog.mode === 'delete'
                        ? FILE_KEYS.deleteConfirm
                        : FILE_KEYS.moveConfirm,
                    )}
                  </button>
                  <button type="button" onClick={() => setConfirmDialog(null)}>
                    {t(FILE_KEYS.cancel)}
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </section>
  );
}
