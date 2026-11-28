import { createContext, useContext } from 'react';
import type { IdentitySnapshot } from '../api/identity';

export type AuthStatus =
  | 'anonymous'
  | 'authenticating'
  | 'authenticated'
  | 'unauthorized'
  | 'error';

export interface AuthApi {
  readonly status: AuthStatus;
  readonly token: string | null;
  readonly identity: IdentitySnapshot | null;
  readonly hasToken: boolean;
  /** Opaque cache scope derived from the verified identity, never from the token itself. */
  readonly scope: string;
  authorize(token: string): Promise<boolean>;
  clear(reason?: 'manual' | 'unauthorized'): void;
  reportUnauthorized(): void;
}

export const AuthContext = createContext<AuthApi | null>(null);
export const ANONYMOUS_SCOPE = 'anonymous';
const MIN_TOKEN_LENGTH = 8;
const MAX_TOKEN_LENGTH = 512;

function hasControlCharacter(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 0x1f || code === 0x7f) {
      return true;
    }
  }
  return false;
}

export function isValidTokenShape(value: string): boolean {
  return (
    value.length >= MIN_TOKEN_LENGTH &&
    value.length <= MAX_TOKEN_LENGTH &&
    !hasControlCharacter(value)
  );
}

export function useAuth(): AuthApi {
  const api = useContext(AuthContext);
  if (api === null) {
    throw new Error('AuthProvider is missing');
  }
  return api;
}
