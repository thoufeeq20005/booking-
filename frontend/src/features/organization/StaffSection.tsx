import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Mail, Pencil, Plus, Trash2, UserPlus } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CheckboxField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import { accountApi } from '@/services/authApi';
import type { OrganizationDetail } from '@/services/organizationApi';
import { PERMISSIONS, teamApi, type Permission, type StaffInput, type StaffView, type Window } from '@/services/teamApi';
import { formatDateTime } from '@/utils/format';
import { DAY_KEYS, hhmm } from './shared';

function ProfileForm({ org, staff, mode, onDone }: {
  org: OrganizationDetail; staff?: StaffView; mode: 'invite' | 'self' | 'edit'; onDone: (saved: boolean) => void;
}) {
  const { t } = useTranslation();
  const { user, setUser } = useAuth();
  const form = useApiForm();
  const services = useQuery({ queryKey: ['org-services', org.summary.id], queryFn: () => teamApi.services(org.summary.id) });
  const [email, setEmail] = useState('');
  const [name, setName] = useState(staff?.displayName ?? (mode === 'self' ? user?.fullName ?? '' : ''));
  const [title, setTitle] = useState(staff?.title ?? '');
  const [spec, setSpec] = useState(staff?.specialisation ?? '');
  const [perms, setPerms] = useState<Permission[]>(staff?.permissions ?? []);
  const [branchIds, setBranchIds] = useState<number[]>(staff?.branchIds ?? org.branches.map((b) => b.id));
  const [serviceIds, setServiceIds] = useState<number[] | null>(staff?.serviceIds ?? null);
  const chosenServices = serviceIds ?? (services.data ?? []).map((s) => s.id);
  const toggle = <T,>(list: T[], v: T, on: boolean) => (on ? [...list, v] : list.filter((x) => x !== v));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const profile: StaffInput = { displayName: name.trim(), title: title.trim() || undefined,
      specialisation: spec.trim() || undefined, permissions: perms, branchIds, serviceIds: chosenServices };
    const id = org.summary.id;
    const ok = await form.submit(() =>
      mode === 'invite' ? teamApi.invite(id, email.trim(), profile)
        : mode === 'self' ? teamApi.addSelf(id, profile)
          : teamApi.updateStaff(id, staff!.id, profile));
    if (ok) {
      if (mode === 'self') setUser(await accountApi.me());
      onDone(true);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {mode === 'invite' && (
        <TextField label={t('auth.email')} type="email" value={email} onChange={(e) => setEmail(e.target.value)}
          hint={t('team.inviteHint')} error={form.fieldErrors.email} />
      )}
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label={t('team.displayName')} value={name} maxLength={120} onChange={(e) => setName(e.target.value)}
          error={form.fieldErrors['profile.displayName'] ?? form.fieldErrors.displayName} />
        <TextField label={t('team.title')} value={title} maxLength={40} placeholder="Dr." onChange={(e) => setTitle(e.target.value)} />
        <TextField label={t('team.specialisation')} value={spec} maxLength={150} onChange={(e) => setSpec(e.target.value)} />
      </div>
      <fieldset>
        <legend className="text-sm font-medium">{t('team.branches')}</legend>
        <div className="mt-2 flex flex-wrap gap-4">
          {org.branches.map((b) => (
            <CheckboxField key={b.id} label={b.name} checked={branchIds.includes(b.id)} onChange={(on) => setBranchIds((l) => toggle(l, b.id, on))} />
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend className="text-sm font-medium">{t('team.services')}</legend>
        <div className="mt-2 flex flex-wrap gap-4">
          {(services.data ?? []).map((s) => (
            <CheckboxField key={s.id} label={s.name} checked={chosenServices.includes(s.id)}
              onChange={(on) => setServiceIds(toggle(chosenServices, s.id, on))} />
          ))}
          {services.data?.length === 0 && <p className="text-sm text-fg-muted">{t('team.noServices')}</p>}
        </div>
      </fieldset>
      {mode !== 'self' && (
        <fieldset>
          <legend className="text-sm font-medium">{t('team.permissions')}</legend>
          <div className="mt-2 flex flex-wrap gap-4">
            {PERMISSIONS.map((p) => (
              <CheckboxField key={p} label={t(`team.permission.${p}`)} checked={perms.includes(p)} onChange={(on) => setPerms((l) => toggle(l, p, on))} />
            ))}
          </div>
        </fieldset>
      )}
      <div className="flex gap-2">
        <Button type="submit" loading={form.submitting} disabled={!name.trim() || (mode === 'invite' && !email.trim())}>
          {mode === 'invite' ? t('team.sendInvite') : t('common.save')}
        </Button>
        <Button variant="ghost" onClick={() => onDone(false)}>{t('common.cancel')}</Button>
      </div>
    </form>
  );
}

function ScheduleEditor({ org, staff, onDone }: { org: OrganizationDetail; staff: StaffView; onDone: (saved: boolean) => void }) {
  const { t, i18n } = useTranslation();
  const form = useApiForm();
  const leaveForm = useApiForm();
  const qc = useQueryClient();
  const branches = org.branches.filter((b) => staff.branchIds.includes(b.id));
  const [rows, setRows] = useState<Window[]>(staff.availability.map((w) => ({ ...w, startTime: hhmm(w.startTime), endTime: hhmm(w.endTime) })));
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [reason, setReason] = useState('');
  const refresh = () => qc.invalidateQueries({ queryKey: ['org-staff', org.summary.id] });
  const update = (i: number, patch: Partial<Window>) => setRows((r) => r.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  const save = async () => {
    if (await form.submit(() => teamApi.setAvailability(org.summary.id, staff.id, rows))) onDone(true);
  };
  const addLeave = async (e: FormEvent) => {
    e.preventDefault();
    const ok = await leaveForm.submit(() => teamApi.addLeave(org.summary.id, staff.id, {
      startsAt: new Date(from).toISOString(), endsAt: new Date(to).toISOString(), reason: reason.trim() || undefined }));
    if (ok) {
      setFrom('');
      setTo('');
      setReason('');
      void refresh();
    }
  };
  const removeLeave = async (id: number) => {
    if (await leaveForm.submit(() => teamApi.deleteLeave(org.summary.id, staff.id, id))) void refresh();
  };
  const cell = 'h-9 rounded-lg border border-border bg-surface px-2 text-sm';
  const firstError = Object.values(form.fieldErrors)[0];

  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <h4 className="font-medium">{t('team.weeklyAvailability')}</h4>
        <p className="text-xs text-fg-muted">{t('team.availabilityHint')}</p>
        {rows.length === 0 && <p className="text-sm text-fg-muted">{t('team.noAvailability')}</p>}
        {rows.map((r, i) => (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <select aria-label={t('team.day')} className={cell} value={r.dayOfWeek} onChange={(e) => update(i, { dayOfWeek: Number(e.target.value) })}>
              {DAY_KEYS.map((d, k) => <option key={d} value={k + 1}>{t(`days.${d}`)}</option>)}
            </select>
            <select aria-label={t('team.branch')} className={cell} value={r.branchId} onChange={(e) => update(i, { branchId: Number(e.target.value) })}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
            <input type="time" aria-label={t('business.opensAt')} className={cell} value={r.startTime} onChange={(e) => update(i, { startTime: e.target.value })} />
            –
            <input type="time" aria-label={t('business.closesAt')} className={cell} value={r.endTime} onChange={(e) => update(i, { endTime: e.target.value })} />
            <Button variant="ghost" size="sm" aria-label={t('admin.delete')} onClick={() => setRows((x) => x.filter((_, j) => j !== i))}>
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        ))}
        {firstError && <p className="text-xs font-medium text-danger">{firstError}</p>}
        {form.formError && <Alert tone="error">{form.formError}</Alert>}
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" size="sm" disabled={branches.length === 0}
            onClick={() => setRows((x) => [...x, { branchId: branches[0]!.id, dayOfWeek: 1, startTime: '09:00', endTime: '13:00' }])}>
            <Plus className="h-4 w-4" aria-hidden="true" />{t('team.addWindow')}
          </Button>
          <Button size="sm" loading={form.submitting} onClick={() => void save()}>{t('team.saveAvailability')}</Button>
          <Button variant="ghost" size="sm" onClick={() => onDone(false)}>{t('common.cancel')}</Button>
        </div>
      </section>
      <section className="space-y-3">
        <h4 className="font-medium">{t('team.leave')}</h4>
        <ul className="space-y-1 text-sm">
          {staff.upcomingLeave.map((l) => (
            <li key={l.id} className="flex items-center gap-2">
              <span>{formatDateTime(l.startsAt, intlLocale(i18n.language))} – {formatDateTime(l.endsAt, intlLocale(i18n.language))}{l.reason ? ` · ${l.reason}` : ''}</span>
              <Button variant="ghost" size="sm" aria-label={t('admin.delete')} onClick={() => void removeLeave(l.id)}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
            </li>
          ))}
          {staff.upcomingLeave.length === 0 && <li className="text-fg-muted">{t('team.noLeave')}</li>}
        </ul>
        <form onSubmit={addLeave} className="flex flex-wrap items-end gap-3" noValidate>
          <TextField label={t('team.leaveFrom')} type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
          <TextField label={t('team.leaveTo')} type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} error={leaveForm.fieldErrors.endsAt} />
          <TextField label={t('business.holidayReason')} value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
          <Button type="submit" variant="secondary" loading={leaveForm.submitting} disabled={!from || !to}>{t('team.addLeave')}</Button>
        </form>
        {leaveForm.formError && <Alert tone="error">{leaveForm.formError}</Alert>}
      </section>
    </div>
  );
}

type Mode = { kind: 'invite' | 'self' } | { kind: 'edit' | 'schedule'; id: number } | null;

export function StaffSection({ org }: { org: OrganizationDetail }) {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const qc = useQueryClient();
  const form = useApiForm();
  const key = ['org-staff', org.summary.id];
  const { data = [], isLoading } = useQuery({ queryKey: key, queryFn: () => teamApi.staff(org.summary.id) });
  const [mode, setMode] = useState<Mode>(null);
  const editable = org.editable.operations;
  const done = (saved: boolean) => {
    setMode(null);
    if (saved) void qc.invalidateQueries({ queryKey: key });
  };
  const act = async (fn: () => Promise<unknown>) => {
    if (await form.submit(() => fn().then(() => true))) done(true);
  };
  const isSelfListed = data.some((s) => s.userId === user?.id);

  if (isLoading) return <LoadingState />;
  if (org.branches.length === 0) return <EmptyState title={t('team.needBranch')} />;

  return (
    <div className="space-y-4">
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {data.length === 0 && !mode && <EmptyState title={t('team.noStaff')}>{t('team.noStaffHint')}</EmptyState>}
      {data.map((s) => {
        const open = mode && 'id' in mode && mode.id === s.id ? mode.kind : null;
        return (
          <Card key={s.id} className="p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="font-semibold">
                  {[s.title, s.displayName].filter(Boolean).join(' ')}
                  <span className="ml-2 rounded-full bg-surface-muted px-2 py-0.5 text-xs font-medium text-fg-muted">{t(`team.status.${s.status}`)}</span>
                </p>
                <p className="text-sm text-fg-muted">{[s.specialisation, s.email].filter(Boolean).join(' · ')}</p>
                {s.invitation && (
                  <p className="text-xs text-fg-muted">
                    {s.invitation.open
                      ? t('team.invitePending', { date: formatDateTime(s.invitation.expiresAt, intlLocale(i18n.language)) })
                      : t('team.inviteExpired')}
                  </p>
                )}
                {s.status !== 'INVITED' && (
                  <p className="text-xs text-fg-muted">
                    {s.availability.length === 0 ? t('team.noAvailability')
                      : s.availability.map((w) => `${t(`days.${DAY_KEYS[w.dayOfWeek - 1]}`)} ${hhmm(w.startTime)}–${hhmm(w.endTime)}`).join(' · ')}
                  </p>
                )}
              </div>
              {editable && (
                <div className="flex flex-wrap gap-1">
                  <Button variant="ghost" size="sm" onClick={() => setMode({ kind: 'edit', id: s.id })}><Pencil className="h-4 w-4" aria-hidden="true" />{t('business.edit')}</Button>
                  {s.status !== 'INVITED' && (
                    <Button variant="ghost" size="sm" onClick={() => setMode({ kind: 'schedule', id: s.id })}><CalendarClock className="h-4 w-4" aria-hidden="true" />{t('team.schedule')}</Button>
                  )}
                  {s.status === 'INVITED' && (
                    <>
                      <Button variant="ghost" size="sm" onClick={() => void act(() => teamApi.resend(org.summary.id, s.id))}><Mail className="h-4 w-4" aria-hidden="true" />{t('team.resend')}</Button>
                      <Button variant="ghost" size="sm" onClick={() => void act(() => teamApi.revoke(org.summary.id, s.id))}>{t('team.revoke')}</Button>
                    </>
                  )}
                  {s.status === 'ACTIVE' && (
                    <Button variant="ghost" size="sm" onClick={() => void act(() => teamApi.setStatus(org.summary.id, s.id, 'INACTIVE'))}>{t('team.deactivate')}</Button>
                  )}
                  {s.status === 'INACTIVE' && (
                    <Button variant="ghost" size="sm" onClick={() => void act(() => teamApi.setStatus(org.summary.id, s.id, 'ACTIVE'))}>{t('dashboard.reactivate')}</Button>
                  )}
                </div>
              )}
            </div>
            {open === 'edit' && <div className="mt-5 border-t border-border pt-5"><ProfileForm org={org} staff={s} mode="edit" onDone={done} /></div>}
            {open === 'schedule' && <div className="mt-5 border-t border-border pt-5"><ScheduleEditor org={org} staff={s} onDone={done} /></div>}
          </Card>
        );
      })}
      {mode && (mode.kind === 'invite' || mode.kind === 'self') ? (
        <Card className="p-5">
          <h3 className="mb-4 font-semibold">{mode.kind === 'invite' ? t('team.inviteTitle') : t('team.addSelf')}</h3>
          <ProfileForm org={org} mode={mode.kind} onDone={done} />
        </Card>
      ) : editable && (
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => setMode({ kind: 'invite' })}><UserPlus className="h-4 w-4" aria-hidden="true" />{t('team.inviteTitle')}</Button>
          {!isSelfListed && <Button variant="ghost" onClick={() => setMode({ kind: 'self' })}>{t('team.addSelf')}</Button>}
        </div>
      )}
    </div>
  );
}
