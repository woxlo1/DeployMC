import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DoctorCheck, DoctorReport } from '../api/doctor';
import { App } from '../App';

const TOKEN = 'doctor-fake-token-4d2c';
const ENDPOINT = '/api/v1/server/doctor';

const CHECK_IDS = [
  'environment',
  'capabilities',
  'bind',
  'auth.mode',
  'auth.tunnel',
  'sandbox',
  'snapshot',
  'deployment',
  'platform.jar-replacement',
  'console.capture',
  'configuration',
  'localization',
  'updates',
] as const;

function makeChecks(): DoctorCheck[] {
  return CHECK_IDS.map((id, index) => ({
    id,
    status: index === 0 ? 'PASS' : 'WARN',
    implemented: index === 0,
    detail: `${id} detail`,
    remediation: `${id} remediation`,
  }));
}

function makeRawReport(): Record<string, unknown> {
  return {
    schemaVersion: '1.0',
    generatedAt: '2026-11-23T10:15:00Z',
    overall: 'WARN',
    scope: 'limited',
    checks: makeChecks(),
  };
}

function makeReport(overrides: Partial<DoctorReport> = {}): DoctorReport {
  return {
    ...makeRawReport(),
    ...overrides,
  } as DoctorReport;
}

function withCheck(index: number, patch: Partial<DoctorCheck>): DoctorCheck[] {
  const checks = makeChecks();
  checks[index] = { ...checks[index]!, ...patch };
  return checks;
}

function withDuplicateCheckId(): DoctorCheck[] {
  const checks = makeChecks();
  checks[1] = { ...checks[1]!, id: checks[0]!.id };
  return checks;
}

interface InvalidReportCase {
  name: string;
  payload: unknown;
  sensitiveText?: string;
}

const INVALID_REPORT_CASES: readonly InvalidReportCase[] = [
  {
    name: 'rejects an invalid check id',
    payload: { ...makeRawReport(), checks: withCheck(0, { id: 'Invalid_ID' }) },
    sensitiveText: 'Invalid_ID',
  },
  {
    name: 'rejects a path-like check id',
    payload: { ...makeRawReport(), checks: withCheck(0, { id: '../../etc/passwd' }) },
    sensitiveText: '../../etc/passwd',
  },
  {
    name: 'rejects a wrong schemaVersion',
    payload: { ...makeRawReport(), schemaVersion: '2.0' },
    sensitiveText: '2.0',
  },
  {
    name: 'rejects a wrong scope',
    payload: { ...makeRawReport(), scope: 'sensitive-full-scope' },
    sensitiveText: 'sensitive-full-scope',
  },
  {
    name: 'rejects a non-UTC generatedAt',
    payload: { ...makeRawReport(), generatedAt: '2026-11-23T10:15:00+09:00' },
    sensitiveText: '2026-11-23T10:15:00+09:00',
  },
  {
    name: 'rejects an impossible generatedAt',
    payload: { ...makeRawReport(), generatedAt: '2026-13-45T10:15:00Z' },
    sensitiveText: '2026-13-45T10:15:00Z',
  },
  {
    name: 'rejects duplicate check ids',
    payload: { ...makeRawReport(), checks: withDuplicateCheckId() },
  },
  {
    name: 'rejects an over-long detail',
    payload: { ...makeRawReport(), checks: withCheck(0, { detail: 'X'.repeat(513) }) },
    sensitiveText: 'X'.repeat(513),
  },
  {
    name: 'rejects an over-long remediation',
    payload: { ...makeRawReport(), checks: withCheck(0, { remediation: 'Y'.repeat(513) }) },
    sensitiveText: 'Y'.repeat(513),
  },
  {
    name: 'rejects an invalid evidence key',
    payload: { ...makeRawReport(), checks: withCheck(0, { evidence: { '../secret': true } }) },
    sensitiveText: '../secret',
  },
  {
    name: 'rejects an over-long evidence value',
    payload: {
      ...makeRawReport(),
      checks: withCheck(0, { evidence: { mode: 'Z'.repeat(257) } }),
    },
    sensitiveText: 'Z'.repeat(257),
  },
  {
    name: 'rejects a non-integer evidence value',
    payload: { ...makeRawReport(), checks: withCheck(0, { evidence: { count: 1.5 } }) },
  },
  {
    name: 'rejects a nested evidence value',
    payload: {
      ...makeRawReport(),
      checks: withCheck(0, {
        evidence: { mode: { nested: 'secret' } },
      } as unknown as Partial<DoctorCheck>),
    },
    sensitiveText: 'secret',
  },
  {
    name: 'rejects more than 16 evidence entries',
    payload: {
      ...makeRawReport(),
      checks: withCheck(0, {
        evidence: Object.fromEntries(
          Array.from({ length: 17 }, (_, index) => [`key${index}`, index]),
        ),
      }),
    },
    sensitiveText: 'key16',
  },
];

function mockResponse(payload: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(payload),
  } as unknown as Response;
}

function renderApp() {
  return render(<App />);
}

function runDoctor(token: string, label = 'Bearer token', button = 'Run doctor') {
  fireEvent.change(screen.getByLabelText(label), { target: { value: token } });
  fireEvent.click(screen.getByRole('button', { name: button }));
}

const consoleCalls: unknown[][] = [];

function expectTokenAbsentFromStorageAndBrowser() {
  expect(window.location.href).not.toContain(TOKEN);
  expect(document.cookie).not.toContain(TOKEN);
  expect(JSON.stringify(consoleCalls)).not.toContain(TOKEN);
}

describe('doctor panel', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    document.documentElement.lang = 'en';
    consoleCalls.length = 0;
    for (const method of ['log', 'debug', 'info', 'warn', 'error'] as const) {
      vi.spyOn(console, method).mockImplementation((...args: unknown[]) => {
        consoleCalls.push(args);
      });
    }
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('renders a successful redacted report and clears the token after the request', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const checks = makeChecks();
    checks[0] = {
      ...checks[0]!,
      detail: 'Environment verified.',
      remediation: 'No action needed.',
      evidence: { platform: 'paper', checks: 13, available: true },
    };
    const report = makeReport({ checks });
    vi.mocked(fetch).mockResolvedValue(mockResponse(report));

    renderApp();
    expect(screen.getByLabelText('Bearer token')).toHaveAttribute('type', 'password');
    runDoctor(TOKEN);

    expect(await screen.findByText('Environment verified.')).toBeInTheDocument();
    expect(screen.getByText('Pass')).toBeInTheDocument();
    expect(screen.getAllByText('Warning').length).toBeGreaterThan(0);
    expect(screen.getByText('1.0')).toBeInTheDocument();
    expect(screen.getByText('limited')).toBeInTheDocument();
    expect(screen.getByText('Generated at (UTC)')).toBeInTheDocument();
    expect(document.querySelector('time')).toHaveAttribute('dateTime', '2026-11-23T10:15:00Z');
    expect(screen.getByText('capabilities detail')).toBeInTheDocument();
    expect(screen.getByText('This baseline is not a support-tier verdict.')).toBeInTheDocument();
    expect(screen.getByLabelText('Bearer token')).toHaveValue('');

    expect(fetch).toHaveBeenCalledTimes(1);
    const [url, init] = vi.mocked(fetch).mock.calls[0]!;
    expect(url).toBe(ENDPOINT);
    expect(init?.headers).toEqual(expect.objectContaining({ Authorization: `Bearer ${TOKEN}` }));

    expect(document.body.textContent).not.toContain(TOKEN);
    expect(setItem.mock.calls.map((args) => args.join('=')).join('\n')).not.toContain(TOKEN);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it('shows a localized error for 401 and clears the token', async () => {
    vi.mocked(fetch).mockResolvedValue(mockResponse({}, 401));

    renderApp();
    runDoctor(TOKEN);

    expect(
      await screen.findByText('Unauthorized. Check the token and try again.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Bearer token')).toHaveValue('');
    expect(screen.queryByText('limited')).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain(TOKEN);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it('shows a localized error for a non-OK response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockResponse({}, 500));

    renderApp();
    runDoctor(TOKEN);

    expect(await screen.findByText('The doctor request failed.')).toBeInTheDocument();
    expect(screen.getByLabelText('Bearer token')).toHaveValue('');
    expect(document.body.textContent).not.toContain(TOKEN);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it('never writes the token to local or session storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    vi.mocked(fetch).mockResolvedValue(mockResponse(makeReport()));

    renderApp();
    runDoctor(TOKEN);
    await screen.findByText('limited');

    const written = setItem.mock.calls.map((args) => args.join('=')).join('\n');
    expect(written).toContain('deploymc.locale');
    expect(written).not.toContain(TOKEN);
    expect(window.sessionStorage.length).toBe(0);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it('uses Japanese labels for the panel, statuses, and reminder', async () => {
    const report = makeReport({
      checks: [
        { ...makeChecks()[0]!, detail: '環境を確認しました。', remediation: '対処は不要です。' },
        ...makeChecks().slice(1),
      ],
    });
    vi.mocked(fetch).mockResolvedValue(mockResponse(report));

    renderApp();
    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'ja' },
    });
    expect(screen.getByRole('button', { name: '診断を実行' })).toBeInTheDocument();
    expect(screen.getByText('自己診断')).toBeInTheDocument();

    runDoctor(TOKEN, 'Bearer トークン', '診断を実行');
    expect(await screen.findByText('環境を確認しました。')).toBeInTheDocument();
    expect(screen.getAllByText('警告').length).toBeGreaterThan(0);
    expect(screen.getByText('この基準はサポートレベルの判定ではありません。')).toBeInTheDocument();
    expect(screen.getAllByText('実装済み').length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Bearer トークン')).toHaveValue('');
    expect(document.body.textContent).not.toContain(TOKEN);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it('redacts path-like text in server-provided prose', async () => {
    const checks = makeChecks();
    checks[0] = {
      ...checks[0]!,
      detail: 'Read /opt/server/plugins/config.yml',
      remediation: 'Use C:\\tools\\fix.exe or admin@example.com',
    };
    vi.mocked(fetch).mockResolvedValue(mockResponse(makeReport({ checks })));

    renderApp();
    runDoctor(TOKEN);
    await screen.findByText('Read [redacted]');

    const body = document.body.textContent ?? '';
    expect(body).not.toContain('/opt/server/plugins/config.yml');
    expect(body).not.toContain('C:\\tools\\fix.exe');
    expect(body).not.toContain('admin@example.com');
    expect(body).not.toContain(TOKEN);
  });

  it('renders only allow-listed evidence and redacts unsafe values', async () => {
    const checks = makeChecks();
    checks[0] = {
      ...checks[0]!,
      detail: 'Environment verified.',
      evidence: {
        mode: 'token',
        checks: 13,
        available: true,
        platform: '/var/lib/deploymc',
        path: '/opt/server/plugins',
        identity: 'admin',
        remoteAddress: '192.0.2.10',
        suspicious: 'C:\\secret\\token',
      },
    };
    vi.mocked(fetch).mockResolvedValue(mockResponse(makeReport({ checks })));

    renderApp();
    runDoctor(TOKEN);
    await screen.findByText('Environment verified.');

    expect(screen.getAllByText('Evidence').length).toBeGreaterThan(0);
    expect(screen.getByText('mode')).toBeInTheDocument();
    expect(screen.getByText('token')).toBeInTheDocument();
    expect(screen.getByText('13')).toBeInTheDocument();
    expect(screen.getByText('[redacted]')).toBeInTheDocument();
    const body = document.body.textContent ?? '';
    expect(body).not.toContain('/var/lib/deploymc');
    expect(body).not.toContain('/opt/server/plugins');
    expect(body).not.toContain('192.0.2.10');
    expect(body).not.toContain('C:\\secret\\token');
    expect(body).not.toContain(TOKEN);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it.each(INVALID_REPORT_CASES)('$name', async ({ payload, sensitiveText }) => {
    vi.mocked(fetch).mockResolvedValue(mockResponse(payload));

    renderApp();
    runDoctor(TOKEN);

    expect(
      await screen.findByText('The doctor response was invalid or contained sensitive text.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Bearer token')).toHaveValue('');
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(document.querySelector('.doctor__report')).not.toBeInTheDocument();
    expect(document.querySelector('time')).not.toBeInTheDocument();
    expect(screen.queryByText('1.0')).not.toBeInTheDocument();
    expect(screen.queryByText('limited')).not.toBeInTheDocument();

    const body = document.body.textContent ?? '';
    if (sensitiveText !== undefined) {
      expect(body).not.toContain(sensitiveText);
    }
    expect(body).not.toContain(TOKEN);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it('rejects a report that echoes the token', async () => {
    const report = makeReport({
      checks: [{ ...makeChecks()[0]!, detail: `Echo: ${TOKEN}` }, ...makeChecks().slice(1)],
    });
    vi.mocked(fetch).mockResolvedValue(mockResponse(report));

    renderApp();
    runDoctor(TOKEN);

    expect(
      await screen.findByText('The doctor response was invalid or contained sensitive text.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Bearer token')).toHaveValue('');
    expect(document.body.textContent).not.toContain(TOKEN);
    expectTokenAbsentFromStorageAndBrowser();
  });

  it('aborts an in-flight request on unmount', async () => {
    let signal: AbortSignal | undefined;
    vi.mocked(fetch).mockImplementation((_url, init) => {
      signal = init?.signal ?? undefined;
      return new Promise<Response>(() => undefined);
    });

    const { unmount } = renderApp();
    runDoctor(TOKEN);
    await waitFor(() => expect(signal).toBeDefined());

    unmount();
    expect(signal?.aborted).toBe(true);
    expect(document.body.textContent ?? '').not.toContain(TOKEN);
  });
});
