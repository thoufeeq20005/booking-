import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Flag, Star } from 'lucide-react';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextAreaField } from '@/components/ui/FormFields';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import { reviewApi, type Review } from '@/services/engagementApi';
import { formatDate } from '@/utils/format';

export function Stars({ value, size = 'h-4 w-4' }: { value: number; size?: string }) {
  const { t } = useTranslation();
  return (
    <span className="inline-flex" role="img" aria-label={t('review.starsLabel', { count: value })}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Star key={i} className={clsx(size, i <= value ? 'fill-amber-400 text-amber-400' : 'text-border')} aria-hidden="true" />
      ))}
    </span>
  );
}

/** Star picker + comment for a completed booking. */
export function ReviewForm({ appointmentId, onDone }: { appointmentId: number; onDone: () => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-semibold">{t('review.rateVisit')}</h2>
      <div role="radiogroup" aria-label={t('review.rating')} className="flex gap-1">
        {[1, 2, 3, 4, 5].map((i) => (
          <button key={i} type="button" role="radio" aria-checked={rating === i} aria-label={t('review.starsLabel', { count: i })}
            onClick={() => setRating(i)}>
            <Star className={clsx('h-8 w-8', i <= rating ? 'fill-amber-400 text-amber-400' : 'text-border')} aria-hidden="true" />
          </button>
        ))}
      </div>
      <TextAreaField label={t('review.comment')} rows={3} maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <Button disabled={rating === 0} loading={form.submitting}
        onClick={() => void form.submit(() => reviewApi.create(appointmentId, rating, comment.trim() || undefined)).then((r) => r && onDone())}>
        {t('review.submit')}
      </Button>
    </div>
  );
}

function ReportButton({ reviewId }: { reviewId: number }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [done, setDone] = useState(false);
  const [text, setText] = useState('');
  if (done) return <span className="text-xs text-fg-muted">{t('review.reported')}</span>;
  return open ? (
    <span className="flex flex-wrap items-center gap-2">
      <input aria-label={t('review.reportReason')} placeholder={t('review.reportReason')} value={text} onChange={(e) => setText(e.target.value)}
        className="h-8 rounded-lg border border-border bg-surface px-2 text-xs" />
      <Button size="sm" variant="ghost" disabled={!text.trim()}
        onClick={() => void reviewApi.report(reviewId, text.trim()).then(() => setDone(true))}>{t('review.report')}</Button>
    </span>
  ) : (
    <button type="button" onClick={() => setOpen(true)} className="inline-flex items-center gap-1 text-xs text-fg-muted hover:text-fg">
      <Flag className="h-3 w-3" aria-hidden="true" />{t('review.report')}
    </button>
  );
}

export function ReviewItem({ r, canReport }: { r: Review; canReport?: boolean }) {
  const { t, i18n } = useTranslation();
  return (
    <li className="space-y-1 py-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Stars value={r.rating} />
        <span className="font-medium">{r.authorName}</span>
        <span className="text-xs text-fg-muted">{r.serviceName} · {formatDate(r.createdAt, intlLocale(i18n.language))}</span>
      </div>
      {r.comment && <p className="whitespace-pre-line">{r.comment}</p>}
      {r.ownerReply && <p className="rounded-lg bg-surface-muted p-2 text-fg-muted"><span className="font-medium text-fg">{t('review.reply')}:</span> {r.ownerReply}</p>}
      {canReport && <ReportButton reviewId={r.id} />}
    </li>
  );
}

export function BusinessReviews({ slug, rating, count }: { slug: string; rating: number; count: number }) {
  const { t } = useTranslation();
  const { status } = useAuth();
  const { data = [] } = useQuery({ queryKey: ['reviews', slug], queryFn: () => reviewApi.forBusiness(slug) });
  return (
    <Card className="p-6">
      <h2 className="flex flex-wrap items-center gap-2 text-lg font-semibold">
        {t('review.title')}
        {count > 0 && <span className="inline-flex items-center gap-1 text-sm font-normal text-fg-muted"><Stars value={Math.round(rating)} /> {Number(rating).toFixed(1)} · {t('biz.reviews', { count })}</span>}
      </h2>
      {data.length === 0 ? <p className="mt-3 text-sm text-fg-muted">{t('review.none')}</p> : (
        <ul className="mt-2 divide-y divide-border">{data.map((r) => <ReviewItem key={r.id} r={r} canReport={status === 'authenticated'} />)}</ul>
      )}
    </Card>
  );
}
