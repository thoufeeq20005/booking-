import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Flame } from 'lucide-react';
import clsx from 'clsx';
import { Card } from '@/components/ui/Card';
import type { DiningDetails } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

/** Veg/non-veg mark used on Indian menus: a square with a dot, green or red. */
export function FoodMark({ veg }: { veg: boolean }) {
  const { t } = useTranslation();
  return (
    <span role="img" aria-label={t(veg ? 'venue.veg' : 'venue.nonVeg')}
      className={clsx('inline-flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-sm border', veg ? 'border-emerald-600' : 'border-red-600')}>
      <span className={clsx('h-1.5 w-1.5 rounded-full', veg ? 'bg-emerald-600' : 'bg-red-600')} />
    </span>
  );
}

export function DiningMenu({ details }: { details: DiningDetails }) {
  const { t } = useTranslation();
  const [vegOnly, setVegOnly] = useState(false);
  return (
    <Card className="p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">{t('venue.menu')}</h2>
        {!details.vegOnly && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={vegOnly} onChange={(e) => setVegOnly(e.target.checked)} className="h-4 w-4 accent-emerald-600" />
            {t('venue.vegOnlyFilter')}
          </label>
        )}
      </div>
      {details.menu.length === 0 && <p className="mt-3 text-sm text-fg-muted">{t('venue.noMenu')}</p>}
      {details.menu.map((section) => {
        const items = section.items.filter((i) => !vegOnly || i.veg);
        if (items.length === 0) return null;
        return (
          <section key={section.name} className="mt-5">
            <h3 className="text-sm font-semibold uppercase tracking-wider text-fg-muted">{section.name}</h3>
            <ul className="mt-2 divide-y divide-border">
              {items.map((i) => (
                <li key={i.id} className="flex items-start justify-between gap-4 py-3 text-sm">
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 font-medium"><FoodMark veg={i.veg} />{i.name}
                      {i.bestseller && <span className="inline-flex items-center gap-0.5 rounded bg-amber-100 px-1.5 text-[11px] font-semibold text-amber-800"><Flame className="h-3 w-3" aria-hidden="true" />{t('venue.bestseller')}</span>}
                    </p>
                    {i.description && <p className="mt-0.5 text-fg-muted">{i.description}</p>}
                  </div>
                  <span className="shrink-0 font-medium">{formatINR(i.price)}</span>
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </Card>
  );
}
