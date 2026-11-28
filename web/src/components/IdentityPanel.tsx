import { useEffect, useState, type FormEvent } from 'react';
import { useAuth } from '../auth/store';
import { authStatusLabelKey, identityKindLabelKey } from '../auth/status';
import type { Translator } from '../i18n';
import { AUTH_KEYS } from '../i18n/keys';

/** Identity screen. The token field is cleared immediately after submission. */
export function IdentityPanel({ t }: { readonly t: Translator }) {
  const auth = useAuth();
  const [candidate, setCandidate] = useState('');
  const [invalid, setInvalid] = useState(false);

  useEffect(
    () => () => {
      setCandidate('');
    },
    [],
  );

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const submitted = candidate;
    setCandidate('');
    setInvalid(false);
    const accepted = await auth.authorize(submitted);
    setInvalid(!accepted);
  }

  const statusValues = auth.identity === null ? undefined : { id: auth.identity.identity.id };

  return (
    <section className="app__panel identity" aria-labelledby="identity-heading">
      <h2 id="identity-heading">{t(AUTH_KEYS.headerLabel)}</h2>
      <p className="identity__status" data-auth-status={auth.status} role="status">
        {t(authStatusLabelKey(auth.status), statusValues)}
      </p>
      <form className="identity__form" onSubmit={handleSubmit}>
        <label htmlFor="identity-token">{t(AUTH_KEYS.tokenLabel)}</label>
        <input
          id="identity-token"
          type="password"
          value={candidate}
          onChange={(event) => setCandidate(event.target.value)}
          autoComplete="off"
          spellCheck={false}
          disabled={auth.status === 'authenticating'}
        />
        <p className="identity__help">{t(AUTH_KEYS.tokenHelp)}</p>
        <button type="submit" disabled={auth.status === 'authenticating'}>
          {t(AUTH_KEYS.signIn)}
        </button>
        {auth.hasToken ? (
          <button type="button" onClick={() => auth.clear('manual')}>
            {t(AUTH_KEYS.signOut)}
          </button>
        ) : null}
      </form>
      {invalid ? <p role="alert">{t(AUTH_KEYS.requestFailed)}</p> : null}
      {auth.identity === null ? (
        <p>{t(AUTH_KEYS.identityNone)}</p>
      ) : (
        <dl className="identity__details">
          <div>
            <dt>{t(AUTH_KEYS.identityId)}</dt>
            <dd>{auth.identity.identity.id}</dd>
          </div>
          <div>
            <dt>{t(AUTH_KEYS.identityKind)}</dt>
            <dd>{t(identityKindLabelKey(auth.identity.identity.kind))}</dd>
          </div>
          <div>
            <dt>{t(AUTH_KEYS.identityRoles)}</dt>
            <dd>{t(AUTH_KEYS.roleCount, { count: auth.identity.roles.length })}</dd>
          </div>
          <div>
            <dt>{t(AUTH_KEYS.identityPermissions)}</dt>
            <dd>{t(AUTH_KEYS.permissionCount, { count: auth.identity.permissions.length })}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}
