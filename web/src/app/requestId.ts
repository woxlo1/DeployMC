let sequence = 0;

/**
 * Creates a short, non-sensitive correlation id for UI failures and error previews. It is safe to
 * display and report; it never contains a path, token, identity, or raw exception text.
 */
export function createRequestId(): string {
  sequence += 1;
  const random =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID().slice(0, 8)
      : `${Date.now().toString(36)}-${sequence}`;
  return `req_${random}`;
}
