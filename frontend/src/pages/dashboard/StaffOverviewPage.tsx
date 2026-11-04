import { useTranslation } from 'react-i18next';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';

/** Staff join businesses by invitation (Phase 5); until then there is nothing to schedule. */
export function StaffOverviewPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.hello', { name: user?.fullName ?? '' })}</h1>
      <Card className="p-6">
        <EmptyState title={t('dashboard.staffEmpty')}>{t('dashboard.staffEmptyHint')}</EmptyState>
      </Card>
    </div>
  );
}
