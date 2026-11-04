import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Briefcase,
  Camera,
  Car,
  Dumbbell,
  GraduationCap,
  HeartPulse,
  Home,
  PawPrint,
  Scissors,
  Sparkles,
  Stethoscope,
  Store,
  Utensils,
  type LucideIcon,
} from 'lucide-react';
import clsx from 'clsx';
import { API_BASE_URL } from '@/services/apiClient';
import type { DocumentStatus, LocalizedText, OrganizationStatus } from '@/services/organizationApi';

/** Icons an admin can pick for a category. Unknown names fall back to a shop icon. */
export const CATEGORY_ICONS: Record<string, LucideIcon> = {
  stethoscope: Stethoscope,
  'heart-pulse': HeartPulse,
  scissors: Scissors,
  sparkles: Sparkles,
  'graduation-cap': GraduationCap,
  dumbbell: Dumbbell,
  'paw-print': PawPrint,
  car: Car,
  home: Home,
  camera: Camera,
  utensils: Utensils,
  briefcase: Briefcase,
  store: Store,
};

export function CategoryIcon({ name, className }: { name: string; className?: string }) {
  const Icon = CATEGORY_ICONS[name] ?? Store;
  return <Icon className={clsx('h-5 w-5', className)} aria-hidden="true" />;
}

/** Picks the current language from translatable text, falling back to English. */
export function useLocalized() {
  const { i18n } = useTranslation();
  return useCallback(
    (text?: LocalizedText | null) => (text ? (text[i18n.language] ?? text.en ?? '') : ''),
    [i18n.language],
  );
}

const statusStyles: Record<OrganizationStatus | DocumentStatus, string> = {
  DRAFT: 'bg-surface-muted text-fg-muted',
  PENDING: 'bg-amber-100 text-amber-900 dark:bg-amber-500/20 dark:text-amber-200',
  SUBMITTED: 'bg-amber-100 text-amber-900 dark:bg-amber-500/20 dark:text-amber-200',
  APPROVED: 'bg-success/15 text-success',
  ACCEPTED: 'bg-success/15 text-success',
  REJECTED: 'bg-danger/15 text-danger',
  SUSPENDED: 'bg-danger/15 text-danger',
};

export function StatusBadge({ status, kind = 'org' }: { status: OrganizationStatus | DocumentStatus; kind?: 'org' | 'doc' }) {
  const { t } = useTranslation();
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', statusStyles[status])}>
      {t(kind === 'org' ? `orgStatus.${status}` : `docStatus.${status}`)}
    </span>
  );
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '')} MB`;
}

const TYPE_LABELS: Record<string, string> = {
  'application/pdf': 'PDF',
  'image/jpeg': 'JPG',
  'image/png': 'PNG',
  'image/webp': 'WebP',
};

export const typeLabels = (types: string[]) => types.map((t) => TYPE_LABELS[t] ?? t).join(', ');

/** Opens a downloaded private file in a new tab without ever exposing a shareable URL. */
export function openBlob(blob: Blob) {
  if (typeof URL.createObjectURL !== 'function') return;
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** "09:00:00" → "09:00" for <input type="time">. */
export const hhmm = (time?: string) => (time ? time.slice(0, 5) : '');

export const ISO_DAYS = [1, 2, 3, 4, 5, 6, 7] as const;
export const DAY_KEYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const;

/** Public file paths come back as /api/v1/...; rebase them when the API lives at a different base URL. */
export const fileUrl = (path: string) => path.replace(/^\/api\/v1/, API_BASE_URL);
