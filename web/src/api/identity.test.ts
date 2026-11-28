import { describe, expect, it } from 'vitest';
import { parseIdentity } from './identity';
import { API_PERMISSION_IDENTIFIERS } from './generated';

const VALID = {
  schemaVersion: '1.0',
  identity: { id: 'viewer@example.invalid', kind: 'token' },
  roles: ['reader'],
  permissions: ['server.read', 'files.read'],
};

describe('identity response parser', () => {
  it('accepts a bounded payload with registry permissions', () => {
    expect(parseIdentity(VALID)).toEqual(VALID);
  });

  it('uses the generated permission registry', () => {
    expect(API_PERMISSION_IDENTIFIERS).toContain('audit.read');
    expect(API_PERMISSION_IDENTIFIERS).toHaveLength(23);
    expect(parseIdentity({ ...VALID, permissions: ['not.a.permission'] })).toBeNull();
  });

  it('rejects unknown identity kinds, extra fields, and malformed values', () => {
    expect(parseIdentity({ ...VALID, identity: { id: 'x', kind: 'admin' } })).toBeNull();
    expect(parseIdentity({ ...VALID, tokenDigest: 'deadbeef' })).toBeNull();
    expect(parseIdentity({ ...VALID, roles: ['ok'.repeat(100)] })).toBeNull();
    expect(parseIdentity({ ...VALID, permissions: ['server.read', 'server.read'] })).toBeNull();
    expect(parseIdentity({ ...VALID, identity: { id: 'bad\nid', kind: 'token' } })).toBeNull();
    expect(parseIdentity(null)).toBeNull();
  });
});
