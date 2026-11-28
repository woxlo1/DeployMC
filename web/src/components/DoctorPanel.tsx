import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useAuth } from '../auth/store';
import {
  DoctorRequestError,
  fetchDoctorReport,
  type DoctorCheck,
  type DoctorErrorCode,
  type DoctorEvidenceValue,
  type DoctorReport,
  type DoctorStatus,
} from '../api/doctor';
import type { Translator } from '../i18n';

interface DoctorPanelProps {
  locale: 'en' | 'ja';
  t: Translator;
}

const STATUS_LABEL_KEYS: Readonly<Record<DoctorStatus, string>> = {
  PASS: 'doctor.status.PASS',
  WARN: 'doctor.status.WARN',
  FAIL: 'doctor.status.FAIL',
};

const ERROR_LABEL_KEYS: Readonly<Record<DoctorErrorCode, string>> = {
  unauthorized: 'doctor.error.unauthorized',
  'request-failed': 'doctor.error.requestFailed',
  'invalid-response': 'doctor.error.invalidResponse',
};

/**
 * Evidence keys that can be shown without exposing paths, secrets, identities, or addresses.
 * Anything outside this set is discarded before rendering.
 */
const ALLOWED_EVIDENCE_KEYS: ReadonlySet<string> = new Set([
  'apiVersion',
  'available',
  'binding',
  'catalog',
  'checks',
  'configuration',
  'count',
  'default-role',
  'enabled',
  'errors',
  'implemented',
  'level',
  'lock',
  'loopback',
  'mode',
  'platform',
  'pluginVersion',
  'probe',
  'protectedPath',
  'registry-version',
  'roles',
  'scope',
  'status',
  'symlinkPolicy',
  'users',
  'verify',
  'version',
  'warnings',
  'writable',
]);

const SAFE_EVIDENCE_STRING = /^[A-Za-z][A-Za-z0-9_-]{0,63}$/;
const UUID_LIKE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
const LONG_HEX = /^[0-9a-f]{16,}$/i;
const SENSITIVE_PROSE =
  /(?:https?:\/\/|file:\/\/)[^\s<>"']+|(?:[A-Za-z]:\\|\\\\)[^\s<>"']+|\/[A-Za-z0-9._~-]+(?:\/[A-Za-z0-9._~-]+)+|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}|\b(?:\d{1,3}\.){3}\d{1,3}\b|\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
const REDACTED = Symbol('redacted');

function scrubProse(value: string, t: Translator): string {
  return value.replace(SENSITIVE_PROSE, t('doctor.redacted'));
}

function formatEvidenceValue(
  value: DoctorEvidenceValue,
): string | number | boolean | typeof REDACTED {
  if (typeof value === 'string') {
    const isSafe =
      SAFE_EVIDENCE_STRING.test(value) && !UUID_LIKE.test(value) && !LONG_HEX.test(value);
    return isSafe ? value : REDACTED;
  }
  return value;
}

function formatInstant(value: string, locale: 'en' | 'ja'): string {
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) {
    return value;
  }
  return instant.toLocaleString(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'UTC',
  });
}

function CheckItem({ check, t }: { check: DoctorCheck; t: Translator }) {
  const evidenceEntries = Object.entries(check.evidence ?? {}).filter(([key]) =>
    ALLOWED_EVIDENCE_KEYS.has(key),
  );
  const detail = scrubProse(check.detail, t);
  const remediation =
    check.remediation === undefined ? t('doctor.value.none') : scrubProse(check.remediation, t);

  return (
    <li className="doctor__check">
      <p className="doctor__check-heading">
        <span className="doctor__label">{t('doctor.check.status')}</span>{' '}
        <strong>{t(STATUS_LABEL_KEYS[check.status])}</strong>
        <span className="doctor__label">{t('doctor.check.id')}</span> <code>{check.id}</code>
      </p>
      <p>
        <span className="doctor__label">{t('doctor.check.implemented')}</span>{' '}
        <span>{t(check.implemented ? 'doctor.implemented.yes' : 'doctor.implemented.no')}</span>
      </p>
      <p>
        <span className="doctor__label">{t('doctor.check.detail')}</span> <span>{detail}</span>
      </p>
      <p>
        <span className="doctor__label">{t('doctor.check.remediation')}</span>{' '}
        <span>{remediation}</span>
      </p>
      <div className="doctor__field">
        <span className="doctor__label">{t('doctor.check.evidence')}</span>
        {evidenceEntries.length === 0 ? (
          <span>{t('doctor.value.none')}</span>
        ) : (
          <ul className="doctor__evidence">
            {evidenceEntries.map(([key, value]) => {
              const formatted = formatEvidenceValue(value);
              return (
                <li key={key}>
                  <code>{key}</code>:{' '}
                  <span>{formatted === REDACTED ? t('doctor.redacted') : String(formatted)}</span>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </li>
  );
}

function ReportView({
  report,
  locale,
  t,
}: {
  report: DoctorReport;
  locale: 'en' | 'ja';
  t: Translator;
}) {
  return (
    <div className="doctor__report">
      <dl className="doctor__summary">
        <div>
          <dt>{t('doctor.report.overall')}</dt>
          <dd>{t(STATUS_LABEL_KEYS[report.overall])}</dd>
        </div>
        <div>
          <dt>{t('doctor.report.schemaVersion')}</dt>
          <dd>{report.schemaVersion}</dd>
        </div>
        <div>
          <dt>{t('doctor.report.generatedAt')}</dt>
          <dd>
            <time dateTime={report.generatedAt}>{formatInstant(report.generatedAt, locale)}</time>
          </dd>
        </div>
        <div>
          <dt>{t('doctor.report.scope')}</dt>
          <dd>{report.scope}</dd>
        </div>
      </dl>
      <ul className="doctor__checks">
        {report.checks.map((check) => (
          <CheckItem key={check.id} check={check} t={t} />
        ))}
      </ul>
      <p className="doctor__reminder">{t('doctor.reminder')}</p>
    </div>
  );
}

/** Doctor panel. The bearer token exists only while the user types and during one request. */
export function DoctorPanel({ locale, t }: DoctorPanelProps) {
  const auth = useAuth();
  const [token, setToken] = useState('');
  const [report, setReport] = useState<DoctorReport | null>(null);
  const [errorCode, setErrorCode] = useState<DoctorErrorCode | null>(null);
  const [loading, setLoading] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(
    () => () => {
      controllerRef.current?.abort();
      controllerRef.current = null;
      setToken('');
    },
    [],
  );

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const submittedToken = token;
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    setToken('');
    setReport(null);
    setErrorCode(null);
    setLoading(true);

    try {
      const result = await fetchDoctorReport(submittedToken, controller.signal);
      if (!controller.signal.aborted) {
        setReport(result);
      }
    } catch (error) {
      if (controller.signal.aborted) {
        return;
      }
      const code =
        error instanceof DoctorRequestError ? error.code : ('request-failed' as DoctorErrorCode);
      if (code === 'unauthorized') {
        auth.reportUnauthorized();
      }
      setErrorCode(code);
    } finally {
      if (controllerRef.current === controller) {
        controllerRef.current = null;
      }
      setToken('');
      setLoading(false);
    }
  }

  return (
    <section className="app__panel doctor" aria-labelledby="doctor-heading">
      <h2 id="doctor-heading">{t('doctor.heading')}</h2>
      <form className="doctor__form" onSubmit={handleSubmit}>
        <label htmlFor="doctor-token">{t('doctor.token.label')}</label>
        <input
          id="doctor-token"
          type="password"
          value={token}
          onChange={(event) => setToken(event.target.value)}
          autoComplete="off"
          spellCheck={false}
        />
        <button type="submit" disabled={loading}>
          {t('doctor.run')}
        </button>
      </form>
      {loading ? <p role="status">{t('doctor.loading')}</p> : null}
      {errorCode === null ? null : (
        <p role="alert" className="doctor__error">
          {t(ERROR_LABEL_KEYS[errorCode])}
        </p>
      )}
      {report === null ? null : <ReportView report={report} locale={locale} t={t} />}
    </section>
  );
}
