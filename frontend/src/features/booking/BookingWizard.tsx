import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { CalendarCheck } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField, TextAreaField } from '@/components/ui/FormFields';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { bookingApi, isoDateIST } from '@/services/bookingApi';
import { useQuery } from '@tanstack/react-query';
import { extrasApi } from '@/services/extrasApi';
import type { PublicProfile } from '@/services/discoveryApi';
import { usePublicConfig } from '@/services/platformApi';
import { formatINR } from '@/utils/format';
import { SlotPicker } from './SlotPicker';

/** Service → branch → (staff) → day → time → confirm. Prices come from the server; this only previews them. */
export function BookingWizard({ profile }: { profile: PublicProfile }) {
  const { t } = useTranslation();
  const { status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const config = usePublicConfig();
  const form = useApiForm();
  const [serviceId, setServiceId] = useState(profile.services[0]?.id ?? 0);
  const service = profile.services.find((s) => s.id === serviceId);
  const branchIds = Object.keys(service?.branchPrices ?? {}).map(Number);
  const [branchId, setBranchId] = useState(branchIds[0] ?? 0);
  const [staffId, setStaffId] = useState<number | undefined>(undefined);
  const [date, setDate] = useState(isoDateIST(new Date()));
  const [startsAt, setStartsAt] = useState<string>();
  const [note, setNote] = useState('');
  const [forWhom, setForWhom] = useState('');
  const family = useQuery({ queryKey: ['family'], queryFn: extrasApi.family, enabled: status === 'authenticated' });

  if (!service || branchIds.length === 0) return null;
  const effectiveBranch = branchIds.includes(branchId) ? branchId : branchIds[0]!;
  const staffOptions = service.requiresStaff
    ? profile.staff.filter((s) => s.serviceIds.includes(service.id) && s.branchIds.includes(effectiveBranch)) : [];
  const price = Number(service.branchPrices[effectiveBranch] ?? service.price);
  const fee = config.data ? Math.round(price * Number(config.data.commissionPercent)) / 100 : null;

  const reset = () => setStartsAt(undefined);
  const book = async () => {
    if (!startsAt) return;
    const b = await form.submit(() => bookingApi.book({ branchId: effectiveBranch, serviceId, staffId, startsAt, note: note.trim() || undefined, familyMemberId: forWhom ? Number(forWhom) : undefined }));
    if (!b) return;
    // Online payment: go to the payment page; otherwise the booking is already confirmed (or awaiting approval).
    if (b.status === 'PENDING_PAYMENT') navigate(`/customer/bookings/${b.id}/pay`);
    else navigate(`/customer/bookings/${b.id}`, { state: { justBooked: true } });
  };

  return (
    <Card className="space-y-4 p-5">
      <h2 className="flex items-center gap-2 text-lg font-semibold"><CalendarCheck className="h-5 w-5 text-brand-600" aria-hidden="true" />{t('booking.title')}</h2>
      <SelectField label={t('team.service')} value={serviceId} onChange={(e) => { setServiceId(Number(e.target.value)); setStaffId(undefined); reset(); }}>
        {profile.services.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
      </SelectField>
      {branchIds.length > 1 && (
        <SelectField label={t('team.branch')} value={effectiveBranch} onChange={(e) => { setBranchId(Number(e.target.value)); reset(); }}>
          {profile.branches.filter((b) => branchIds.includes(b.id)).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </SelectField>
      )}
      {staffOptions.length > 1 && (
        <SelectField label={t('booking.with')} value={staffId ?? ''} onChange={(e) => { setStaffId(e.target.value ? Number(e.target.value) : undefined); reset(); }}>
          <option value="">{t('booking.anyone')}</option>
          {staffOptions.map((s) => <option key={s.id} value={s.id}>{[s.title, s.displayName].filter(Boolean).join(' ')}</option>)}
        </SelectField>
      )}
      <SlotPicker branchId={effectiveBranch} serviceId={serviceId} staffId={staffId} date={date}
        onDate={(d) => { setDate(d); reset(); }} value={startsAt} onPick={setStartsAt} />
      <dl className="space-y-1 rounded-xl bg-surface-muted p-3 text-sm">
        <div className="flex justify-between"><dt>{t('home.exampleService')}</dt><dd>{formatINR(price)}</dd></div>
        {fee != null && <div className="flex justify-between"><dt>{t('home.examplePlatform', { percent: config.data!.commissionPercent })}</dt><dd>{formatINR(fee)}</dd></div>}
        {fee != null && <div className="flex justify-between font-semibold"><dt>{t('home.exampleTotal')}</dt><dd>{formatINR(price + fee)}</dd></div>}
      </dl>
      {status === 'authenticated' ? (
        <>
          {Boolean(family.data?.length) && (
            <SelectField label={t('extras.forWhom')} value={forWhom} onChange={(e) => setForWhom(e.target.value)}>
              <option value="">{t('extras.myself')}</option>
              {family.data?.map((m) => <option key={m.id} value={m.id}>{m.fullName}</option>)}
            </SelectField>
          )}
          <TextAreaField label={t('booking.note')} rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          {form.formError && <Alert tone="error">{form.formError}</Alert>}
          <Button className="w-full" disabled={!startsAt} loading={form.submitting} onClick={() => void book()}>{t('booking.confirm')}</Button>
          <p className="text-xs text-fg-muted">{t(profile.confirmationMode === 'APPROVAL' ? 'biz.mode.APPROVAL' : 'biz.mode.INSTANT')}</p>
        </>
      ) : (
        <Link to={`/signin?next=${encodeURIComponent(location.pathname)}`} className={buttonClasses('primary', 'md', 'w-full')}>{t('booking.signInToBook')}</Link>
      )}
    </Card>
  );
}
