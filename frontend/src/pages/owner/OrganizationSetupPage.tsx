import { useId, type ReactNode } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, Circle, ImagePlus, Send, Trash2 } from 'lucide-react';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { BranchesSection } from '@/features/organization/BranchesSection';
import { DocumentsSection } from '@/features/organization/DocumentsSection';
import { HistoryList } from '@/features/organization/HistoryList';
import { OrganizationForm } from '@/features/organization/OrganizationForm';
import { ServicesSection } from '@/features/organization/ServicesSection';
import { StaffSection } from '@/features/organization/StaffSection';
import { fileUrl, StatusBadge, useLocalized } from '@/features/organization/shared';
import { useApiForm } from '@/hooks/useApiForm';
import {
  orgKeys,
  ownerOrganizationApi as api,
  type MissingStep,
  type OrganizationDetail,
} from '@/services/organizationApi';

const STEPS = ['details', 'branches', 'documents', 'services', 'staff', 'review'] as const;
type Step = (typeof STEPS)[number];

/** Which tab fixes each missing step. */
const STEP_FOR: Record<MissingStep, Step | 'account'> = {
  CONTACT: 'details',
  BRANCH: 'branches',
  WORKING_HOURS: 'branches',
  DOCUMENTS: 'documents',
  ACCOUNT_VERIFICATION: 'account',
};

function LogoField({ org }: { org: OrganizationDetail }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const form = useApiForm();
  const inputId = useId();
  const id = org.summary.id;
  const apply = (d?: OrganizationDetail) => d && qc.setQueryData(orgKeys.detail(id), d);
  if (!org.editable.operations && !org.summary.logoUrl) return null;

  return (
    <div className="flex flex-wrap items-center gap-4">
      {org.summary.logoUrl ? (
        <img src={fileUrl(org.summary.logoUrl)} alt={t('business.logo')} className="h-16 w-16 rounded-xl object-cover" />
      ) : (
        <span className="flex h-16 w-16 items-center justify-center rounded-xl border border-dashed border-border text-fg-muted">
          <ImagePlus className="h-6 w-6" aria-hidden="true" />
        </span>
      )}
      {org.editable.operations && (
        <div className="space-y-1">
          <div className="flex gap-2">
            <input id={inputId} type="file" accept="image/png,image/jpeg,image/webp" className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0];
                e.target.value = '';
                if (f) void form.submit(() => api.uploadLogo(id, f)).then(apply);
              }} />
            <label htmlFor={inputId} className={clsx(buttonClasses('secondary', 'sm'), 'cursor-pointer')}>
              {org.summary.logoUrl ? t('business.changeLogo') : t('business.uploadLogo')}
            </label>
            {org.summary.logoUrl && (
              <Button variant="ghost" size="sm" aria-label={t('business.removeLogo')}
                onClick={() => void form.submit(() => api.removeLogo(id)).then(apply)}>
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </Button>
            )}
          </div>
          <p className="text-xs text-fg-muted">{t('business.logoHint')}</p>
          {form.formError && <p className="text-xs text-danger">{form.formError}</p>}
        </div>
      )}
    </div>
  );
}

function ReviewStep({ org, goTo }: { org: OrganizationDetail; goTo: (s: Step) => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const form = useApiForm();
  const canSubmit = org.editable.identity;
  const checks: MissingStep[] = ['CONTACT', 'BRANCH', 'WORKING_HOURS', 'DOCUMENTS', 'ACCOUNT_VERIFICATION'];

  const submit = async () => {
    const d = await form.submit(() => api.submit(org.summary.id));
    if (d) {
      qc.setQueryData(orgKeys.detail(org.summary.id), d);
      void qc.invalidateQueries({ queryKey: orgKeys.mine });
    }
  };

  return (
    <div className="space-y-6">
      {canSubmit && (
        <Card className="p-6">
          <h2 className="text-lg font-semibold">{t('business.checklist')}</h2>
          <ul className="mt-4 space-y-2">
            {checks.map((c) => {
              const missing = org.missingSteps.includes(c);
              const target = STEP_FOR[c];
              return (
                <li key={c} className="flex items-center gap-2.5 text-sm">
                  {missing ? (
                    <Circle className="h-5 w-5 text-fg-muted" aria-hidden="true" />
                  ) : (
                    <CheckCircle2 className="h-5 w-5 text-success" aria-hidden="true" />
                  )}
                  <span className={missing ? '' : 'text-fg-muted'}>{t(`business.missing.${c}`)}</span>
                  {missing && (target === 'account' ? (
                    <Link to="/account" className="ml-auto text-sm font-medium text-brand-700 hover:underline">
                      {t('business.fix')}
                    </Link>
                  ) : (
                    <button type="button" onClick={() => goTo(target)}
                      className="ml-auto text-sm font-medium text-brand-700 hover:underline">
                      {t('business.fix')}
                    </button>
                  ))}
                </li>
              );
            })}
          </ul>
          {form.formError && <Alert tone="error" className="mt-4">{form.formError}</Alert>}
          <p className="mt-5 text-sm text-fg-muted">{t('business.submitHint')}</p>
          <Button className="mt-3" onClick={() => void submit()} loading={form.submitting}
            disabled={org.missingSteps.length > 0}>
            <Send className="h-4 w-4" aria-hidden="true" />
            {org.summary.status === 'REJECTED' ? t('business.resubmit') : t('business.submit')}
          </Button>
        </Card>
      )}
      <Card className="p-6">
        <h2 className="text-lg font-semibold">{t('business.history')}</h2>
        <div className="mt-4">
          <HistoryList entries={org.history} />
        </div>
      </Card>
    </div>
  );
}

function StatusBanner({ org }: { org: OrganizationDetail }) {
  const { t } = useTranslation();
  const s = org.summary;
  const messages: Partial<Record<typeof s.status, ReactNode>> = {
    DRAFT: t('business.banner.DRAFT'),
    PENDING: t('business.banner.PENDING'),
    APPROVED: t('business.banner.APPROVED'),
    REJECTED: t('business.banner.REJECTED', { reason: s.statusReason ?? '' }),
    SUSPENDED: t('business.banner.SUSPENDED', { reason: s.statusReason ?? '' }),
  };
  const tone = s.status === 'APPROVED' ? 'success' : s.status === 'REJECTED' || s.status === 'SUSPENDED' ? 'error' : 'info';
  return <Alert tone={tone}>{messages[s.status]}</Alert>;
}

export function OrganizationSetupPage() {
  const { t } = useTranslation();
  const pick = useLocalized();
  const id = Number(useParams().id);
  const [params, setParams] = useSearchParams();
  const qc = useQueryClient();
  const query = useQuery({ queryKey: orgKeys.detail(id), queryFn: () => api.get(id), enabled: Number.isFinite(id) });
  const step: Step = (STEPS as readonly string[]).includes(params.get('step') ?? '')
    ? (params.get('step') as Step) : 'details';
  const goTo = (s: Step) => setParams({ step: s }, { replace: true });

  if (query.isLoading) return <LoadingState />;
  if (query.error || !query.data) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  const org = query.data;

  const stepDone: Record<Step, boolean> = {
    details: !org.missingSteps.includes('CONTACT'),
    branches: !org.missingSteps.includes('BRANCH') && !org.missingSteps.includes('WORKING_HOURS'),
    documents: !org.missingSteps.includes('DOCUMENTS'),
    services: false,
    staff: false,
    review: org.summary.status !== 'DRAFT' && org.summary.status !== 'REJECTED',
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <Link to="/owner/organizations" className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        {t('business.myBusinesses')}
      </Link>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{org.summary.name}</h1>
        <StatusBadge status={org.summary.status} />
      </div>
      <p className="-mt-4 text-fg-muted">{[pick(org.summary.categoryName), pick(org.summary.subcategoryName)].filter(Boolean).join(' › ')}</p>
      <StatusBanner org={org} />

      <div role="tablist" aria-label={t('business.setupSteps')} className="flex gap-1 overflow-x-auto border-b border-border">
        {STEPS.map((s, i) => (
          <button key={s} type="button" role="tab" aria-selected={step === s} onClick={() => goTo(s)}
            className={clsx('flex shrink-0 items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium transition',
              step === s ? 'border-brand-600 text-brand-700' : 'border-transparent text-fg-muted hover:text-fg')}>
            {stepDone[s] ? <CheckCircle2 className="h-4 w-4 text-success" aria-hidden="true" /> : <span className="text-xs">{i + 1}</span>}
            {t(`business.tab.${s}`)}
          </button>
        ))}
      </div>

      <div role="tabpanel">
        {step === 'details' && (
          <Card className="space-y-6 p-6">
            <LogoField org={org} />
            {org.editable.operations ? (
              <OrganizationForm
                key={org.summary.updatedAt}
                initial={org}
                identityLocked={!org.editable.identity}
                submitLabel={t('common.save')}
                onSubmit={async (input) => {
                  const d = await api.update(id, input);
                  qc.setQueryData(orgKeys.detail(id), d);
                  return d;
                }}
              />
            ) : (
              <Alert>{t('business.readOnly')}</Alert>
            )}
          </Card>
        )}
        {step === 'branches' && <BranchesSection org={org} />}
        {step === 'documents' && <DocumentsSection org={org} />}
        {step === 'services' && <ServicesSection org={org} />}
        {step === 'staff' && <StaffSection org={org} />}
        {step === 'review' && <ReviewStep org={org} goTo={goTo} />}
      </div>
    </div>
  );
}
