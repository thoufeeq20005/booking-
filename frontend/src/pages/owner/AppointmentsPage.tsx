import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { BusinessDayList } from '@/features/booking/BusinessDayList';
import { EarningsCard } from '@/features/booking/EarningsCard';
import { useApiForm } from '@/hooks/useApiForm';
import { bookingApi, isoDateIST } from '@/services/bookingApi';
import { orgKeys, ownerOrganizationApi } from '@/services/organizationApi';

export function AppointmentsPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const orgs = useQuery({ queryKey: orgKeys.mine, queryFn: ownerOrganizationApi.list });
  const approved = (orgs.data ?? []).filter((o) => o.status === 'APPROVED' || o.status === 'SUSPENDED');
  const [orgId, setOrgId] = useState<number>();
  const current = orgId ?? approved[0]?.id;
  const [date, setDate] = useState(isoDateIST(new Date()));
  const key = ['owner-day', current, date];
  const day = useQuery({ queryKey: key, queryFn: () => bookingApi.ownerDay(current!, date), enabled: Boolean(current) });
  const scan = useApiForm();
  const [code, setCode] = useState('');
  const [checked, setChecked] = useState<string>();

  if (orgs.isLoading) return <LoadingState />;
  if (approved.length === 0) return <div className="mx-auto max-w-4xl px-4 py-10"><EmptyState title={t('booking.noApprovedBusiness')} /></div>;

  const checkIn = async (e: FormEvent) => {
    e.preventDefault();
    const b = await scan.submit(() => bookingApi.checkIn(current!, code.trim()));
    if (b) {
      setChecked(t('booking.checkedInName', { name: b.customerName, service: b.serviceName }));
      setCode('');
      void qc.invalidateQueries({ queryKey: ['owner-day'] });
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('booking.appointments')}</h1>
      <div className="flex flex-wrap items-end gap-3">
        {approved.length > 1 && (
          <SelectField label={t('admin.business')} value={current} onChange={(e) => setOrgId(Number(e.target.value))} className="w-56">
            {approved.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
          </SelectField>
        )}
        <TextField label={t('business.holidayDate')} type="date" value={date} onChange={(e) => setDate(e.target.value)} className="w-44" />
      </div>
      {current && <EarningsCard orgId={current} />}
      <Card className="p-5">
        <form onSubmit={checkIn} className="flex flex-wrap items-end gap-3">
          <TextField label={t('booking.scanCode')} value={code} onChange={(e) => setCode(e.target.value)} hint={t('booking.scanHint')} className="min-w-[18rem] flex-1" />
          <Button type="submit" variant="secondary" disabled={!code.trim()} loading={scan.submitting}>{t('booking.action.check-in')}</Button>
        </form>
        {scan.formError && <Alert tone="error" className="mt-3">{scan.formError}</Alert>}
        {checked && <Alert tone="success" className="mt-3">{checked}</Alert>}
      </Card>
      {day.isLoading ? <LoadingState /> : (
        <BusinessDayList bookings={day.data ?? []} act={(b, action, reason) =>
          bookingApi.ownerAct(current!, b.id, action, reason).then(() => qc.invalidateQueries({ queryKey: key }))} />
      )}
    </div>
  );
}
