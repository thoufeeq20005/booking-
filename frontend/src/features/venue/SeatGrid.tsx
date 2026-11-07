import { Fragment, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { seatLabel, type SeatMap } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

/** Rows from the back (most expensive) to the front, the screen drawn at the bottom like in the hall. */
export function SeatGrid({ map, selected, onToggle }: { map: SeatMap; selected: string[]; onToggle: (seat: string) => void }) {
  const { t } = useTranslation();
  const taken = useMemo(() => new Set(map.taken), [map.taken]);
  const rows = [...map.rows].reverse();
  let lastTier = '';
  return (
    <div className="overflow-x-auto pb-2">
      <div className="mx-auto w-max space-y-1.5">
        {rows.map((r) => {
          const header = r.tier !== lastTier;
          lastTier = r.tier;
          return (
            <Fragment key={r.row}>
              {header && (
                <p className="pt-3 text-center text-xs font-semibold uppercase tracking-wider text-fg-muted">
                  {t(`venue.tier.${r.tier}`, { defaultValue: r.tier })} · {formatINR(map.prices[r.tier] ?? 0)}
                </p>
              )}
              <div className="flex items-center gap-1" role="group" aria-label={t('venue.row', { row: r.row })}>
                <span className="w-6 text-center text-xs font-medium text-fg-muted">{r.row}</span>
                {Array.from({ length: r.seats }, (_, i) => {
                  const n = i + 1;
                  const seat = seatLabel(r.row, n);
                  const isTaken = taken.has(seat);
                  const isMine = selected.includes(seat);
                  return (
                    <Fragment key={seat}>
                      <button type="button" disabled={isTaken || !map.bookable} aria-pressed={isMine}
                        aria-label={`${seat}${isTaken ? `, ${t('venue.legendSold')}` : ''}`} onClick={() => onToggle(seat)}
                        className={clsx('h-7 w-7 rounded-t-lg rounded-b-sm border text-[10px] font-medium tabular-nums transition',
                          isTaken ? 'cursor-not-allowed border-border bg-surface-muted text-transparent'
                            : isMine ? 'border-brand-600 bg-brand-600 text-white'
                              : 'border-emerald-500 text-emerald-700 hover:bg-emerald-50')}>
                        {n}
                      </button>
                      {r.gapsAfter.includes(n) && <span className="w-4" aria-hidden="true" />}
                    </Fragment>
                  );
                })}
                <span className="w-6" aria-hidden="true" />
              </div>
            </Fragment>
          );
        })}
        <div className="pt-6">
          <div className="mx-auto h-2 w-3/4 rounded-full bg-gradient-to-r from-brand-200 via-brand-500 to-brand-200" />
          <p className="mt-1 text-center text-xs text-fg-muted">{t('venue.screenThisWay')}</p>
        </div>
      </div>
    </div>
  );
}
