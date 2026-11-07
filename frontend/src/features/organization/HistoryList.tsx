import { useTranslation } from 'react-i18next';
import { intlLocale } from '@/i18n';
import type { HistoryEntry } from '@/services/organizationApi';
import { formatDateTime } from '@/utils/format';

export function HistoryList({ entries }: { entries: HistoryEntry[] }) {
  const { t, i18n } = useTranslation();
  if (entries.length === 0) return <p className="text-sm text-fg-muted">{t('business.noHistory')}</p>;
  return (
    <ol className="space-y-3 border-l-2 border-border pl-4">
      {[...entries].reverse().map((h, i) => (
        <li key={`${h.createdAt}-${i}`} className="text-sm">
          <p className="font-medium">{t(`business.action.${h.action}`)}</p>
          <p className="text-xs text-fg-muted">{formatDateTime(h.createdAt, intlLocale(i18n.language))}</p>
          {h.reason && <p className="mt-1 text-fg-muted">“{h.reason}”</p>}
        </li>
      ))}
    </ol>
  );
}
