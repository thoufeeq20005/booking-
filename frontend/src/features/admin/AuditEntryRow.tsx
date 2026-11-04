import { useTranslation } from 'react-i18next';
import { intlLocale } from '@/i18n';
import type { AuditEntry } from '@/services/dashboardApi';
import { formatDateTime } from '@/utils/format';

/** "ORGANIZATION_APPROVED" → "Organization approved" when no translation exists. */
const humanize = (code: string) => {
  const s = code.toLowerCase().replace(/_/g, ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
};

export function useAuditLabel() {
  const { t } = useTranslation();
  return (action: string) => t(`audit.action.${action}`, { defaultValue: humanize(action) });
}

export function detailsText(details: AuditEntry['details']): string {
  if (details == null) return '';
  if (typeof details === 'string') return details;
  return Object.entries(details)
    .filter(([, v]) => v !== '' && v != null)
    .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
    .join(' · ');
}

/** Compact activity line used on the admin overview. */
export function AuditEntryRow({ entry }: { entry: AuditEntry }) {
  const { t, i18n } = useTranslation();
  const label = useAuditLabel();
  return (
    <li className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5 py-2.5 text-sm">
      <span>
        <span className="font-medium">{label(entry.action)}</span>
        <span className="text-fg-muted">
          {' · '}{entry.entityType}{entry.entityId ? ` #${entry.entityId}` : ''}
          {' · '}{entry.actorName ?? t('dashboard.system')}
        </span>
      </span>
      <time className="text-xs text-fg-muted" dateTime={entry.createdAt}>
        {formatDateTime(entry.createdAt, intlLocale(i18n.language))}
      </time>
    </li>
  );
}
