import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { BadgeCheck, Building2, FileText } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { useAuth } from '@/contexts/AuthContext';
import { OrganizationForm } from '@/features/organization/OrganizationForm';
import { accountApi } from '@/services/authApi';
import { ownerOrganizationApi } from '@/services/organizationApi';

export function RegisterBusinessPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { setUser } = useAuth();

  const create = async (input: Parameters<typeof ownerOrganizationApi.create>[0]) => {
    const detail = await ownerOrganizationApi.create(input);
    // The account just gained the business-owner role; refresh it so owner pages unlock.
    setUser(await accountApi.me());
    navigate(`/owner/organizations/${detail.summary.id}?step=branches`, { replace: true });
    return detail;
  };

  const steps = [
    { icon: Building2, title: t('business.stepDetails'), body: t('business.stepDetailsBody') },
    { icon: FileText, title: t('business.stepBranchesDocs'), body: t('business.stepBranchesDocsBody') },
    { icon: BadgeCheck, title: t('business.stepReview'), body: t('business.stepReviewBody') },
  ];

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('business.registerTitle')}</h1>
      <p className="mt-2 max-w-2xl text-fg-muted">{t('business.registerSubtitle')}</p>
      <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_18rem]">
        <Card className="p-6">
          <OrganizationForm submitLabel={t('business.createAndContinue')} onSubmit={create} />
        </Card>
        <ol className="space-y-4">
          {steps.map((s, i) => (
            <li key={s.title} className="flex gap-3">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
                <s.icon className="h-5 w-5" aria-hidden="true" />
              </span>
              <div>
                <p className="font-semibold">{i + 1}. {s.title}</p>
                <p className="text-sm text-fg-muted">{s.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
