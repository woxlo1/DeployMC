import type { IdentityKind } from '../api/identity';
import { AUTH_KEYS } from '../i18n/keys';
import type { AuthStatus } from './store';

export function authStatusLabelKey(status: AuthStatus): string {
  switch (status) {
    case 'authenticating':
      return AUTH_KEYS.statusAuthenticating;
    case 'authenticated':
      return AUTH_KEYS.statusAuthenticated;
    case 'unauthorized':
      return AUTH_KEYS.statusUnauthorized;
    case 'error':
      return AUTH_KEYS.statusError;
    default:
      return AUTH_KEYS.statusAnonymous;
  }
}

export function identityKindLabelKey(kind: IdentityKind): string {
  switch (kind) {
    case 'cloudflare':
      return AUTH_KEYS.kindCloudflare;
    case 'local-onetime':
      return AUTH_KEYS.kindOnetime;
    default:
      return AUTH_KEYS.kindToken;
  }
}
