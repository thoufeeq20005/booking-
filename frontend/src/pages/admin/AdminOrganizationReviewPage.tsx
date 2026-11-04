import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Check, Eye, FileText, X } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextAreaField } from '@/components/ui/FormFields';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { HistoryList } from '@/features/organization/HistoryList';
import { DAY_KEYS, fileUrl, formatBytes, openBlob, StatusBadge, useLocalized } from '@/features/organization/shared';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import { adminOrganizationApi as api, orgKeys, type DocumentSlot, type OrganizationDetail } from '@/services/organizationApi';
import { formatWallTime } from '@/utils/format';
import { paymentApi } from '@/services/paymentApi';
import { TextField } from '@/components/ui/TextField';

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{title}</h2>
      <div className="mt-4">{children}</div>
    </Card>
  );
}

function Field({ label, value }: { label: string; value?: ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-fg-muted">{label}</dt>
      <dd className="mt-0.5 break-words">{value || '—'}</dd>
    </div>
  );
}

function DocumentReview({ org, slot, onUpdated }: { org: OrganizationDetail; slot: DocumentSlot; onUpdated: (d: OrganizationDetail) => void }) {
  const { t } = useTranslation();
  const pick = useLocalized();
  const form = useApiForm();
  const [rejecting, setRejecting] = useState(false);
  const [note, setNote] = useState('');
  const doc = slot.document;
  const canReview = org.summary.status === 'PENDING' && doc;

  const review = async (status: 'ACCEPTED' | 'REJECTED') => {
    if (!doc) return;
    const d = await form.submit(() => api.reviewDocument(org.summary.id, doc.id, { status, note: note.trim() || undefined }));
    if (d) {
      setRejecting(false);
      setNote('');
      onUpdated(d);
    }
  };
  const view = async () => {
    if (!doc) return;
    const blob = await form.submit(() => api.documentFile(org.summary.id, doc.id));
    if (blob) openBlob(blob);
  };

  return (
    <li className="space-y-2 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="font-medium">
          {pick(slot.requirement.label)}{' '}
          <span className="text-xs font-normal text-fg-muted">
            {slot.requirement.mandatory ? t('business.required') : t('business.optional')}
          </span>
        </p>
        {doc ? <StatusBadge status={doc.status} kind="doc" /> : <span className="text-xs text-fg-muted">{t('admin.notUploaded')}</span>}
      </div>
      {doc && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <FileText className="h-4 w-4 text-brand-600" aria-hidden="true" />
          <span className="min-w-0 flex-1 truncate">{doc.fileName} · {formatBytes(doc.sizeBytes)}</span>
          <Button variant="secondary" size="sm" onClick={() => void view()}>
            <Eye className="h-4 w-4" aria-hidden="true" />{t('business.view')}
          </Button>
          {canReview && (
            <>
              <Button variant="secondary" size="sm" onClick={() => void review('ACCEPTED')} disabled={doc.status === 'ACCEPTED'}>
                <Check className="h-4 w-4" aria-hidden="true" />{t('admin.accept')}
              </Button>
              <Button variant="secondary" size="sm" onClick={() => setRejecting((v) => !v)}>
                <X className="h-4 w-4" aria-hidden="true" />{t('admin.rejectDocument')}
              </Button>
            </>
          )}
        </div>
      )}
      {doc?.reviewerNote && <p className="text-sm text-fg-muted">{t('admin.note')}: {doc.reviewerNote}</p>}
      {rejecting && (
        <div className="space-y-2">
          <TextAreaField label={t('admin.rejectNote')} rows={2} value={note} maxLength={1000}
            onChange={(e) => setNote(e.target.value)} error={form.fieldErrors.note} />
          <Button variant="danger" size="sm" onClick={() => void review('REJECTED')} disabled={!note.trim()} loading={form.submitting}>
            {t('admin.confirmRejectDocument')}
          </Button>
        </div>
      )}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
    </li>
  );
}

function PayoutAccount({ orgId }: { orgId: number }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [value, setValue] = useState('');
  const [saved, setSaved] = useState(false);
  return (
    <form className="flex flex-wrap items-end gap-3" onSubmit={(e) => {
      e.preventDefault();
      setSaved(false);
      void form.submit(() => paymentApi.setPayoutAccount(orgId, value.trim()).then(() => true)).then((ok) => ok && setSaved(true));
    }}>
      <TextField label={t('payment.payoutAccount')} value={value} onChange={(e) => setValue(e.target.value)} placeholder="acc_XXXXXXXX"
        hint={t('payment.payoutAccountHint')} error={form.fieldErrors.accountId} className="min-w-[16rem] flex-1" />
      <Button type="submit" variant="secondary" loading={form.submitting}>{t('common.save')}</Button>
      {saved && <span className="text-sm text-success">{t('account.saved')}</span>}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
    </form>
  );
}

type Decision = 'approve' | 'reject' | 'suspend' | 'reinstate';

function Decisions({ org, onUpdated }: { org: OrganizationDetail; onUpdated: (d: OrganizationDetail) => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [open, setOpen] = useState<Decision | null>(null);
  const [reason, setReason] = useState('');
  const status = org.summary.status;
  const available: Decision[] =
    status === 'PENDING' ? ['approve', 'reject'] : status === 'APPROVED' ? ['suspend'] : status === 'SUSPENDED' ? ['reinstate'] : [];
  if (available.length === 0) return <p className="text-sm text-fg-muted">{t('admin.noDecision')}</p>;
  const needsReason = open === 'reject' || open === 'suspend';

  const decide = async () => {
    if (!open) return;
    const id = org.summary.id;
    const r = reason.trim();
    const call = { approve: () => api.approve(id, r || undefined), reject: () => api.reject(id, r),
      suspend: () => api.suspend(id, r), reinstate: () => api.reinstate(id, r || undefined) }[open];
    const d = await form.submit(call);
    if (d) {
      setOpen(null);
      setReason('');
      onUpdated(d);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {available.map((d) => (
          <Button key={d} variant={d === 'approve' || d === 'reinstate' ? 'primary' : 'danger'}
            onClick={() => { setOpen(d); form.setFormError(null); }}>
            {t(`admin.decision.${d}`)}
          </Button>
        ))}
      </div>
      {open && (
        <div className="space-y-3 rounded-xl border border-border p-4">
          <TextAreaField label={needsReason ? t('admin.reasonRequired') : t('admin.reasonOptional')} rows={3}
            value={reason} maxLength={1000} onChange={(e) => setReason(e.target.value)} error={form.fieldErrors.reason}
            hint={needsReason ? t('admin.reasonHint') : undefined} />
          {form.formError && <Alert tone="error">{form.formError}</Alert>}
          <div className="flex gap-2">
            <Button variant={open === 'approve' || open === 'reinstate' ? 'primary' : 'danger'} loading={form.submitting}
              disabled={needsReason && reason.trim().length < 5} onClick={() => void decide()}>
              {t(`admin.confirm.${open}`)}
            </Button>
            <Button variant="ghost" onClick={() => setOpen(null)}>{t('common.cancel')}</Button>
          </div>
        </div>
      )}
    </div>
  );
}

export function AdminOrganizationReviewPage() {
  const { t, i18n } = useTranslation();
  const pick = useLocalized();
  const id = Number(useParams().id);
  const qc = useQueryClient();
  const query = useQuery({ queryKey: orgKeys.adminDetail(id), queryFn: () => api.get(id) });
  const locale = intlLocale(i18n.language);

  if (query.isLoading) return <LoadingState />;
  if (query.error || !query.data) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  const org = query.data;
  const s = org.summary;
  const onUpdated = (d: OrganizationDetail) => {
    qc.setQueryData(orgKeys.adminDetail(id), d);
    void qc.invalidateQueries({ queryKey: ['admin-organizations'] });
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link to="/admin/organizations" className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('admin.backToQueue')}
        </Link>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        {s.logoUrl && <img src={fileUrl(s.logoUrl)} alt="" className="h-12 w-12 rounded-xl object-cover" />}
        <h1 className="text-3xl font-bold tracking-tight">{s.name}</h1>
        <StatusBadge status={s.status} />
      </div>
      {s.statusReason && <Alert tone="error">{s.statusReason}</Alert>}

      <Section title={t('admin.decisionTitle')}>
        <Decisions org={org} onUpdated={onUpdated} />
      </Section>

      <Section title={t('business.tab.details')}>
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          <Field label={t('business.category')} value={[pick(s.categoryName), pick(s.subcategoryName)].filter(Boolean).join(' › ')} />
          <Field label={t('business.confirmationMode')} value={t(`business.mode.${org.confirmationMode}`)} />
          <Field label={t('business.contactPhone')} value={org.contactPhone} />
          <Field label={t('business.contactEmail')} value={org.contactEmail} />
          <Field label={t('business.website')} value={org.website && (
            <a href={org.website} target="_blank" rel="noopener noreferrer nofollow" className="text-brand-700 hover:underline">{org.website}</a>
          )} />
          <Field label={t('business.gstin')} value={org.gstin} />
          <div className="sm:col-span-2"><Field label={t('business.description')} value={org.description} /></div>
        </dl>
      </Section>

      <Section title={t('admin.owners')}>
        <ul className="space-y-2 text-sm">
          {org.owners?.map((o) => (
            <li key={o.userId}>
              <span className="font-medium">{o.fullName}</span>
              <span className="text-fg-muted">
                {' · '}{o.email ?? '—'} {o.email && `(${o.emailVerified ? t('account.verified') : t('account.notVerified')})`}
                {' · '}{o.phone ?? '—'} {o.phone && `(${o.phoneVerified ? t('account.verified') : t('account.notVerified')})`}
              </span>
            </li>
          ))}
        </ul>
      </Section>

      <Section title={t('business.tab.branches')}>
        <ul className="space-y-4 text-sm">
          {org.branches.map((b) => (
            <li key={b.id}>
              <p className="font-medium">{b.name}{!b.active && ` (${t('business.inactive')})`}</p>
              <p className="text-fg-muted">{[b.addressLine1, b.addressLine2, b.locality, b.city, b.state, b.pincode].filter(Boolean).join(', ')}</p>
              <p className="text-fg-muted">
                <a className="text-brand-700 hover:underline" target="_blank" rel="noopener noreferrer"
                  href={`https://www.google.com/maps?q=${b.latitude},${b.longitude}`}>
                  {b.latitude}, {b.longitude}
                </a>
              </p>
              <p className="text-fg-muted">
                {b.hours.map((h) => `${t(`days.${DAY_KEYS[h.dayOfWeek - 1]}`)} ${formatWallTime(h.opensAt, locale)}–${formatWallTime(h.closesAt, locale)}`).join(' · ') || t('business.hoursMissing')}
              </p>
            </li>
          ))}
          {org.branches.length === 0 && <li className="text-fg-muted">{t('business.noBranches')}</li>}
        </ul>
      </Section>

      <Section title={t('business.tab.documents')}>
        <ul className="divide-y divide-border rounded-xl border border-border">
          {org.documents.map((slot) => (
            <DocumentReview key={slot.requirement.id} org={org} slot={slot} onUpdated={onUpdated} />
          ))}
        </ul>
      </Section>

      {s.status === 'APPROVED' && (
        <Section title={t('payment.payouts')}>
          <PayoutAccount orgId={s.id} />
        </Section>
      )}

      <Section title={t('business.history')}>
        <HistoryList entries={org.history} />
      </Section>
    </div>
  );
}
