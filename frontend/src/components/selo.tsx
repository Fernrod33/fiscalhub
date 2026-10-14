import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export type Tom = 'info' | 'warning' | 'success' | 'danger';

const TONS: Record<Tom, string> = {
  info: 'bg-info-bg text-info',
  warning: 'bg-warning-bg text-warning',
  success: 'bg-success-bg text-success',
  danger: 'bg-danger-bg text-danger',
};

/** Selo de estado (cor só para estado; os rótulos e tons de cada enum ficam em lib/rotulos). */
export function Selo({ tom, children }: { tom: Tom; children: ReactNode }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-sm px-2 py-0.5 text-xs font-semibold', TONS[tom])}>
      {children}
    </span>
  );
}
