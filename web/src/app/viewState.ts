export const VIEW_STATES = ['loading', 'empty', 'error'] as const;

export type ViewStateKind = (typeof VIEW_STATES)[number];

export function isViewStateKind(value: string | null): value is ViewStateKind {
  return value !== null && (VIEW_STATES as readonly string[]).includes(value);
}
