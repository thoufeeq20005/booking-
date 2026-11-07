import type { ReactNode } from 'react';
import { Link } from 'react-router';
import clsx from 'clsx';
import { Card } from './Card';

interface Props {
  label: string;
  value: ReactNode;
  /** Short supporting line under the number, e.g. "+3 this week". */
  note?: ReactNode;
  to?: string;
  /** Draws attention when something needs action; the label still says what. */
  emphasis?: boolean;
}

/** A single headline number. Text stays in text colours; emphasis is a border, not a coloured number. */
export function StatTile({ label, value, note, to, emphasis }: Props) {
  const body = (
    <Card className={clsx('h-full p-5 transition', emphasis && 'border-brand-400', to && 'hover:border-brand-300')}>
      <p className="text-sm text-fg-muted">{label}</p>
      <p className="mt-1 text-3xl font-bold tabular-nums tracking-tight">{value}</p>
      {note && <p className="mt-1 text-xs text-fg-muted">{note}</p>}
    </Card>
  );
  return to ? (
    <Link to={to} className="block h-full rounded-2xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-500">
      {body}
    </Link>
  ) : (
    body
  );
}
