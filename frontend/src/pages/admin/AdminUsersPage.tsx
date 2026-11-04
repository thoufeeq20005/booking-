import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Search, ShieldOff } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField, TextAreaField } from '@/components/ui/FormFields';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import type { Role, UserView } from '@/services/apiClient';
import { adminUserApi, type UserStatus } from '@/services/dashboardApi';
import { formatDate } from '@/utils/format';

const ROLES: Role[] = ['CUSTOMER', 'BUSINESS_OWNER', 'STAFF', 'SUPER_ADMIN'];
type Action = { kind: 'suspend' | 'reactivate' | 'reset2fa'; user: UserView };

function ActionPanel({ action, onDone }: { action: Action; onDone: () => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [reason, setReason] = useState('');
  const needsReason = action.kind !== 'reactivate';
  const run = async () => {
    const u = action.user;
    const res = await form.submit(() =>
      action.kind === 'reset2fa'
        ? adminUserApi.resetTwoFactor(u.id, reason.trim())
        : adminUserApi.setStatus(u.id, action.kind === 'suspend' ? 'SUSPENDED' : 'ACTIVE', reason.trim() || undefined),
    );
    if (res) onDone();
  };
  return (
    <div className="space-y-3 rounded-xl border border-border bg-surface-muted/40 p-4">
      <p className="text-sm font-medium">{t(`dashboard.userAction.${action.kind}`, { name: action.user.fullName })}</p>
      {action.kind === 'reset2fa' && <Alert>{t('dashboard.reset2faWarning')}</Alert>}
      <TextAreaField rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)}
        label={action.kind === 'reset2fa' ? t('dashboard.reset2faReason') : needsReason ? t('admin.reasonRequired') : t('admin.reasonOptional')}
        error={form.fieldErrors.reason} />
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <div className="flex gap-2">
        <Button variant={action.kind === 'reactivate' ? 'primary' : 'danger'} size="sm" loading={form.submitting}
          disabled={needsReason && reason.trim().length < 5} onClick={() => void run()}>
          {t(`dashboard.userConfirm.${action.kind}`)}
        </Button>
        <Button variant="ghost" size="sm" onClick={onDone}>{t('common.cancel')}</Button>
      </div>
    </div>
  );
}

export function AdminUsersPage() {
  const { t, i18n } = useTranslation();
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const [action, setAction] = useState<Action | null>(null);
  const search = {
    q: params.get('q') || undefined,
    status: (params.get('status') || undefined) as UserStatus | undefined,
    role: (params.get('role') || undefined) as Role | undefined,
    page: Number(params.get('page') ?? 0),
    size: 20,
  };
  const query = useQuery({
    queryKey: ['admin-users', search],
    queryFn: () => adminUserApi.search(search),
    placeholderData: keepPreviousData,
  });
  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('page' in patch)) next.delete('page');
    setParams(next);
  };
  const done = () => {
    setAction(null);
    void qc.invalidateQueries({ queryKey: ['admin-users'] });
    void qc.invalidateQueries({ queryKey: ['admin-dashboard'] });
  };
  const locale = intlLocale(i18n.language);

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.users')}</h1>
      <div className="flex flex-wrap items-end gap-3">
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); update({ q }); }}>
          <input aria-label={t('dashboard.searchUsers')} placeholder={t('dashboard.searchUsers')} value={q}
            onChange={(e) => setQ(e.target.value)} className="h-11 w-64 rounded-xl border border-border bg-surface px-3 text-sm" />
          <Button type="submit" variant="secondary" aria-label={t('admin.search')}><Search className="h-4 w-4" aria-hidden="true" /></Button>
        </form>
        <SelectField label={t('admin.status')} value={search.status ?? ''} onChange={(e) => update({ status: e.target.value })} className="w-40">
          <option value="">{t('admin.all')}</option>
          <option value="ACTIVE">{t('dashboard.userStatus.ACTIVE')}</option>
          <option value="SUSPENDED">{t('dashboard.userStatus.SUSPENDED')}</option>
        </SelectField>
        <SelectField label={t('dashboard.roleFilter')} value={search.role ?? ''} onChange={(e) => update({ role: e.target.value })} className="w-48">
          <option value="">{t('admin.all')}</option>
          {ROLES.map((r) => <option key={r} value={r}>{t(`dashboard.role.${r}`)}</option>)}
        </SelectField>
      </div>

      {query.isLoading && <LoadingState />}
      {query.error && <ErrorState error={query.error} onRetry={() => void query.refetch()} />}
      {query.data?.items.length === 0 && <EmptyState title={t('dashboard.noUsers')} />}
      {query.data && query.data.items.length > 0 && (
        <Card className="divide-y divide-border">
          {query.data.items.map((u) => {
            const self = u.id === me?.id;
            return (
              <div key={u.id} className="space-y-3 p-4">
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">
                      {u.fullName}
                      {u.status === 'SUSPENDED' && (
                        <span className="ml-2 rounded-full bg-danger/15 px-2 py-0.5 text-xs font-semibold text-danger">
                          {t('dashboard.userStatus.SUSPENDED')}
                        </span>
                      )}
                    </p>
                    <p className="truncate text-sm text-fg-muted">{[u.email, u.phone].filter(Boolean).join(' · ')}</p>
                    <p className="text-xs text-fg-muted">
                      {u.roles.map((r) => t(`dashboard.role.${r}`)).join(', ')}
                      {' · '}{t('dashboard.joined', { date: formatDate(u.createdAt, locale) })}
                      {u.twoFactorEnabled && ` · ${t('dashboard.twoFactorOn')}`}
                    </p>
                  </div>
                  {!self && (
                    <div className="flex flex-wrap gap-2">
                      {u.twoFactorEnabled && (
                        <Button variant="secondary" size="sm" onClick={() => setAction({ kind: 'reset2fa', user: u })}>
                          <ShieldOff className="h-4 w-4" aria-hidden="true" />{t('dashboard.reset2fa')}
                        </Button>
                      )}
                      {u.status === 'SUSPENDED' ? (
                        <Button variant="secondary" size="sm" onClick={() => setAction({ kind: 'reactivate', user: u })}>
                          {t('dashboard.reactivate')}
                        </Button>
                      ) : (
                        <Button variant="secondary" size="sm" onClick={() => setAction({ kind: 'suspend', user: u })}>
                          {t('dashboard.suspend')}
                        </Button>
                      )}
                    </div>
                  )}
                </div>
                {action?.user.id === u.id && <ActionPanel key={action.kind} action={action} onDone={done} />}
              </div>
            );
          })}
        </Card>
      )}
      {query.data && query.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <Button variant="secondary" size="sm" disabled={search.page === 0} onClick={() => update({ page: String(search.page - 1) })}>
            {t('admin.previous')}
          </Button>
          <span>{t('admin.pageOf', { page: search.page + 1, total: query.data.totalPages })}</span>
          <Button variant="secondary" size="sm" disabled={search.page + 1 >= query.data.totalPages}
            onClick={() => update({ page: String(search.page + 1) })}>
            {t('admin.next')}
          </Button>
        </div>
      )}
    </div>
  );
}
