/**
 * Strict, bounded access to the generated file REST operations.
 *
 * The UI never turns free-form request text into a path. Names are validated locally, all paths
 * remain sandbox-relative, and every response is parsed before it reaches React state. The bearer
 * token is a one-call argument supplied by the caller; it is never retained here.
 */
import { ApiRequestError, apiClient, type ApiParameterValue } from './client';
import { queryCache } from './queryCache';

export const ROOT_PATH = '.' as const;
export const DIRECTORY_DEPTH_LIMIT = 4;
export const LIST_PAGE_LIMIT = 50;

const MAX_PATH_LENGTH = 4096;
const MAX_NAME_LENGTH = 128;
const MAX_CURSOR_LENGTH = 512;
const MAX_REVISION_LENGTH = 256;
const MAX_LIST_ITEMS = 500;
const MAX_VALIDATION_ISSUES = 64;
const MAX_EXPLANATION_TEXT = 64;
const FORBIDDEN_NAME_CHARS = /[<>:"|?*]/;
const RESERVED_NAME = /^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?$/i;
const SHORT_NAME = /~[0-9]/;
const UTC_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/;
const EXPLANATION_REASONS = [
  'NO_ROLE',
  'NOT_GRANTED',
  'TOKEN_SCOPE_EXCEEDED',
  'PATH_DENIED',
  'PATH_SCOPE',
] as const;

export type EntryKind = 'file' | 'directory';
export type ExplanationReason = (typeof EXPLANATION_REASONS)[number];
export type FilePermission = 'files.read' | 'files.write' | 'files.delete' | 'files.upload';

export interface FileEntry {
  readonly path: string;
  readonly kind: EntryKind;
  readonly size: number;
  readonly revision: string | null;
  readonly lastModified: string;
}

export interface FileList {
  readonly path: string;
  readonly items: readonly FileEntry[];
  readonly nextCursor: string | null;
  readonly total: number;
}

export interface Explanation {
  readonly permission: FilePermission;
  readonly decision: 'allow' | 'deny';
  readonly reason: ExplanationReason | null;
  readonly pathRequested: boolean;
  readonly pathResolved: boolean;
  readonly pathWithinScope: boolean;
}

export interface UploadProgress {
  readonly phase: 'reading' | 'uploading' | 'complete';
  readonly loaded: number;
  readonly total: number;
}

export interface UploadOptions {
  readonly signal?: AbortSignal;
  readonly onProgress?: (progress: UploadProgress) => void;
}

interface ValidationFinding {
  readonly code: string;
  readonly severity: 'ERROR' | 'WARNING';
}

interface ValidationSummary {
  readonly matched: boolean;
  readonly valid: boolean;
  readonly format: 'yaml' | 'json' | 'toml' | 'properties' | null;
  readonly issues: readonly ValidationFinding[];
}

interface WriteResult {
  readonly entry: FileEntry;
  readonly validation: ValidationSummary | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasOnlyFields(value: Record<string, unknown>, allowed: ReadonlySet<string>): boolean {
  return Object.keys(value).every((key) => allowed.has(key));
}

function hasControlCharacter(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 0x1f || code === 0x7f) {
      return true;
    }
  }
  return false;
}

/** Validates a single user-supplied file or directory name. */
export function validateName(raw: string): string | null {
  const trimmed = raw.trim();
  if (
    trimmed.length === 0 ||
    trimmed.length > MAX_NAME_LENGTH ||
    trimmed === '.' ||
    trimmed === '..' ||
    raw !== raw.trim() ||
    hasControlCharacter(trimmed) ||
    FORBIDDEN_NAME_CHARS.test(trimmed) ||
    SHORT_NAME.test(trimmed) ||
    RESERVED_NAME.test(trimmed)
  ) {
    return null;
  }
  const normalized = trimmed.normalize('NFC');
  return normalized.split('/').length === 1 && normalized.split('\\').length === 1
    ? normalized
    : null;
}

/** Checks a sandbox-relative path without ever resolving it against a host file system. */
export function isValidRelativePath(path: string): boolean {
  if (path.length === 0 || path.length > MAX_PATH_LENGTH || hasControlCharacter(path)) {
    return false;
  }
  if (path === ROOT_PATH) {
    return true;
  }
  if (path.startsWith('/') || path.includes('\\') || /^[A-Za-z]:/.test(path)) {
    return false;
  }
  if (path.includes('%') || path.includes('\uff0e') || path.includes('\u3002')) {
    return false;
  }
  const segments = path.split('/');
  if (segments.some((segment) => segment.length === 0 || segment === '.' || segment === '..')) {
    return false;
  }
  return segments.every((segment) => validateName(segment) !== null);
}

/** Builds a child path from a validated parent and a user-supplied name. */
export function joinPath(parent: string, name: string): string | null {
  if (!isValidRelativePath(parent) || parent.includes('\\')) {
    return null;
  }
  const safeName = validateName(name);
  if (safeName === null) {
    return null;
  }
  const joined = parent === ROOT_PATH ? safeName : `${parent}/${safeName}`;
  return isValidRelativePath(joined) ? joined : null;
}

export function basename(path: string): string {
  if (path === ROOT_PATH) {
    return ROOT_PATH;
  }
  const index = path.lastIndexOf('/');
  return index === -1 ? path : path.slice(index + 1);
}

export function parentPath(path: string): string {
  const index = path.lastIndexOf('/');
  return index <= 0 ? ROOT_PATH : path.slice(0, index);
}

export function breadcrumbSegments(path: string): readonly string[] {
  if (!isValidRelativePath(path) || path === ROOT_PATH) {
    return [];
  }
  const result: string[] = [];
  let current: string = ROOT_PATH;
  for (const segment of path.split('/')) {
    const joined = joinPath(current, segment);
    if (joined === null) {
      return [];
    }
    current = joined;
    result.push(joined);
  }
  return result;
}

export function isWithin(path: string, directory: string): boolean {
  if (!isValidRelativePath(path)) {
    return false;
  }
  if (directory === ROOT_PATH) {
    return true;
  }
  return path === directory || path.startsWith(`${directory}/`);
}

function isUtcInstant(value: string): boolean {
  return UTC_INSTANT.test(value) && Number.isFinite(Date.parse(value));
}

function parseEntry(value: unknown): FileEntry | null {
  const fields = new Set(['path', 'kind', 'size', 'revision', 'lastModified']);
  if (!isRecord(value) || !hasOnlyFields(value, fields)) {
    return null;
  }
  if (
    typeof value.path !== 'string' ||
    !isValidRelativePath(value.path) ||
    (value.kind !== 'file' && value.kind !== 'directory') ||
    typeof value.size !== 'number' ||
    !Number.isSafeInteger(value.size) ||
    value.size < 0 ||
    (value.revision !== null &&
      (typeof value.revision !== 'string' || value.revision.length > MAX_REVISION_LENGTH)) ||
    typeof value.lastModified !== 'string' ||
    !isUtcInstant(value.lastModified)
  ) {
    return null;
  }
  return {
    path: value.path,
    kind: value.kind,
    size: value.size,
    revision: value.revision as string | null,
    lastModified: value.lastModified,
  };
}

function parseFileList(value: unknown, requestedPath: string, depth: number): FileList | null {
  const fields = new Set(['path', 'items', 'nextCursor', 'total']);
  if (!isRecord(value) || !hasOnlyFields(value, fields)) {
    return null;
  }
  if (
    value.path !== requestedPath ||
    !Array.isArray(value.items) ||
    value.items.length > MAX_LIST_ITEMS
  ) {
    return null;
  }
  if (
    (value.nextCursor !== null &&
      (typeof value.nextCursor !== 'string' || value.nextCursor.length > MAX_CURSOR_LENGTH)) ||
    typeof value.total !== 'number' ||
    !Number.isSafeInteger(value.total) ||
    value.total < 0
  ) {
    return null;
  }
  const items: FileEntry[] = [];
  for (const item of value.items) {
    const entry = parseEntry(item);
    if (entry === null) {
      return null;
    }
    if (depth === 1 && !isWithin(entry.path, requestedPath)) {
      return null;
    }
    items.push(entry);
  }
  return {
    path: requestedPath,
    items,
    nextCursor: value.nextCursor as string | null,
    total: value.total,
  };
}

function parseValidation(value: unknown): ValidationSummary | null {
  if (value === null) {
    return null;
  }
  const fields = new Set(['path', 'matched', 'format', 'valid', 'issues']);
  if (!isRecord(value) || !hasOnlyFields(value, fields)) {
    return null;
  }
  if (
    typeof value.path !== 'string' ||
    !isValidRelativePath(value.path) ||
    typeof value.matched !== 'boolean' ||
    typeof value.valid !== 'boolean' ||
    (value.format !== null &&
      value.format !== 'yaml' &&
      value.format !== 'json' &&
      value.format !== 'toml' &&
      value.format !== 'properties') ||
    !Array.isArray(value.issues) ||
    value.issues.length > MAX_VALIDATION_ISSUES
  ) {
    return null;
  }
  const issues: ValidationFinding[] = [];
  for (const item of value.issues) {
    if (
      !isRecord(item) ||
      typeof item.code !== 'string' ||
      item.code.length > MAX_EXPLANATION_TEXT ||
      (item.severity !== 'ERROR' && item.severity !== 'WARNING')
    ) {
      return null;
    }
    issues.push({ code: item.code, severity: item.severity });
  }
  return {
    matched: value.matched,
    valid: value.valid,
    format: value.format as ValidationSummary['format'],
    issues,
  };
}

function parseWriteResult(value: unknown): WriteResult | null {
  const fields = new Set(['entry', 'validation']);
  if (!isRecord(value) || !hasOnlyFields(value, fields)) {
    return null;
  }
  const entry = parseEntry(value.entry);
  if (entry === null) {
    return null;
  }
  if (value.validation === undefined || value.validation === null) {
    return { entry, validation: null };
  }
  const validation = parseValidation(value.validation);
  return validation === null ? null : { entry, validation };
}

interface ExplanationPayload {
  readonly explain: Explanation;
}

function parseExplanation(
  value: unknown,
  requestedPermission: FilePermission,
): ExplanationPayload | null {
  const responseFields = new Set(['schemaVersion', 'identity', 'roles', 'permissions', 'explain']);
  const explainFields = new Set(['permission', 'decision', 'reason', 'path', 'token', 'roles']);
  const pathFields = new Set(['requested', 'resolved', 'withinScope']);
  if (!isRecord(value) || !hasOnlyFields(value, responseFields)) {
    return null;
  }
  if (!isRecord(value.explain) || !hasOnlyFields(value.explain, explainFields)) {
    return null;
  }
  const explain = value.explain;
  if (
    explain.permission !== requestedPermission ||
    (explain.decision !== 'allow' && explain.decision !== 'deny') ||
    !isRecord(explain.path) ||
    !hasOnlyFields(explain.path, pathFields) ||
    typeof explain.path.requested !== 'boolean' ||
    typeof explain.path.resolved !== 'boolean' ||
    typeof explain.path.withinScope !== 'boolean' ||
    (explain.reason !== null &&
      (typeof explain.reason !== 'string' ||
        !EXPLANATION_REASONS.includes(explain.reason as ExplanationReason)))
  ) {
    return null;
  }
  return {
    explain: {
      permission: requestedPermission,
      decision: explain.decision,
      reason: explain.reason as ExplanationReason | null,
      pathRequested: explain.path.requested,
      pathResolved: explain.path.resolved,
      pathWithinScope: explain.path.withinScope,
    },
  };
}

export function invalidateFileCache(scope: string): number {
  return (
    queryCache.invalidate(`${scope}|listFiles|`) +
    queryCache.invalidate(`${scope}|getCurrentIdentity|`)
  );
}

export async function listFilesPage(
  token: string,
  path: string,
  options: {
    readonly cursor?: string;
    readonly limit?: number;
    readonly depth?: number;
    readonly signal?: AbortSignal;
  } = {},
): Promise<FileList> {
  if (!isValidRelativePath(path)) {
    throw new ApiRequestError('invalid-response');
  }
  const depth = options.depth ?? 1;
  const params: Readonly<Record<string, ApiParameterValue>> = {
    path,
    depth,
    limit: options.limit ?? LIST_PAGE_LIMIT,
    ...(options.cursor === undefined ? {} : { cursor: options.cursor }),
  };
  const response = await apiClient.request<FileList>('listFiles', {
    token,
    params,
    signal: options.signal,
    validator: (value): value is FileList => parseFileList(value, path, depth) !== null,
  });
  if (response.data === null) {
    throw new ApiRequestError('invalid-response', {
      status: response.status,
      requestId: response.requestId,
    });
  }
  return response.data;
}

export async function explainPermission(
  token: string,
  permission: FilePermission,
  path: string,
  signal?: AbortSignal,
): Promise<Explanation> {
  if (!isValidRelativePath(path)) {
    throw new ApiRequestError('invalid-response');
  }
  const response = await apiClient.request<ExplanationPayload>('getCurrentIdentity', {
    token,
    params: { explain: permission, path },
    signal,
    validator: (value): value is ExplanationPayload => parseExplanation(value, permission) !== null,
  });
  const parsed = response.data === null ? null : parseExplanation(response.data, permission);
  if (parsed === null) {
    throw new ApiRequestError('invalid-response', {
      status: response.status,
      requestId: response.requestId,
    });
  }
  return parsed.explain;
}

export async function createEntry(
  token: string,
  path: string,
  kind: EntryKind,
): Promise<FileEntry> {
  if (!isValidRelativePath(path)) {
    throw new ApiRequestError('invalid-response');
  }
  const body = JSON.stringify(kind === 'directory' ? { path, kind } : { path, kind, content: '' });
  const response = await apiClient.request<WriteResult>('createFileEntry', {
    token,
    body,
    contentType: 'application/json',
    validator: (value): value is WriteResult => parseWriteResult(value) !== null,
  });
  if (response.data === null) {
    throw new ApiRequestError('invalid-response', {
      status: response.status,
      requestId: response.requestId,
    });
  }
  return response.data.entry;
}

export async function moveEntry(
  token: string,
  from: string,
  to: string,
  options: { readonly ifMatch?: string } = {},
): Promise<FileEntry> {
  if (!isValidRelativePath(from) || !isValidRelativePath(to)) {
    throw new ApiRequestError('invalid-response');
  }
  const response = await apiClient.request<FileEntry>('moveFileEntry', {
    token,
    body: JSON.stringify({ from, to, overwrite: false }),
    contentType: 'application/json',
    ...(options.ifMatch === undefined ? {} : { ifMatch: options.ifMatch }),
    validator: (value): value is FileEntry => parseEntry(value) !== null,
  });
  if (response.data === null) {
    throw new ApiRequestError('invalid-response', {
      status: response.status,
      requestId: response.requestId,
    });
  }
  return response.data;
}

export async function deleteEntry(
  token: string,
  path: string,
  options: { readonly recursive?: boolean; readonly expectedEntryCount?: number } = {},
): Promise<void> {
  if (!isValidRelativePath(path)) {
    throw new ApiRequestError('invalid-response');
  }
  const params: Readonly<Record<string, ApiParameterValue>> = {
    path,
    recursive: options.recursive === true,
    ...(options.expectedEntryCount === undefined
      ? {}
      : { expectedEntryCount: options.expectedEntryCount }),
  };
  await apiClient.request('deleteFileEntry', { token, params });
}

function readFileWithProgress(
  file: File,
  signal: AbortSignal,
  onProgress: (progress: UploadProgress) => void,
): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new ApiRequestError('aborted'));
      return;
    }
    const reader = new FileReader();
    const onAbort = () => reader.abort();
    const cleanup = () => signal.removeEventListener('abort', onAbort);
    signal.addEventListener('abort', onAbort, { once: true });

    reader.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress({ phase: 'reading', loaded: event.loaded, total: event.total });
      }
    };
    reader.onload = () => {
      cleanup();
      if (signal.aborted) {
        reject(new ApiRequestError('aborted'));
      } else if (reader.result instanceof ArrayBuffer) {
        resolve(reader.result);
      } else {
        reject(new ApiRequestError('invalid-response'));
      }
    };
    reader.onerror = () => {
      cleanup();
      reject(new ApiRequestError('network-error'));
    };
    reader.onabort = () => {
      cleanup();
      reject(new ApiRequestError('aborted'));
    };
    reader.readAsArrayBuffer(file);
  });
}

/**
 * Uploads a file with a local read percentage followed by an indeterminate network phase. Native
 * `fetch` does not expose upload progress, so no network percentage is invented.
 */
export async function uploadEntry(
  token: string,
  path: string,
  file: File,
  options: UploadOptions = {},
): Promise<FileEntry> {
  if (!isValidRelativePath(path)) {
    throw new ApiRequestError('invalid-response');
  }
  const signal = options.signal;
  const report = options.onProgress ?? (() => undefined);
  const bytes = await readFileWithProgress(file, signal ?? new AbortController().signal, report);
  if (signal?.aborted === true) {
    throw new ApiRequestError('aborted');
  }
  report({ phase: 'uploading', loaded: 0, total: file.size });
  const response = await apiClient.request<WriteResult>('uploadFileContent', {
    token,
    params: { path },
    body: bytes,
    contentType: 'application/octet-stream',
    signal,
    validator: (value): value is WriteResult => parseWriteResult(value) !== null,
  });
  if (response.data === null) {
    throw new ApiRequestError('invalid-response', {
      status: response.status,
      requestId: response.requestId,
    });
  }
  report({ phase: 'complete', loaded: file.size, total: file.size });
  return response.data.entry;
}

export function isFileRequestError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError;
}
