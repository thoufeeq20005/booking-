import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CheckboxField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { ReviewItem } from '@/features/engagement/ReviewWidgets';
import { reviewApi } from '@/services/engagementApi';

export function AdminReviewsPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [reported, setReported] = useState(true);
  const q = useQuery({ queryKey: ['moderation', reported], queryFn: () => reviewApi.moderation(reported) });
  const act = (id: number, action: 'hide' | 'restore') =>
    void reviewApi.moderate(id, action).then(() => qc.invalidateQueries({ queryKey: ['moderation'] }));

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('review.moderation')}</h1>
      <CheckboxField label={t('review.onlyReported')} checked={reported} onChange={setReported} />
      {q.isLoading && <LoadingState />}
      {q.data?.length === 0 && <EmptyState title={t('review.none')} />}
      <Card className="divide-y divide-border px-5">
        {q.data?.map((r) => (
          <div key={r.id} className="flex flex-wrap items-start gap-3 py-2">
            <ul className="min-w-0 flex-1"><ReviewItem r={r} /></ul>
            <div className="py-3 text-xs text-fg-muted">
              {r.organizationName} · {t('review.reports', { count: r.reportCount })} · {t(`review.status.${r.status}`)}
              <div className="mt-2">
                {r.status === 'VISIBLE'
                  ? <Button size="sm" variant="danger" onClick={() => act(r.id, 'hide')}>{t('review.hide')}</Button>
                  : <Button size="sm" variant="secondary" onClick={() => act(r.id, 'restore')}>{t('review.restore')}</Button>}
              </div>
            </div>
          </div>
        ))}
      </Card>
    </div>
  );
}
