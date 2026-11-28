/**
 * Strict parser for the authenticated `/api/v1/me` payload.
 *
 * The UI displays only the opaque id, kind, roles, and effective permission count. Raw token
 * scopes, digests, paths, and role rules are never accepted into application state.
 */
import { API_PERMISSION_IDENTIFIERS } from './generated';

export type IdentityKind = 'cloudflare' | 'token' | 'local-onetime';

export interface IdentitySnapshot {
  readonly schemaVersion: '1.0';
  readonly identity: {
    readonly id: string;
    readonly kind: IdentityKind;
  };
  readonly roles: readonly string[];
  readonly permissions: readonly string[];
}

const ALLOWED_KINDS: ReadonlySet<string> = new Set(['cloudflare', 'token', 'local-onetime']);
const PERMISSIONS: ReadonlySet<string> = new Set(API_PERMISSION_IDENTIFIERS);
const RESPONSE_FIELDS: ReadonlySet<string> = new Set([
  'schemaVersion',
  'identity',
  'roles',
  'permissions',
  'explain',
]);
const IDENTITY_FIELDS: ReadonlySet<string> = new Set(['id', 'kind']);
const MAX_ID_LENGTH = 320;
const MAX_ROLE_LENGTH = 64;
const MAX_ROLES = 64;
const MAX_PERMISSIONS = 23;

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

function boundedText(value: unknown, maxLength: number): value is string {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    value.length <= maxLength &&
    !hasControlCharacter(value)
  );
}

export function parseIdentity(value: unknown): IdentitySnapshot | null {
  if (!isRecord(value) || !hasOnlyFields(value, RESPONSE_FIELDS)) {
    return null;
  }
  if (value.schemaVersion !== '1.0') {
    return null;
  }
  if (!isRecord(value.identity) || !hasOnlyFields(value.identity, IDENTITY_FIELDS)) {
    return null;
  }
  if (
    !boundedText(value.identity.id, MAX_ID_LENGTH) ||
    typeof value.identity.kind !== 'string' ||
    !ALLOWED_KINDS.has(value.identity.kind)
  ) {
    return null;
  }
  if (!Array.isArray(value.roles) || value.roles.length > MAX_ROLES) {
    return null;
  }
  if (!Array.isArray(value.permissions) || value.permissions.length > MAX_PERMISSIONS) {
    return null;
  }

  const roles: string[] = [];
  const seenRoles = new Set<string>();
  for (const role of value.roles) {
    if (!boundedText(role, MAX_ROLE_LENGTH) || seenRoles.has(role)) {
      return null;
    }
    seenRoles.add(role);
    roles.push(role);
  }

  const permissions: string[] = [];
  const seenPermissions = new Set<string>();
  for (const permission of value.permissions) {
    if (
      typeof permission !== 'string' ||
      !PERMISSIONS.has(permission) ||
      seenPermissions.has(permission)
    ) {
      return null;
    }
    seenPermissions.add(permission);
    permissions.push(permission);
  }

  // Explain mode carries booleans and counts only. It is accepted but never rendered as prose.
  if (value.explain !== undefined && !isRecord(value.explain)) {
    return null;
  }

  return {
    schemaVersion: '1.0',
    identity: {
      id: value.identity.id,
      kind: value.identity.kind as IdentityKind,
    },
    roles,
    permissions,
  };
}
