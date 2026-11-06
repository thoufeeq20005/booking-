import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CheckboxField, SelectField, TextAreaField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import { PhotoField } from './PhotoField';
import type { OrganizationDetail } from '@/services/organizationApi';
import { teamApi, type ServiceInput, type ServiceView } from '@/services/teamApi';
import { formatINR } from '@/utils/format';

const num = (v: string, fallback: number) => (v.trim() === '' || Number.isNaN(Number(v)) ? fallback : Number(v));

function ServiceForm({ org, service, onDone }: { org: OrganizationDetail; service?: ServiceView; onDone: (saved: boolean) => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [v, setV] = useState({
    name: service?.name ?? '',
    description: service?.description ?? '',
    durationMinutes: String(service?.durationMinutes ?? 30),
    price: String(service?.price ?? ''),
    bufferMinutes: String(service?.bufferMinutes ?? 0),
    slotIntervalMinutes: String(service?.slotIntervalMinutes ?? 15),
    maxAdvanceDays: String(service?.maxAdvanceDays ?? 60),
    minNoticeMinutes: String(service?.minNoticeMinutes ?? 60),
  });
  const [type, setType] = useState(service?.consultationType ?? 'IN_PERSON');
  const [requiresStaff, setRequiresStaff] = useState(service?.requiresStaff ?? true);
  const [active, setActive] = useState(service?.active ?? true);
  const [offers, setOffers] = useState<Record<number, string | null>>(() =>
    Object.fromEntries((service?.branches ?? org.branches.map((b) => ({ branchId: b.id, priceOverride: null })))
      .map((o) => [o.branchId, o.priceOverride == null ? '' : String(o.priceOverride)])),
  );
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const body: ServiceInput = {
      name: v.name.trim(),
      description: v.description.trim() || undefined,
      durationMinutes: num(v.durationMinutes, 30),
      price: v.price.trim(),
      consultationType: type,
      requiresStaff,
      bufferMinutes: num(v.bufferMinutes, 0),
      slotIntervalMinutes: num(v.slotIntervalMinutes, 15),
      maxAdvanceDays: num(v.maxAdvanceDays, 60),
      minNoticeMinutes: num(v.minNoticeMinutes, 60),
      active,
      branches: Object.entries(offers).map(([id, p]) => ({ branchId: Number(id), priceOverride: p?.trim() ? p.trim() : null })),
    };
    const ok = await form.submit(() =>
      service ? teamApi.updateService(org.summary.id, service.id, body) : teamApi.createService(org.summary.id, body));
    if (ok) onDone(true);
  };
  const err = (k: string) => form.fieldErrors[k];

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <TextField label={t('team.serviceName')} value={v.name} onChange={set('name')} maxLength={150} error={err('name')} />
      <TextAreaField label={t('business.description')} rows={2} value={v.description} onChange={set('description')} maxLength={1000} />
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label={t('team.duration')} type="number" min={5} max={720} value={v.durationMinutes} onChange={set('durationMinutes')} error={err('durationMinutes')} />
        <TextField label={t('team.price')} inputMode="decimal" value={v.price} onChange={set('price')} error={err('price')} />
        <SelectField label={t('team.type')} value={type} onChange={(e) => setType(e.target.value as typeof type)}>
          <option value="IN_PERSON">{t('team.typeName.IN_PERSON')}</option>
          <option value="ONLINE">{t('team.typeName.ONLINE')}</option>
        </SelectField>
        <TextField label={t('team.buffer')} type="number" min={0} max={240} value={v.bufferMinutes} onChange={set('bufferMinutes')} hint={t('team.bufferHint')} error={err('bufferMinutes')} />
        <TextField label={t('team.interval')} type="number" min={5} max={240} value={v.slotIntervalMinutes} onChange={set('slotIntervalMinutes')} hint={t('team.intervalHint')} error={err('slotIntervalMinutes')} />
        <TextField label={t('team.notice')} type="number" min={0} value={v.minNoticeMinutes} onChange={set('minNoticeMinutes')} error={err('minNoticeMinutes')} />
        <TextField label={t('team.advance')} type="number" min={1} max={365} value={v.maxAdvanceDays} onChange={set('maxAdvanceDays')} error={err('maxAdvanceDays')} />
      </div>
      <fieldset>
        <legend className="text-sm font-medium">{t('team.offeredAt')}</legend>
        {err('branches') && <p className="text-xs text-danger">{err('branches')}</p>}
        <div className="mt-2 space-y-2">
          {org.branches.map((b) => (
            <div key={b.id} className="flex flex-wrap items-center gap-3">
              <CheckboxField label={b.name} checked={b.id in offers}
                onChange={(on) => setOffers((o) => {
                  const n = { ...o };
                  if (on) n[b.id] = '';
                  else delete n[b.id];
                  return n;
                })} />
              {b.id in offers && (
                <input aria-label={t('team.branchPrice', { branch: b.name })} placeholder={t('team.branchPricePlaceholder')}
                  inputMode="decimal" value={offers[b.id] ?? ''}
                  onChange={(e) => setOffers((o) => ({ ...o, [b.id]: e.target.value }))}
                  className="h-9 w-40 rounded-lg border border-border bg-surface px-2 text-sm" />
              )}
            </div>
          ))}
        </div>
      </fieldset>
      <div className="flex flex-wrap gap-6">
        <CheckboxField label={t('team.requiresStaff')} checked={requiresStaff} onChange={setRequiresStaff} hint={t('team.requiresStaffHint')} />
        <CheckboxField label={t('admin.active')} checked={active} onChange={setActive} />
      </div>
      <div className="flex gap-2">
        <Button type="submit" loading={form.submitting} disabled={!v.name.trim() || !v.price.trim()}>{t('common.save')}</Button>
        <Button variant="ghost" onClick={() => onDone(false)}>{t('common.cancel')}</Button>
      </div>
    </form>
  );
}

function SlotPreview({ org, services }: { org: OrganizationDetail; services: ServiceView[] }) {
  const { t } = useTranslation();
  const [serviceId, setServiceId] = useState(services[0]?.id ?? 0);
  const service = services.find((s) => s.id === serviceId);
  const [branchId, setBranchId] = useState(service?.branches[0]?.branchId ?? 0);
  const [date, setDate] = useState(() => new Date(Date.now() + 86400000).toISOString().slice(0, 10));
  const enabled = Boolean(serviceId && branchId && date);
  const q = useQuery({
    queryKey: ['slot-preview', org.summary.id, serviceId, branchId, date],
    queryFn: () => teamApi.previewSlots(org.summary.id, { serviceId, branchId, date }),
    enabled,
    retry: false,
  });
  const branchesForService = org.branches.filter((b) => service?.branches.some((o) => o.branchId === b.id));

  return (
    <Card className="space-y-4 p-6">
      <div>
        <h3 className="font-semibold">{t('team.previewTitle')}</h3>
        <p className="text-sm text-fg-muted">{t('team.previewHint')}</p>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        <SelectField label={t('team.service')} value={serviceId} onChange={(e) => {
          const id = Number(e.target.value);
          setServiceId(id);
          setBranchId(services.find((s) => s.id === id)?.branches[0]?.branchId ?? 0);
        }}>
          {services.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </SelectField>
        <SelectField label={t('team.branch')} value={branchId} onChange={(e) => setBranchId(Number(e.target.value))}>
          {branchesForService.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </SelectField>
        <TextField label={t('business.holidayDate')} type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>
      {q.isFetching && <LoadingState />}
      {q.data && (q.data.slots.length > 0 ? (
        <div>
          <p className="mb-2 text-sm text-fg-muted">{t('team.slotsFound', { count: q.data.slots.length, price: formatINR(q.data.price) })}</p>
          <ul className="flex flex-wrap gap-2">
            {q.data.slots.map((s) => (
              <li key={s.time} className="rounded-lg border border-border px-2.5 py-1 text-sm tabular-nums">{s.time}</li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="text-sm text-fg-muted">{t(`team.outcome.${q.data.outcome === 'OPEN' ? 'FULL' : q.data.outcome}`)}</p>
      ))}
      {q.error && <Alert tone="error">{t('errors.NOT_FOUND')}</Alert>}
    </Card>
  );
}

export function ServicesSection({ org }: { org: OrganizationDetail }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const key = ['org-services', org.summary.id];
  const { data = [], isLoading } = useQuery({ queryKey: key, queryFn: () => teamApi.services(org.summary.id) });
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const form = useApiForm();
  const editable = org.editable.operations;
  const done = (saved: boolean) => {
    setEditing(null);
    if (saved) void qc.invalidateQueries({ queryKey: key });
  };
  const remove = async (id: number) => {
    if (!window.confirm(t('admin.confirmDelete'))) return;
    if (await form.submit(() => teamApi.deleteService(org.summary.id, id).then(() => true))) done(true);
  };
  const branchName = (id: number) => org.branches.find((b) => b.id === id)?.name ?? `#${id}`;

  if (isLoading) return <LoadingState />;
  if (org.branches.length === 0) return <EmptyState title={t('team.needBranch')} />;

  return (
    <div className="space-y-4">
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {data.length === 0 && editing !== 'new' && <EmptyState title={t('team.noServices')}>{t('team.noServicesHint')}</EmptyState>}
      {data.map((s) => (
        <Card key={s.id} className="p-5">
          {editing === s.id ? (
            <ServiceForm org={org} service={s} onDone={done} />
          ) : (
            <div className="flex flex-wrap items-start justify-between gap-3">
              <PhotoField
                url={s.imageUrl}
                alt={s.name}
                editable={editable}
                shape="thumb"
                upload={(f) => teamApi.uploadServiceImage(org.summary.id, s.id, f)}
                remove={() => teamApi.removeServiceImage(org.summary.id, s.id)}
                onChange={() => void qc.invalidateQueries({ queryKey: key })}
              />
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{s.name}{!s.active && <span className="ml-2 text-xs text-fg-muted">({t('business.inactive')})</span>}</p>
                <p className="text-sm text-fg-muted">
                  {t('team.summary', { minutes: s.durationMinutes, price: formatINR(s.price) })}
                  {' · '}{s.branches.map((b) => b.priceOverride != null ? `${branchName(b.branchId)} (${formatINR(b.priceOverride)})` : branchName(b.branchId)).join(', ') || t('team.noBranch')}
                </p>
              </div>
              {editable && (
                <div className="flex gap-1">
                  <Button variant="ghost" size="sm" onClick={() => setEditing(s.id)}><Pencil className="h-4 w-4" aria-hidden="true" />{t('business.edit')}</Button>
                  <Button variant="ghost" size="sm" aria-label={t('admin.delete')} onClick={() => void remove(s.id)}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
                </div>
              )}
            </div>
          )}
        </Card>
      ))}
      {editing === 'new' ? (
        <Card className="p-5"><ServiceForm org={org} onDone={done} /></Card>
      ) : editable && (
        <Button variant="secondary" onClick={() => setEditing('new')}><Plus className="h-4 w-4" aria-hidden="true" />{t('team.addService')}</Button>
      )}
      {data.some((s) => s.branches.length > 0) && <SlotPreview org={org} services={data.filter((s) => s.branches.length > 0)} />}
    </div>
  );
}
