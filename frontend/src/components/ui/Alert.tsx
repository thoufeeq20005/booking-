import type { ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react';
import clsx from 'clsx';

type Tone = 'info' | 'success' | 'error';

const styles: Record<Tone, string> = {
  info: 'border-brand-200 bg-brand-50 text-brand-900',
  success: 'border-success/30 bg-success/10 text-fg',
  error: 'border-danger/30 bg-danger/10 text-fg',
};

const icons = { info: Info, success: CheckCircle2, error: AlertTriangle };

export function Alert({ tone = 'info', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  const Icon = icons[tone];
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={clsx('flex gap-2.5 rounded-xl border px-3.5 py-3 text-sm', styles[tone], className)}
    >
      <Icon
        className={clsx('mt-0.5 h-4 w-4 shrink-0', tone === 'error' ? 'text-danger' : tone === 'success' ? 'text-success' : 'text-brand-600')}
        aria-hidden="true"
      />
      <div>{children}</div>
    </div>
  );
}
