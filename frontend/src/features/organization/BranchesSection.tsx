import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarOff, Clock, LocateFixed, MapPin, Pencil, Plus, Trash2 } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CheckboxField } from '@/components/ui/FormFields';
import { EmptyState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import {
  orgKeys,
  ownerOrganizationApi as api,
  type Branch,
  type BranchInput,
  type DayHours,
  type OrganizationDetail,
} from '@/services/organizationApi';
import { formatDate, formatWallTime } from '@/utils/format';
import { DAY_KEYS, hhmm, ISO_DAYS } from './shared';

const emptyBranch: BranchInput = {
  name: '',
  addressLine1: '',
  addressLine2: '',
  locality: '',
  city: '',
  state: '',
  pincode: '',
  latitude: NaN,
  longitude: NaN,
  phone: '',
  active: true,
};

function BranchForm({ orgId, branch, onDone }: { orgId: number; branch?: Branch; onDone: () => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [v, setV] = useState<Record<string, string>>(() => {
    const b = branch ?? emptyBranch;
    return {
      name: b.name,
      addressLine1: b.addressLine1,
      addressLine2: b.addressLine2 ?? '',
      locality: b.locality ?? '',
      city: b.city,
      state: b.state,
      pincode: b.pincode,
      latitude: Number.isFinite(b.latitude) ? String(b.latitude) : '',
      longitude: Number.isFinite(b.longitude) ? String(b.longitude) : '',
      phone: b.phone ?? '',
    };
  });
  const [active, setActive] = useState(branch?.active ?? true);
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState(false);
  const set = (k: string) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));

  const locate = () => {
    if (!navigator.geolocation) {
      setLocateError(true);
      return;
    }
    setLocating(true);
    setLocateError(false);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setV((s) => ({ ...s, latitude: pos.coords.latitude.toFixed(6), longitude: pos.coords.longitude.toFixed(6) }));
        setLocating(false);
      },
      () => {
        setLocateError(true);
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 15000 },
    );
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const body: BranchInput = {
      name: v.name!.trim(),
      addressLine1: v.addressLine1!.trim(),
      addressLine2: v.addressLine2!.trim() || undefined,
      locality: v.locality!.trim() || undefined,
      city: v.city!.trim(),
      state: v.state!.trim(),
      pincode: v.pincode!.trim(),
      latitude: Number(v.latitude),
      longitude: Number(v.longitude),
      phone: v.phone!.trim() || undefined,
      active,
    };
    const ok = await form.submit(() =>
      branch ? api.updateBranch(orgId, branch.id, body) : api.createBranch(orgId, body),
    );
    if (ok) onDone();
  };

  const err = (k: string) => form.fieldErrors[k];
  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <TextField label={t('business.branchName')} value={v.name} onChange={set('name')} maxLength={150}
        hint={t('business.branchNameHint')} error={err('name')} />
      <TextField label={t('business.addressLine1')} value={v.addressLine1} onChange={set('addressLine1')}
        maxLength={200} autoComplete="address-line1" error={err('addressLine1')} />
      <TextField label={t('business.addressLine2')} value={v.addressLine2} onChange={set('addressLine2')}
        maxLength={200} autoComplete="address-line2" error={err('addressLine2')} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label={t('business.locality')} value={v.locality} onChange={set('locality')} maxLength={120} error={err('locality')} />
        <TextField label={t('business.city')} value={v.city} onChange={set('city')} maxLength={120}
          autoComplete="address-level2" error={err('city')} />
        <TextField label={t('business.state')} value={v.state} onChange={set('state')} maxLength={120}
          autoComplete="address-level1" error={err('state')} />
        <TextField label={t('business.pincode')} value={v.pincode} onChange={set('pincode')} inputMode="numeric"
          maxLength={6} autoComplete="postal-code" error={err('pincode')} />
      </div>
      <div className="rounded-xl border border-border p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-medium">{t('business.location')}</p>
          <Button variant="secondary" size="sm" onClick={locate} loading={locating}>
            <LocateFixed className="h-4 w-4" aria-hidden="true" />
            {t('business.useMyLocation')}
          </Button>
        </div>
        <p className="mt-1 text-xs text-fg-muted">{t('business.locationHint')}</p>
        {locateError && <p className="mt-2 text-xs text-danger">{t('business.locationFailed')}</p>}
        <div className="mt-3 grid gap-4 sm:grid-cols-2">
          <TextField label={t('business.latitude')} value={v.latitude} onChange={set('latitude')} inputMode="decimal"
            error={err('latitude')} />
          <TextField label={t('business.longitude')} value={v.longitude} onChange={set('longitude')} inputMode="decimal"
            error={err('longitude')} />
        </div>
      </div>
      <TextField label={t('business.branchPhone')} type="tel" value={v.phone} onChange={set('phone')}
        hint={t('business.optional')} error={err('phone')} />
      {branch && <CheckboxField label={t('business.branchActive')} checked={active} onChange={setActive}
        hint={t('business.branchActiveHint')} />}
      <div className="flex gap-2">
        <Button type="submit" loading={form.submitting}>{t('common.save')}</Button>
        <Button variant="ghost" onClick={onDone}>{t('common.cancel')}</Button>
      </div>
    </form>
  );
}

interface DayRow {
  open: boolean;
  opensAt: string;
  closesAt: string;
  breakStart: string;
  breakEnd: string;
}

function HoursEditor({ orgId, branch, onDone }: { orgId: number; branch: Branch; onDone: () => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [rows, setRows] = useState<DayRow[]>(() =>
    ISO_DAYS.map((d) => {
      const h = branch.hours.find((x) => x.dayOfWeek === d);
      return {
        open: Boolean(h),
        opensAt: hhmm(h?.opensAt) || '09:00',
        closesAt: hhmm(h?.closesAt) || '18:00',
        breakStart: hhmm(h?.breakStart),
        breakEnd: hhmm(h?.breakEnd),
      };
    }),
  );
  const update = (i: number, patch: Partial<DayRow>) =>
    setRows((rs) => rs.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const days: DayHours[] = rows.flatMap((r, i) =>
      r.open
        ? [{ dayOfWeek: i + 1, opensAt: r.opensAt, closesAt: r.closesAt,
             breakStart: r.breakStart || undefined, breakEnd: r.breakEnd || undefined }]
        : [],
    );
    const ok = await form.submit(() => api.setHours(orgId, branch.id, days));
    if (ok) onDone();
  };

  // Field errors arrive as days[i].field, where i indexes the submitted (open) days only.
  const openIndex = (i: number) => rows.slice(0, i).filter((r) => r.open).length;
  const rowError = (i: number) =>
    Object.entries(form.fieldErrors).find(([k]) => k.startsWith(`days[${openIndex(i)}]`))?.[1];

  const timeInput = 'h-9 rounded-lg border border-border bg-surface px-2 text-sm disabled:opacity-50';
  return (
    <form onSubmit={submit} className="space-y-3" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <p className="text-xs text-fg-muted">{t('business.hoursHint')}</p>
      <div className="space-y-2">
        {rows.map((r, i) => (
          <div key={DAY_KEYS[i]} className="rounded-xl border border-border p-3">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              <label className="flex w-28 items-center gap-2 text-sm font-medium">
                <input type="checkbox" checked={r.open} onChange={(e) => update(i, { open: e.target.checked })}
                  className="h-4 w-4 rounded text-brand-600" />
                {t(`days.${DAY_KEYS[i]}`)}
              </label>
              {r.open ? (
                <>
                  <span className="flex items-center gap-1.5 text-sm">
                    <input type="time" aria-label={t('business.opensAt')} className={timeInput} value={r.opensAt}
                      onChange={(e) => update(i, { opensAt: e.target.value })} />
                    –
                    <input type="time" aria-label={t('business.closesAt')} className={timeInput} value={r.closesAt}
                      onChange={(e) => update(i, { closesAt: e.target.value })} />
                  </span>
                  <span className="flex items-center gap-1.5 text-xs text-fg-muted">
                    {t('business.break')}
                    <input type="time" aria-label={t('business.breakStart')} className={timeInput} value={r.breakStart}
                      onChange={(e) => update(i, { breakStart: e.target.value })} />
                    –
                    <input type="time" aria-label={t('business.breakEnd')} className={timeInput} value={r.breakEnd}
                      onChange={(e) => update(i, { breakEnd: e.target.value })} />
                  </span>
                </>
              ) : (
                <span className="text-sm text-fg-muted">{t('business.closed')}</span>
              )}
            </div>
            {r.open && rowError(i) && <p className="mt-1 text-xs font-medium text-danger">{rowError(i)}</p>}
          </div>
        ))}
      </div>
      <div className="flex gap-2">
        <Button type="submit" loading={form.submitting}>{t('business.saveHours')}</Button>
        <Button variant="ghost" onClick={onDone}>{t('common.cancel')}</Button>
      </div>
    </form>
  );
}

function HolidaysEditor({ orgId, branch, editable }: { orgId: number; branch: Branch; editable: boolean }) {
  const { t, i18n } = useTranslation();
  const form = useApiForm();
  const [date, setDate] = useState('');
  const [reason, setReason] = useState('');
  const key = ['branch-holidays', orgId, branch.id];
  const qc = useQueryClient();
  const { data = [] } = useQuery({ queryKey: key, queryFn: () => api.holidays(orgId, branch.id) });

  const add = async (e: FormEvent) => {
    e.preventDefault();
    const ok = await form.submit(() => api.addHoliday(orgId, branch.id, { date, reason: reason.trim() || undefined }));
    if (ok) {
      setDate('');
      setReason('');
      void qc.invalidateQueries({ queryKey: key });
    }
  };
  const remove = async (id: number) => {
    await form.submit(() => api.deleteHoliday(orgId, branch.id, id).then(() => true));
    void qc.invalidateQueries({ queryKey: key });
  };

  return (
    <div className="space-y-3">
      {data.length === 0 ? (
        <p className="text-sm text-fg-muted">{t('business.noHolidays')}</p>
      ) : (
        <ul className="divide-y divide-border rounded-xl border border-border">
          {data.map((h) => (
            <li key={h.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
              <span>
                <span className="font-medium">{formatDate(`${h.date}T12:00:00+05:30`, intlLocale(i18n.language))}</span>
                {h.reason && <span className="text-fg-muted"> · {h.reason}</span>}
              </span>
              {editable && (
                <Button variant="ghost" size="sm" aria-label={t('business.removeHoliday')} onClick={() => void remove(h.id)}>
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <form onSubmit={add} className="flex flex-wrap items-end gap-3" noValidate>
          <TextField label={t('business.holidayDate')} type="date" value={date} onChange={(e) => setDate(e.target.value)}
            error={form.fieldErrors.date}
            className="w-44" />
          <TextField label={t('business.holidayReason')} value={reason} maxLength={200}
            onChange={(e) => setReason(e.target.value)} className="min-w-[12rem] flex-1" />
          <Button type="submit" variant="secondary" loading={form.submitting} disabled={!date}>
            {t('business.addHoliday')}
          </Button>
        </form>
      )}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
    </div>
  );
}

type Mode = { kind: 'none' } | { kind: 'add' } | { kind: 'edit' | 'hours' | 'holidays'; branchId: number };

export function BranchesSection({ org }: { org: OrganizationDetail }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [mode, setMode] = useState<Mode>({ kind: 'none' });
  const form = useApiForm();
  const orgId = org.summary.id;
  const editable = org.editable.operations;
  const locale = intlLocale(i18n.language);

  const refresh = () => {
    setMode({ kind: 'none' });
    void qc.invalidateQueries({ queryKey: orgKeys.detail(orgId) });
  };
  const remove = async (branchId: number) => {
    if (!window.confirm(t('business.confirmDeleteBranch'))) return;
    const ok = await form.submit(() => api.deleteBranch(orgId, branchId).then(() => true));
    if (ok) refresh();
  };

  return (
    <div className="space-y-4">
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {org.branches.length === 0 && mode.kind !== 'add' && (
        <EmptyState title={t('business.noBranches')}>{t('business.noBranchesHint')}</EmptyState>
      )}
      {org.branches.map((b) => {
        const active = mode.kind !== 'none' && mode.kind !== 'add' && mode.branchId === b.id ? mode.kind : null;
        return (
          <Card key={b.id} className="p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="flex items-center gap-2 font-semibold">
                  {b.name}
                  {!b.active && <span className="rounded-full bg-surface-muted px-2 text-xs text-fg-muted">{t('business.inactive')}</span>}
                </h3>
                <p className="mt-1 flex items-start gap-1.5 text-sm text-fg-muted">
                  <MapPin className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  {[b.addressLine1, b.addressLine2, b.locality, b.city, b.state, b.pincode].filter(Boolean).join(', ')}
                </p>
                <p className="mt-1 flex items-start gap-1.5 text-sm text-fg-muted">
                  <Clock className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  {b.hours.length === 0
                    ? <span className="text-danger">{t('business.hoursMissing')}</span>
                    : b.hours.map((h) => `${t(`days.${DAY_KEYS[h.dayOfWeek - 1]}`)} ${formatWallTime(h.opensAt, locale)}–${formatWallTime(h.closesAt, locale)}`).join(' · ')}
                </p>
              </div>
              {editable && (
                <div className="flex flex-wrap gap-1">
                  <Button variant="ghost" size="sm" onClick={() => setMode({ kind: 'edit', branchId: b.id })}>
                    <Pencil className="h-4 w-4" aria-hidden="true" />{t('business.edit')}
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setMode({ kind: 'hours', branchId: b.id })}>
                    <Clock className="h-4 w-4" aria-hidden="true" />{t('business.hours')}
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setMode({ kind: 'holidays', branchId: b.id })}>
                    <CalendarOff className="h-4 w-4" aria-hidden="true" />{t('business.holidays')}
                  </Button>
                  {org.editable.identity && (
                    <Button variant="ghost" size="sm" aria-label={t('business.deleteBranch')} onClick={() => void remove(b.id)}>
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </Button>
                  )}
                </div>
              )}
            </div>
            {active && (
              <div className="mt-5 border-t border-border pt-5">
                {active === 'edit' && <BranchForm orgId={orgId} branch={b} onDone={refresh} />}
                {active === 'hours' && <HoursEditor orgId={orgId} branch={b} onDone={refresh} />}
                {active === 'holidays' && <HolidaysEditor orgId={orgId} branch={b} editable={editable} />}
              </div>
            )}
          </Card>
        );
      })}
      {mode.kind === 'add' ? (
        <Card className="p-5">
          <h3 className="mb-4 font-semibold">{t('business.addBranch')}</h3>
          <BranchForm orgId={orgId} onDone={refresh} />
        </Card>
      ) : (
        editable && (
          <Button variant="secondary" onClick={() => setMode({ kind: 'add' })}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            {t('business.addBranch')}
          </Button>
        )
      )}
    </div>
  );
}
