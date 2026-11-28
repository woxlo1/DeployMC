import { createContext, useContext } from 'react';
import type { TranslationValues } from '../i18n';

export type ToastTone = 'info' | 'success' | 'error';

export interface ToastRequest {
  readonly messageKey: string;
  readonly values?: TranslationValues;
  readonly tone?: ToastTone;
  readonly requestId?: string;
}

export interface ToastItem extends ToastRequest {
  readonly id: string;
}

export interface ToastApi {
  show(request: ToastRequest): void;
  dismiss(id: string): void;
}

export const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (api === null) {
    throw new Error('ToastProvider is missing');
  }
  return api;
}
