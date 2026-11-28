/**
 * In-memory authentication state for the Web UI.
 *
 * A bearer token exists only in React state for the lifetime of the mounted application. It is
 * never written to localStorage, sessionStorage, cookies, the URL, or a log. Identity data is
 * parsed strictly, and a 401 from any authenticated request clears the token and the query cache
 * for that identity scope.
 */
import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import { ApiRequestError, apiClient } from '../api/client';
import { parseIdentity, type IdentitySnapshot } from '../api/identity';
import { queryCache } from '../api/queryCache';
import { AUTH_KEYS } from '../i18n/keys';
import { useToast } from '../toast/context';
import {
  ANONYMOUS_SCOPE,
  AuthContext,
  isValidTokenShape,
  type AuthApi,
  type AuthStatus,
} from './store';

interface AuthProviderProps {
  readonly children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const toast = useToast();
  const [token, setToken] = useState<string | null>(null);
  const [identity, setIdentity] = useState<IdentitySnapshot | null>(null);
  const [status, setStatus] = useState<AuthStatus>('anonymous');
  const scopeRef = useRef<string>(ANONYMOUS_SCOPE);

  const forget = useCallback(
    (nextStatus: 'anonymous' | 'unauthorized', notify: boolean) => {
      queryCache.invalidate(`${scopeRef.current}|`);
      scopeRef.current = ANONYMOUS_SCOPE;
      setToken(null);
      setIdentity(null);
      setStatus(nextStatus);
      if (notify) {
        toast.show({
          messageKey:
            nextStatus === 'unauthorized' ? AUTH_KEYS.toastUnauthorized : AUTH_KEYS.toastCleared,
          tone: nextStatus === 'unauthorized' ? 'error' : 'info',
        });
      }
    },
    [toast],
  );

  const clear = useCallback(
    (reason: 'manual' | 'unauthorized' = 'manual') => {
      forget(reason === 'unauthorized' ? 'unauthorized' : 'anonymous', true);
    },
    [forget],
  );

  const reportUnauthorized = useCallback(() => {
    if (token !== null || identity !== null || status === 'authenticated') {
      forget('unauthorized', true);
    }
  }, [forget, identity, status, token]);

  const authorize = useCallback(
    async (candidate: string): Promise<boolean> => {
      const trimmed = candidate.trim();
      if (!isValidTokenShape(trimmed)) {
        setStatus('error');
        return false;
      }

      setStatus('authenticating');
      try {
        const response = await apiClient.request<IdentitySnapshot>('getCurrentIdentity', {
          token: trimmed,
          validator: (value): value is IdentitySnapshot => parseIdentity(value) !== null,
        });
        if (response.data === null) {
          throw new ApiRequestError('invalid-response', {
            status: response.status,
            requestId: response.requestId,
          });
        }

        const previousScope = scopeRef.current;
        const nextScope = response.data.identity.id;
        if (previousScope !== nextScope) {
          queryCache.invalidate(`${previousScope}|`);
        }
        scopeRef.current = nextScope;
        setToken(trimmed);
        setIdentity(response.data);
        setStatus('authenticated');
        return true;
      } catch (error) {
        if (error instanceof ApiRequestError && error.reason === 'unauthorized') {
          forget('unauthorized', true);
        } else {
          forget('anonymous', false);
          setStatus('error');
        }
        return false;
      }
    },
    [forget],
  );

  const api = useMemo<AuthApi>(
    () => ({
      status,
      token,
      identity,
      hasToken: token !== null,
      scope: identity === null ? ANONYMOUS_SCOPE : identity.identity.id,
      authorize,
      clear,
      reportUnauthorized,
    }),
    [authorize, clear, identity, reportUnauthorized, status, token],
  );

  return <AuthContext.Provider value={api}>{children}</AuthContext.Provider>;
}
