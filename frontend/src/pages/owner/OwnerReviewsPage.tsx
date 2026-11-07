import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { ReviewItem } from '@/features/engagement/ReviewWidgets';
import { reviewApi } from '@/services/engagementApi';
import { orgKeys, ownerOrganizationApi } from '@/services/organizationApi';

export function OwnerReviewsPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const orgs = useQuery({ queryKey: orgKeys.mine, queryFn: ownerOrganizationApi.list });
  const approved = (orgs.data ?? []).filter((o) => o.status === 'APPROVED' || o.status === 'SUSPENDED');
  const [orgId, setOrgId] = useState<number>();
  const current = orgId ?? approved[0]?.id;
  const reviews = useQuery({ queryKey: ['owner-reviews', current], queryFn: () => reviewApi.forOwner(current!), enabled: Boolean(current) });
  const [drafts, setDrafts] = useState<Record<number, string>>({});

  if (orgs.isLoading) return <LoadingState />;
  if (!current) return <div className="mx-auto max-w-4xl px-4 py-10"><EmptyState title={t('booking.noApprovedBusiness')} /></div>;

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('review.title')}</h1>
      {approved.length > 1 && (
        <SelectField label={t('admin.business')} value={current} onChange={(e) => setOrgId(Number(e.target.value))} className="w-56">
          {approved.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
        </SelectField>
      )}
      {reviews.data?.length === 0 && <EmptyState title={t('review.none')} />}
      <Card className="divide-y divide-border px-5">
        {reviews.data?.map((r) => (
          <div key={r.id} className="py-2">
            <ul><ReviewItem r={r} /></ul>
            {!r.ownerReply && (
              <div className="flex flex-wrap gap-2 pb-3">
                <input aria-label={t('review.writeReply')} placeholder={t('review.writeReply')} value={drafts[r.id] ?? ''}
                  onChange={(e) => setDrafts((d) => ({ ...d, [r.id]: e.target.value }))}
                  className="h-9 min-w-[16rem] flex-1 rounded-lg border border-border bg-surface px-2 text-sm" />
                <Button size="sm" variant="secondary" disabled={!drafts[r.id]?.trim()}
                  onClick={() => void reviewApi.reply(current, r.id, drafts[r.id]!.trim()).then(() => qc.invalidateQueries({ queryKey: ['owner-reviews'] }))}>
                  {t('review.sendReply')}
                </Button>
              </div>
            )}
          </div>
        ))}
      </Card>
    </div>
  );
}
