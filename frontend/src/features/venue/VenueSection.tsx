import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CheckboxField, SelectField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import type { OrganizationDetail } from '@/services/organizationApi';
import { venueApi, type SeatRow, type VenueSetup } from '@/services/venueApi';
import { formatINR } from '@/utils/format';
import { SeatGrid } from './SeatGrid';
import { FoodMark } from './DiningPanel';
import { istInstant, useVenueFormat } from './shared';

export const venueKey = (orgId: number) => ['venue-setup', orgId] as const;

function useApply(orgId: number) {
  const qc = useQueryClient();
  return (d?: VenueSetup) => d && qc.setQueryData(venueKey(orgId), d);
}

function ProfileForm({ orgId, setup }: { orgId: number; setup: VenueSetup }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const apply = useApply(orgId);
  const p = setup.profile;
  const dining = setup.kind === 'DINING';
  const [v, setV] = useState({
    cuisines: p?.cuisines ?? '',
    costForTwo: p?.costForTwo != null ? String(p.costForTwo) : '',
    capacityPerSlot: p?.capacityPerSlot != null ? String(p.capacityPerSlot) : '',
    offerText: p?.offerText ?? '',
    features: p?.features ?? '',
  });
  const [veg, setVeg] = useState(p?.vegOnly ?? false);
  const [saved, setSaved] = useState(false);
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaved(false);
    const d = await form.submit(() => venueApi.saveProfile(orgId, {
      cuisines: v.cuisines.trim() || undefined,
      costForTwo: v.costForTwo.trim() || undefined,
      capacityPerSlot: v.capacityPerSlot.trim() ? Number(v.capacityPerSlot) : undefined,
      offerText: v.offerText.trim() || undefined,
      features: v.features.trim() || undefined,
      vegOnly: veg,
    }));
    if (d) { apply(d); setSaved(true); }
  };
  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{t('venue.settings')}</h2>
      {!p && <Alert className="mt-3">{t(dining ? 'venue.settingsFirstDining' : 'venue.settingsFirstCinema')}</Alert>}
      <form onSubmit={submit} className="mt-4 grid gap-4 sm:grid-cols-2">
        {dining && <TextField label={t('venue.cuisines')} placeholder="South Indian, Chettinad" value={v.cuisines} onChange={set('cuisines')} error={form.fieldErrors.cuisines} maxLength={200} />}
        {dining && <TextField label={t('venue.costForTwoLabel')} inputMode="decimal" value={v.costForTwo} onChange={set('costForTwo')} error={form.fieldErrors.costForTwo} />}
        {dining && <TextField label={t('venue.capacity')} hint={t('venue.capacityHint')} inputMode="numeric" value={v.capacityPerSlot} onChange={set('capacityPerSlot')} error={form.fieldErrors.capacityPerSlot} />}
        <TextField label={t('venue.offer')} value={v.offerText} onChange={set('offerText')} error={form.fieldErrors.offerText} maxLength={200} />
        <TextField className="sm:col-span-2" label={t('venue.features')} hint={t('venue.featuresHint')} value={v.features} onChange={set('features')} error={form.fieldErrors.features} maxLength={300} />
        {dining && <CheckboxField label={t('venue.pureVeg')} checked={veg} onChange={setVeg} />}
        <div className="flex items-center gap-3 sm:col-span-2">
          <Button type="submit" loading={form.submitting}>{t('common.save')}</Button>
          {saved && <span className="text-sm text-success">{t('venue.saved')}</span>}
        </div>
        {form.formError && <Alert tone="error" className="sm:col-span-2">{form.formError}</Alert>}
      </form>
    </Card>
  );
}

function MenuEditor({ orgId, setup }: { orgId: number; setup: VenueSetup }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const apply = useApply(orgId);
  const [v, setV] = useState({ section: setup.menu.at(-1)?.section ?? '', name: '', description: '', price: '' });
  const [veg, setVeg] = useState(true);
  const [best, setBest] = useState(false);
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));
  const add = async (e: FormEvent) => {
    e.preventDefault();
    const d = await form.submit(() => venueApi.addDish(orgId, {
      section: v.section.trim(), name: v.name.trim(), description: v.description.trim() || undefined, price: v.price.trim(), veg, bestseller: best,
    }));
    if (d) { apply(d); setV((s) => ({ ...s, name: '', description: '', price: '' })); setBest(false); }
  };
  const sections = [...new Set(setup.menu.map((m) => m.section))];
  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{t('venue.menu')}</h2>
      {setup.menu.length === 0 && <p className="mt-2 text-sm text-fg-muted">{t('venue.noMenu')}</p>}
      {sections.map((sec) => (
        <section key={sec} className="mt-4">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-fg-muted">{sec}</h3>
          <ul className="mt-1 divide-y divide-border">
            {setup.menu.filter((m) => m.section === sec).map((m) => (
              <li key={m.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                <span className="flex items-center gap-2"><FoodMark veg={m.veg} />{m.name}{m.bestseller && <span className="text-xs text-amber-700">★ {t('venue.bestseller')}</span>}</span>
                <span className="flex items-center gap-2">{formatINR(m.price)}
                  <Button variant="ghost" size="sm" aria-label={t('venue.removeDish', { name: m.name })}
                    onClick={() => void form.submit(() => venueApi.deleteDish(orgId, m.id)).then(apply)}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      ))}
      <form onSubmit={add} className="mt-6 grid gap-3 border-t border-border pt-4 sm:grid-cols-2">
        <TextField label={t('venue.section')} placeholder="Starters" list="menu-sections" value={v.section} onChange={set('section')} error={form.fieldErrors.section} maxLength={60} />
        <datalist id="menu-sections">{sections.map((s) => <option key={s} value={s} />)}</datalist>
        <TextField label={t('venue.dish')} value={v.name} onChange={set('name')} error={form.fieldErrors.name} maxLength={120} />
        <TextField label={t('venue.dishDescription')} value={v.description} onChange={set('description')} error={form.fieldErrors.description} maxLength={300} />
        <TextField label={t('venue.price')} inputMode="decimal" value={v.price} onChange={set('price')} error={form.fieldErrors.price} />
        <CheckboxField label={t('venue.veg')} checked={veg} onChange={setVeg} />
        <CheckboxField label={t('venue.bestseller')} checked={best} onChange={setBest} />
        {form.formError && <Alert tone="error" className="sm:col-span-2">{form.formError}</Alert>}
        <div className="sm:col-span-2"><Button type="submit" loading={form.submitting}><Plus className="h-4 w-4" aria-hidden="true" />{t('venue.addDish')}</Button></div>
      </form>
    </Card>
  );
}

type RowGroup = { from: string; to: string; seats: string; tier: string; aisles: string };
const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';

/** Expands "rows A–G, 18 seats, CLASSIC, aisles after 4, 14" groups into one entry per row. */
export function expandRows(groups: RowGroup[]): SeatRow[] {
  const out: SeatRow[] = [];
  for (const g of groups) {
    const a = LETTERS.indexOf(g.from.trim().toUpperCase());
    const b = LETTERS.indexOf((g.to.trim() || g.from).toUpperCase());
    const seats = Number(g.seats);
    if (a < 0 || b < a || !Number.isInteger(seats) || seats < 1) continue;
    const gaps = g.aisles.split(',').map((x) => Number(x.trim())).filter((n) => Number.isInteger(n) && n > 0 && n < seats);
    for (let i = a; i <= b; i++) out.push({ row: LETTERS[i]!, tier: g.tier.trim().toUpperCase() || 'CLASSIC', seats, gapsAfter: gaps });
  }
  return out;
}

function ScreenEditor({ org, setup }: { org: OrganizationDetail; setup: VenueSetup }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const apply = useApply(org.summary.id);
  const [branchId, setBranchId] = useState(org.branches[0]?.id ?? 0);
  const [name, setName] = useState('');
  const [groups, setGroups] = useState<RowGroup[]>([
    { from: 'A', to: 'F', seats: '14', tier: 'CLASSIC', aisles: '3, 11' },
    { from: 'G', to: 'I', seats: '14', tier: 'PRIME', aisles: '3, 11' },
  ]);
  const rows = expandRows(groups);
  const setGroup = (i: number, k: keyof RowGroup, value: string) => setGroups((gs) => gs.map((g, j) => (j === i ? { ...g, [k]: value } : g)));
  const add = async (e: FormEvent) => {
    e.preventDefault();
    const d = await form.submit(() => venueApi.addScreen(org.summary.id, { branchId, name: name.trim(), rows }));
    if (d) { apply(d); setName(''); }
  };
  const preview = { showtimeId: 0, startsAt: '', format: '', language: '', theatreName: '', branchName: '', screenName: '', rows, taken: [], bookable: false,
    prices: Object.fromEntries(rows.map((r) => [r.tier, 0])), movie: { id: 0, title: '', languages: '', certificate: 'U' as const, genres: '', durationMinutes: 0, releaseDate: '', posterFrom: '', posterTo: '' } };

  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{t('venue.screens')}</h2>
      {setup.screens.length === 0 && <p className="mt-2 text-sm text-fg-muted">{t('venue.noScreens')}</p>}
      <ul className="mt-3 divide-y divide-border">
        {setup.screens.map((s) => (
          <li key={s.id} className="flex items-center justify-between gap-3 py-2 text-sm">
            <span><span className="font-medium">{s.name}</span> · {s.branchName} · {t('venue.seatCount', { count: s.totalSeats })} · {s.tiers.map((x) => t(`venue.tier.${x}`, { defaultValue: x })).join(', ')}</span>
            <Button variant="ghost" size="sm" aria-label={t('venue.removeScreen', { name: s.name })}
              onClick={() => void form.submit(() => venueApi.deleteScreen(org.summary.id, s.id)).then(apply)}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
          </li>
        ))}
      </ul>
      <form onSubmit={add} className="mt-6 space-y-4 border-t border-border pt-4">
        <h3 className="font-medium">{t('venue.addScreen')}</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          {org.branches.length > 1 && (
            <SelectField label={t('team.branch')} value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
              {org.branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </SelectField>
          )}
          <TextField label={t('venue.screenName')} placeholder="Screen 1" value={name} onChange={(e) => setName(e.target.value)} error={form.fieldErrors.name} maxLength={40} />
        </div>
        <div className="space-y-2">
          <p className="text-sm font-medium">{t('venue.rowGroups')}</p>
          {groups.map((g, i) => (
            <div key={i} className="grid grid-cols-2 items-end gap-2 sm:grid-cols-[4rem_4rem_5rem_1fr_1fr_auto]">
              <TextField label={t('venue.fromRow')} value={g.from} maxLength={1} onChange={(e) => setGroup(i, 'from', e.target.value)} />
              <TextField label={t('venue.toRow')} value={g.to} maxLength={1} onChange={(e) => setGroup(i, 'to', e.target.value)} />
              <TextField label={t('venue.seatsPerRow')} inputMode="numeric" value={g.seats} onChange={(e) => setGroup(i, 'seats', e.target.value)} />
              <SelectField label={t('venue.seatClass')} value={g.tier} onChange={(e) => setGroup(i, 'tier', e.target.value)}>
                {['CLASSIC', 'PRIME', 'RECLINER'].map((x) => <option key={x} value={x}>{t(`venue.tier.${x}`)}</option>)}
              </SelectField>
              <TextField label={t('venue.aislesAfter')} placeholder="4, 12" value={g.aisles} onChange={(e) => setGroup(i, 'aisles', e.target.value)} />
              <Button type="button" variant="ghost" aria-label={t('venue.removeRows')} onClick={() => setGroups((gs) => gs.filter((_, j) => j !== i))}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
            </div>
          ))}
          <Button type="button" variant="secondary" size="sm"
            onClick={() => setGroups((gs) => { const last = rows.at(-1)?.row ?? '@'; const next = LETTERS[LETTERS.indexOf(last) + 1] ?? 'A'; return [...gs, { from: next, to: next, seats: '10', tier: 'RECLINER', aisles: '' }]; })}>
            <Plus className="h-4 w-4" aria-hidden="true" />{t('venue.addRows')}
          </Button>
        </div>
        {rows.length > 0 && (
          <div className="rounded-xl border border-dashed border-border p-3">
            <p className="mb-2 text-xs text-fg-muted">{t('venue.preview', { count: rows.reduce((n, r) => n + r.seats, 0) })}</p>
            <SeatGrid map={preview} selected={[]} onToggle={() => undefined} />
          </div>
        )}
        {form.fieldErrors.rows && <Alert tone="error">{form.fieldErrors.rows}</Alert>}
        {form.formError && <Alert tone="error">{form.formError}</Alert>}
        <Button type="submit" loading={form.submitting} disabled={rows.length === 0}><Plus className="h-4 w-4" aria-hidden="true" />{t('venue.saveScreen')}</Button>
      </form>
    </Card>
  );
}

function ShowEditor({ orgId, setup }: { orgId: number; setup: VenueSetup }) {
  const { t } = useTranslation();
  const f = useVenueFormat();
  const form = useApiForm();
  const apply = useApply(orgId);
  const movies = useQuery({ queryKey: ['owner-movies'], queryFn: venueApi.ownerMovies });
  const [screenId, setScreenId] = useState(setup.screens[0]?.id ?? 0);
  const [movieId, setMovieId] = useState(0);
  const [date, setDate] = useState(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date(Date.now() + 86400000)));
  const [time, setTime] = useState('18:00');
  const [format, setFormat] = useState('2D');
  const [language, setLanguage] = useState('');
  const [prices, setPrices] = useState<Record<string, string>>({ CLASSIC: '150', PRIME: '220', RECLINER: '400' });
  const screen = setup.screens.find((s) => s.id === screenId) ?? setup.screens[0];
  const movie = movies.data?.find((m) => m.id === movieId) ?? movies.data?.[0];
  const languages = movie?.languages.split(',').map((x) => x.trim()).filter(Boolean) ?? [];
  if (!screen) return null;

  const add = async (e: FormEvent) => {
    e.preventDefault();
    if (!movie) return;
    const d = await form.submit(() => venueApi.addShow(orgId, {
      screenId: screen.id, movieId: movie.id, startsAt: istInstant(date, time), format,
      language: languages.includes(language) ? language : languages[0] ?? '',
      prices: Object.fromEntries(screen.tiers.map((x) => [x, prices[x] ?? ''])),
    }));
    if (d) apply(d);
  };

  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{t('venue.shows')}</h2>
      <form onSubmit={add} className="mt-4 grid gap-3 sm:grid-cols-3">
        <SelectField label={t('venue.screen')} value={screen.id} onChange={(e) => setScreenId(Number(e.target.value))}>
          {setup.screens.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </SelectField>
        <SelectField label={t('venue.movie')} value={movie?.id ?? ''} onChange={(e) => setMovieId(Number(e.target.value))} error={form.fieldErrors.movieId}>
          {movies.data?.map((m) => <option key={m.id} value={m.id}>{m.title} ({m.certificate}, {f.runtime(m.durationMinutes)})</option>)}
        </SelectField>
        <SelectField label={t('venue.language')} value={languages.includes(language) ? language : languages[0] ?? ''} onChange={(e) => setLanguage(e.target.value)}>
          {languages.map((l) => <option key={l} value={l}>{l}</option>)}
        </SelectField>
        <TextField label={t('booking.date')} type="date" value={date} onChange={(e) => setDate(e.target.value)} error={form.fieldErrors.startsAt} />
        <TextField label={t('booking.time')} type="time" step={300} value={time} onChange={(e) => setTime(e.target.value)} />
        <SelectField label={t('venue.format')} value={format} onChange={(e) => setFormat(e.target.value)}>
          {['2D', '3D', 'IMAX', '4DX'].map((x) => <option key={x} value={x}>{x}</option>)}
        </SelectField>
        {screen.tiers.map((x) => (
          <TextField key={x} label={t('venue.tierPrice', { tier: t(`venue.tier.${x}`, { defaultValue: x }) })} inputMode="decimal"
            value={prices[x] ?? ''} onChange={(e) => setPrices((p) => ({ ...p, [x]: e.target.value }))} error={form.fieldErrors.prices} />
        ))}
        {form.formError && <Alert tone="error" className="sm:col-span-3">{form.formError}</Alert>}
        <div className="sm:col-span-3"><Button type="submit" loading={form.submitting} disabled={!movie}><Plus className="h-4 w-4" aria-hidden="true" />{t('venue.addShow')}</Button></div>
      </form>
      <div className="mt-6 overflow-x-auto border-t border-border pt-4">
        {setup.shows.length === 0 ? <p className="text-sm text-fg-muted">{t('venue.noUpcomingShows')}</p> : (
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-fg-muted">
              <tr><th className="py-2 pr-3">{t('booking.when')}</th><th className="pr-3">{t('venue.movie')}</th><th className="pr-3">{t('venue.screen')}</th><th className="pr-3">{t('venue.sold')}</th><th /></tr>
            </thead>
            <tbody className="divide-y divide-border">
              {setup.shows.map((s) => (
                <tr key={s.id}>
                  <td className="py-2 pr-3 tabular-nums">{f.day(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date(s.startsAt)))} · {f.time(s.startsAt)}</td>
                  <td className="pr-3">{s.movieTitle} <span className="text-fg-muted">· {s.format} · {s.language}</span></td>
                  <td className="pr-3">{s.screenName}</td>
                  <td className="pr-3 tabular-nums">{s.sold}/{s.totalSeats}</td>
                  <td className="text-right">
                    {s.sold === 0 && (
                      <Button variant="ghost" size="sm" aria-label={t('venue.removeShow')}
                        onClick={() => void form.submit(() => venueApi.deleteShow(orgId, s.id)).then(apply)}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Card>
  );
}

/** The owner's "Venue" tab for cinemas and restaurants. */
export function VenueSection({ org }: { org: OrganizationDetail }) {
  const { t } = useTranslation();
  const id = org.summary.id;
  const q = useQuery({ queryKey: venueKey(id), queryFn: () => venueApi.setup(id) });
  if (q.isLoading) return <LoadingState />;
  const setup = q.data;
  if (!setup?.kind) return <EmptyState title={t('venue.notAVenue')} />;
  if (org.branches.length === 0) return <Alert>{t('venue.addBranchFirst')}</Alert>;
  return (
    <div className="space-y-6">
      <ProfileForm key={JSON.stringify(setup.profile ?? {})} orgId={id} setup={setup} />
      {setup.profile && setup.kind === 'DINING' && <MenuEditor orgId={id} setup={setup} />}
      {setup.profile && setup.kind === 'CINEMA' && <ScreenEditor org={org} setup={setup} />}
      {setup.profile && setup.kind === 'CINEMA' && setup.screens.length > 0 && <ShowEditor orgId={id} setup={setup} />}
    </div>
  );
}
