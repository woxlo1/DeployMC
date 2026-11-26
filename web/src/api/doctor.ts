/**
 * Typed client for the redacted doctor report endpoint.
 *
 * The bearer token is a call argument only. It is never read from or written to browser storage,
 * cookies, the URL, or logs, and it is never included in an error message.
 */
export const DOCTOR_ENDPOINT = '/api/v1/server/doctor' as const;

export type DoctorStatus = 'PASS' | 'WARN' | 'FAIL';

export type DoctorEvidenceValue = string | number | boolean;

export interface DoctorCheck {
  id: string;
  status: DoctorStatus;
  implemented: boolean;
  detail: string;
  remediation?: string;
  evidence?: Readonly<Record<string, DoctorEvidenceValue>>;
}

export interface DoctorReport {
  schemaVersion: '1.0';
  generatedAt: string;
  overall: DoctorStatus;
  scope: 'limited';
  checks: readonly DoctorCheck[];
  redacted?: boolean;
}

export type DoctorErrorCode = 'unauthorized' | 'request-failed' | 'invalid-response';

export class DoctorRequestError extends Error {
  readonly code: DoctorErrorCode;

  constructor(code: DoctorErrorCode) {
    super(code);
    this.name = 'DoctorRequestError';
    this.code = code;
  }
}

const STATUSES: readonly string[] = ['PASS', 'WARN', 'FAIL'];
const CHECK_ID = /^[a-z][a-z0-9]*(?:\.[a-z][a-z0-9-]+)*$/;
const EVIDENCE_KEY = /^[A-Za-z][A-Za-z0-9-]*$/;
const UTC_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/;
const MAX_TEXT = 512;
const MAX_EVIDENCE_TEXT = 256;
const MAX_EVIDENCE_ENTRIES = 16;
const REPORT_FIELDS: ReadonlySet<string> = new Set([
  'schemaVersion',
  'generatedAt',
  'overall',
  'scope',
  'checks',
  'redacted',
]);
const CHECK_FIELDS: ReadonlySet<string> = new Set([
  'id',
  'status',
  'implemented',
  'detail',
  'remediation',
  'evidence',
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasOnlyFields(value: Record<string, unknown>, allowed: ReadonlySet<string>): boolean {
  return Object.keys(value).every((key) => allowed.has(key));
}

function isUtcInstant(value: string): boolean {
  return UTC_INSTANT.test(value) && Number.isFinite(Date.parse(value));
}

function isEvidenceValue(value: unknown): value is DoctorEvidenceValue {
  if (typeof value === 'boolean') {
    return true;
  }
  if (typeof value === 'number') {
    return Number.isInteger(value);
  }
  return typeof value === 'string' && value.length <= MAX_EVIDENCE_TEXT;
}

function parseCheck(value: unknown): DoctorCheck | null {
  if (!isRecord(value) || !hasOnlyFields(value, CHECK_FIELDS)) {
    return null;
  }
  if (
    typeof value.id !== 'string' ||
    !CHECK_ID.test(value.id) ||
    typeof value.status !== 'string' ||
    !STATUSES.includes(value.status) ||
    typeof value.implemented !== 'boolean' ||
    typeof value.detail !== 'string' ||
    value.detail.length > MAX_TEXT
  ) {
    return null;
  }
  if (
    value.remediation !== undefined &&
    (typeof value.remediation !== 'string' || value.remediation.length > MAX_TEXT)
  ) {
    return null;
  }

  let evidence: Record<string, DoctorEvidenceValue> | undefined;
  if (value.evidence !== undefined) {
    if (!isRecord(value.evidence) || Object.keys(value.evidence).length > MAX_EVIDENCE_ENTRIES) {
      return null;
    }
    evidence = {};
    for (const [key, entry] of Object.entries(value.evidence)) {
      if (!EVIDENCE_KEY.test(key) || !isEvidenceValue(entry)) {
        return null;
      }
      evidence[key] = entry;
    }
  }

  return {
    id: value.id,
    status: value.status as DoctorStatus,
    implemented: value.implemented,
    detail: value.detail,
    ...(value.remediation === undefined ? {} : { remediation: value.remediation }),
    ...(evidence === undefined ? {} : { evidence }),
  };
}

function parseReport(value: unknown): DoctorReport | null {
  if (!isRecord(value) || !hasOnlyFields(value, REPORT_FIELDS)) {
    return null;
  }
  if (
    value.schemaVersion !== '1.0' ||
    typeof value.generatedAt !== 'string' ||
    !isUtcInstant(value.generatedAt) ||
    typeof value.overall !== 'string' ||
    !STATUSES.includes(value.overall) ||
    value.scope !== 'limited' ||
    !Array.isArray(value.checks) ||
    value.checks.length < 15 ||
    value.checks.length > 64
  ) {
    return null;
  }
  if (value.redacted !== undefined && typeof value.redacted !== 'boolean') {
    return null;
  }

  const checks: DoctorCheck[] = [];
  const ids: Set<string> = new Set();
  for (const entry of value.checks) {
    const check = parseCheck(entry);
    if (check === null || ids.has(check.id)) {
      return null;
    }
    ids.add(check.id);
    checks.push(check);
  }

  return {
    schemaVersion: value.schemaVersion,
    generatedAt: value.generatedAt,
    overall: value.overall as DoctorStatus,
    scope: value.scope,
    checks,
    ...(value.redacted === undefined ? {} : { redacted: value.redacted }),
  };
}

/**
 * Requests the same-origin doctor endpoint with a token supplied by the caller.
 *
 * A malformed response or a response that echoes the token is rejected so that the panel cannot
 * accidentally render sensitive data. The token is not retained after this function returns.
 */
export async function fetchDoctorReport(
  token: string,
  signal?: AbortSignal,
): Promise<DoctorReport> {
  let response: Response;
  try {
    response = await fetch(DOCTOR_ENDPOINT, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      ...(signal === undefined ? {} : { signal }),
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new DoctorRequestError('request-failed');
  }

  if (response.status === 401) {
    throw new DoctorRequestError('unauthorized');
  }
  if (!response.ok) {
    throw new DoctorRequestError('request-failed');
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    throw new DoctorRequestError('invalid-response');
  }

  const report = parseReport(payload);
  if (report === null || (token.length > 0 && JSON.stringify(report).includes(token))) {
    throw new DoctorRequestError('invalid-response');
  }
  return report;
}
