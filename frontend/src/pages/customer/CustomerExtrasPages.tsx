import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Star, Trash2 } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField, TextAreaField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import { bookingApi } from '@/services/bookingApi';
import { extrasApi, RELATIONS, type Relation } from '@/services/extrasApi';
import { formatDateTime } from '@/utils/format';

export function FamilyPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const form = useApiForm();
  const { data, isLoading } = useQuery({ queryKey: ['family'], queryFn: extrasApi.family });
  const [name, setName] = useState('');
  const [relation, setRelation] = useState<Relation>('CHILD');
  const [dob, setDob] = useState('');
  const refresh = () => qc.invalidateQueries({ queryKey: ['family'] });
  const add = async (e: FormEvent) => {
    e.preventDefault();
    const ok = await form.submit(() => extrasApi.addFamily({ fullName: name.trim(), relation, dateOfBirth: dob || undefined }));
    if (ok) { setName(''); setDob(''); void refresh(); }
  };
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('extras.family')}</h1>
      <p className="text-sm text-fg-muted">{t('extras.familyIntro')}</p>
      {isLoading ? <LoadingState /> : data?.length === 0 ? <EmptyState title={t('extras.noFamily')} /> : (
        <Card className="divide-y divide-border">
          {data?.map((m) => (
            <div key={m.id} className="flex items-center justify-between gap-3 p-4 text-sm">
              <span><span className="font-medium">{m.fullName}</span> · {t(`extras.relation.${m.relation}`)}{m.dateOfBirth ? ` · ${m.dateOfBirth}` : ''}</span>
              <Button variant="ghost" size="sm" aria-label={t('admin.delete')} onClick={() => void extrasApi.removeFamily(m.id).then(refresh)}>
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          ))}
        </Card>
      )}
      <Card className="p-5">
        <form onSubmit={add} className="grid gap-3 sm:grid-cols-4 sm:items-end" noValidate>
          <TextField label={t('auth.fullName')} value={name} maxLength={120} onChange={(e) => setName(e.target.value)} className="sm:col-span-2" error={form.fieldErrors.fullName} />
          <SelectField label={t('extras.relationLabel')} value={relation} onChange={(e) => setRelation(e.target.value as Relation)}>
            {RELATIONS.map((r) => <option key={r} value={r}>{t(`extras.relation.${r}`)}</option>)}
          </SelectField>
          <TextField label={t('extras.dob')} type="date" value={dob} onChange={(e) => setDob(e.target.value)} error={form.fieldErrors.dateOfBirth} />
          <Button type="submit" disabled={!name.trim()} loading={form.submitting} className="sm:col-span-4 sm:w-fit">{t('extras.addFamily')}</Button>
        </form>
        {form.formError && <Alert tone="error" className="mt-3">{form.formError}</Alert>}
      </Card>
    </div>
  );
}

export function FavouritesPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['favourites'], queryFn: extrasApi.favourites });
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('extras.favourites')}</h1>
      {isLoading ? <LoadingState /> : data?.length === 0 ? (
        <EmptyState title={t('extras.noFavourites')}><Link to="/search" className="text-brand-700 hover:underline">{t('nav.findServices')}</Link></EmptyState>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {data?.map((f) => (
            <li key={f.organizationId}>
              <Card className="flex items-center gap-3 p-4">
                <div className="min-w-0 flex-1">
                  <Link to={`/b/${f.slug}`} className="font-semibold hover:text-brand-700">{f.name}</Link>
                  <p className="text-sm text-fg-muted">{f.city}{f.reviewCount > 0 && <> · <Star className="inline h-3.5 w-3.5 fill-amber-400 text-amber-400" aria-hidden="true" /> {Number(f.ratingAvg).toFixed(1)}</>}</p>
                </div>
                <Button variant="ghost" size="sm" aria-label={t('extras.removeFavourite')}
                  onClick={() => void extrasApi.removeFavourite(f.organizationId).then(() => qc.invalidateQueries({ queryKey: ['favourites'] }))}>
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </Button>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function SupportPage() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const form = useApiForm();
  const mine = useQuery({ queryKey: ['support'], queryFn: extrasApi.mySupport });
  const bookings = useQuery({ queryKey: ['my-bookings'], queryFn: bookingApi.mine });
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [appointmentId, setAppointmentId] = useState('');
  const [sent, setSent] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const ok = await form.submit(() => extrasApi.openSupport({ subject: subject.trim(), message: message.trim(),
      appointmentId: appointmentId ? Number(appointmentId) : undefined }));
    if (ok) { setSubject(''); setMessage(''); setAppointmentId(''); setSent(true); void qc.invalidateQueries({ queryKey: ['support'] }); }
  };
  const locale = intlLocale(i18n.language);
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('extras.support')}</h1>
      <Card className="p-5">
        <form onSubmit={submit} className="space-y-3" noValidate>
          {sent && <Alert tone="success">{t('extras.supportSent')}</Alert>}
          {form.formError && <Alert tone="error">{form.formError}</Alert>}
          <TextField label={t('extras.subject')} value={subject} maxLength={150} onChange={(e) => setSubject(e.target.value)} />
          <SelectField label={t('extras.aboutBooking')} value={appointmentId} onChange={(e) => setAppointmentId(e.target.value)}>
            <option value="">{t('extras.noBooking')}</option>
            {bookings.data?.map((b) => <option key={b.id} value={b.id}>{b.reference} · {b.serviceName} · {b.organizationName}</option>)}
          </SelectField>
          <TextAreaField label={t('extras.message')} rows={4} maxLength={4000} value={message} onChange={(e) => setMessage(e.target.value)} />
          <Button type="submit" disabled={!subject.trim() || !message.trim()} loading={form.submitting}>{t('extras.send')}</Button>
        </form>
      </Card>
      <ul className="space-y-3">
        {mine.data?.map((r) => (
          <li key={r.id}>
            <Card className="space-y-1 p-4 text-sm">
              <p className="font-semibold">{r.subject} <span className="ml-2 text-xs font-normal text-fg-muted">{t(`extras.status.${r.status}`)}</span></p>
              <p className="text-xs text-fg-muted">{formatDateTime(r.createdAt, locale)}{r.reference ? ` · ${r.reference}` : ''}</p>
              <p className="whitespace-pre-line">{r.message}</p>
              {r.adminReply && <p className="rounded-lg bg-surface-muted p-2"><span className="font-medium">{t('extras.supportReply')}:</span> {r.adminReply}</p>}
            </Card>
          </li>
        ))}
      </ul>
    </div>
  );
}
