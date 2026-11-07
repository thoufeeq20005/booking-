import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { CalendarCheck, Flame, Leaf, Tag, Users } from 'lucide-react';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField, TextAreaField } from '@/components/ui/FormFields';
import { LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { nextDays } from '@/services/bookingApi';
import type { PublicProfile } from '@/services/discoveryApi';
import { venueApi, type DiningDetails } from '@/services/venueApi';
import { formatINR } from '@/utils/format';
import { useVenueFormat } from './shared';

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

export function DiningFacts({ details }: { details: DiningDetails }) {
  const { t } = useTranslation();
  return (
    <Card className="space-y-3 p-6 text-sm">
      {details.cuisines && <p><span className="text-fg-muted">{t('venue.cuisines')}:</span> {details.cuisines}</p>}
      {details.costForTwo != null && <p>{t('venue.costForTwo', { price: formatINR(details.costForTwo) })}</p>}
      {details.vegOnly && <p className="flex items-center gap-1 font-medium text-emerald-700"><Leaf className="h-4 w-4" aria-hidden="true" />{t('venue.pureVeg')}</p>}
      {details.offer && <p className="flex items-center gap-2 rounded-xl bg-brand-50 p-3 font-medium text-brand-800"><Tag className="h-4 w-4" aria-hidden="true" />{details.offer}</p>}
      {details.features.length > 0 && (
        <ul className="flex flex-wrap gap-2">{details.features.map((f) => <li key={f} className="rounded-full border border-border px-3 py-1 text-xs">{f}</li>)}</ul>
      )}
    </Card>
  );
}

/** Party size → day → free sitting → confirm. Table bookings are free. */
export function TableBooking({ profile, details }: { profile: PublicProfile; details: DiningDetails }) {
  const { t } = useTranslation();
  const { status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const f = useVenueFormat();
  const form = useApiForm();
  const [branchId, setBranchId] = useState(profile.branches[0]?.id ?? 0);
  const [party, setParty] = useState(Math.min(2, details.maxPartySize));
  const [date, setDate] = useState(nextDays(1)[0]!);
  const [startsAt, setStartsAt] = useState<string>();
  const [note, setNote] = useState('');
  const slots = useQuery({
    queryKey: ['table-slots', branchId, date, party],
    queryFn: () => venueApi.tableSlots(branchId, date, party),
    enabled: branchId > 0,
  });
  if (!branchId) return null;
  const book = async () => {
    if (!startsAt) return;
    const b = await form.submit(() => venueApi.bookTable({ branchId, startsAt, partySize: party, note: note.trim() || undefined }));
    if (b) navigate(`/customer/bookings/${b.id}`, { state: { justBooked: true } });
    else void slots.refetch();
  };

  return (
    <Card className="space-y-4 p-5">
      <h2 className="flex items-center gap-2 text-lg font-semibold"><CalendarCheck className="h-5 w-5 text-brand-600" aria-hidden="true" />{t('venue.bookTable')}</h2>
      {profile.branches.length > 1 && (
        <SelectField label={t('team.branch')} value={branchId} onChange={(e) => { setBranchId(Number(e.target.value)); setStartsAt(undefined); }}>
          {profile.branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </SelectField>
      )}
      <div>
        <p className="mb-1.5 flex items-center gap-1 text-sm font-medium"><Users className="h-4 w-4" aria-hidden="true" />{t('venue.guests')}</p>
        <div role="radiogroup" aria-label={t('venue.guests')} className="flex flex-wrap gap-1.5">
          {Array.from({ length: details.maxPartySize }, (_, i) => i + 1).map((n) => (
            <button key={n} type="button" role="radio" aria-checked={n === party} onClick={() => { setParty(n); setStartsAt(undefined); }}
              className={clsx('h-9 w-9 rounded-lg border text-sm', n === party ? 'border-brand-600 bg-brand-600 text-white' : 'border-border hover:border-brand-400')}>
              {n}
            </button>
          ))}
        </div>
      </div>
      <div role="radiogroup" aria-label={t('booking.date')} className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
        {nextDays(14).map((d) => (
          <button key={d} type="button" role="radio" aria-checked={d === date} onClick={() => { setDate(d); setStartsAt(undefined); }}
            className={clsx('shrink-0 rounded-xl border px-3 py-2 text-sm', d === date ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-border hover:border-brand-300')}>
            {f.day(d)}
          </button>
        ))}
      </div>
      {slots.isLoading && <LoadingState />}
      {slots.data && (slots.data.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('venue.noTables')}</p>
      ) : (
        <div role="radiogroup" aria-label={t('booking.time')} className="flex flex-wrap gap-2">
          {slots.data.map((s) => (
            <button key={s.startsAt} type="button" role="radio" aria-checked={startsAt === s.startsAt} disabled={!s.available}
              onClick={() => setStartsAt(s.startsAt)}
              className={clsx('rounded-lg border px-3 py-1.5 text-sm tabular-nums',
                !s.available ? 'cursor-not-allowed border-border text-fg-muted line-through'
                  : startsAt === s.startsAt ? 'border-brand-600 bg-brand-600 text-white' : 'border-border hover:border-brand-400')}>
              {f.time(s.startsAt)}
            </button>
          ))}
        </div>
      ))}
      <p className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{t('venue.tableFree')}</p>
      {status === 'authenticated' ? (
        <>
          <TextAreaField label={t('venue.tableNote')} rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          {form.formError && <Alert tone="error">{form.formError}</Alert>}
          <Button className="w-full" disabled={!startsAt} loading={form.submitting} onClick={() => void book()}>
            {t('venue.reserve', { count: party })}
          </Button>
        </>
      ) : (
        <Link to={`/signin?next=${encodeURIComponent(location.pathname)}`} className={buttonClasses('primary', 'md', 'w-full')}>{t('booking.signInToBook')}</Link>
      )}
    </Card>
  );
}
