import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Clapperboard, Plus } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField, TextAreaField } from '@/components/ui/FormFields';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import { Poster, useVenueFormat } from '@/features/venue/shared';
import { venueApi, type Movie } from '@/services/venueApi';

const EMPTY = { title: '', languages: '', genres: '', durationMinutes: '', synopsis: '', cast: '', releaseDate: '', posterFrom: '#1d4ed8', posterTo: '#0f172a' };

export function AdminMoviesPage() {
  const { t } = useTranslation();
  const f = useVenueFormat();
  const qc = useQueryClient();
  const form = useApiForm();
  const list = useQuery({ queryKey: ['admin-movies'], queryFn: venueApi.adminMovies });
  const [v, setV] = useState(EMPTY);
  const [certificate, setCertificate] = useState<Movie['certificate']>('UA');
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));

  const add = async (e: FormEvent) => {
    e.preventDefault();
    const m = await form.submit(() => venueApi.addMovie({
      ...v, title: v.title.trim(), languages: v.languages.trim(), genres: v.genres.trim(), certificate,
      durationMinutes: Number(v.durationMinutes), synopsis: v.synopsis.trim() || undefined, cast: v.cast.trim() || undefined,
    }));
    if (m) { setV(EMPTY); void qc.invalidateQueries({ queryKey: ['admin-movies'] }); }
  };
  const toggle = async (id: number, active: boolean) => {
    await form.submit(() => venueApi.setMovieActive(id, active));
    void qc.invalidateQueries({ queryKey: ['admin-movies'] });
  };

  return (
    <div className="space-y-6">
      <h1 className="flex items-center gap-2 text-2xl font-bold tracking-tight"><Clapperboard className="h-6 w-6 text-brand-600" aria-hidden="true" />{t('venue.catalogue')}</h1>
      <p className="text-sm text-fg-muted">{t('venue.catalogueHint')}</p>
      <Card className="p-6">
        <h2 className="font-semibold">{t('venue.addMovie')}</h2>
        <form onSubmit={add} className="mt-4 grid gap-3 sm:grid-cols-[1fr_1fr_10rem]">
          <TextField label={t('venue.movieTitle')} value={v.title} onChange={set('title')} error={form.fieldErrors.title} maxLength={150} />
          <TextField label={t('venue.languages')} placeholder="Tamil, Telugu" value={v.languages} onChange={set('languages')} error={form.fieldErrors.languages} maxLength={120} />
          <SelectField label={t('venue.certificate')} value={certificate} onChange={(e) => setCertificate(e.target.value as Movie['certificate'])}>
            {['U', 'UA', 'A'].map((c) => <option key={c} value={c}>{c}</option>)}
          </SelectField>
          <TextField label={t('venue.genres')} placeholder="Drama, Thriller" value={v.genres} onChange={set('genres')} error={form.fieldErrors.genres} maxLength={120} />
          <TextField label={t('venue.runtimeMinutes')} inputMode="numeric" value={v.durationMinutes} onChange={set('durationMinutes')} error={form.fieldErrors.durationMinutes} />
          <TextField label={t('venue.releaseDate')} type="date" value={v.releaseDate} onChange={set('releaseDate')} error={form.fieldErrors.releaseDate} />
          <TextField className="sm:col-span-2" label={t('venue.cast')} value={v.cast} onChange={set('cast')} maxLength={300} />
          <div className="flex items-end gap-3">
            <label className="text-sm">{t('venue.posterColours')}
              <span className="mt-1.5 flex gap-2">
                <input type="color" aria-label={t('venue.posterTop')} value={v.posterFrom} onChange={set('posterFrom')} className="h-11 w-14 rounded-lg border border-border" />
                <input type="color" aria-label={t('venue.posterBottom')} value={v.posterTo} onChange={set('posterTo')} className="h-11 w-14 rounded-lg border border-border" />
              </span>
            </label>
          </div>
          <TextAreaField className="sm:col-span-3" label={t('venue.synopsis')} rows={2} maxLength={1000} value={v.synopsis} onChange={set('synopsis')} />
          {form.formError && <Alert tone="error" className="sm:col-span-3">{form.formError}</Alert>}
          <div className="sm:col-span-3"><Button type="submit" loading={form.submitting}><Plus className="h-4 w-4" aria-hidden="true" />{t('venue.addMovie')}</Button></div>
        </form>
      </Card>
      {list.isLoading && <LoadingState />}
      {list.error && <ErrorState error={list.error} onRetry={() => void list.refetch()} />}
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {list.data?.map((m) => (
          <li key={m.id}>
            <Card className={`flex gap-4 p-4 ${m.active ? '' : 'opacity-60'}`}>
              <Poster movie={m} size="sm" className="w-20 shrink-0" />
              <div className="min-w-0 space-y-1 text-sm">
                <p className="font-semibold">{m.title}</p>
                <p className="text-fg-muted">{m.certificate} · {f.runtime(m.durationMinutes)} · {m.languages}</p>
                <p className="text-fg-muted">{m.genres}</p>
                <Button size="sm" variant={m.active ? 'ghost' : 'secondary'} onClick={() => void toggle(m.id, !m.active)}>
                  {t(m.active ? 'venue.retire' : 'venue.restore')}
                </Button>
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </div>
  );
}
