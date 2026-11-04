import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { notificationApi } from '@/services/engagementApi';

/** Header bell with the unread count; refreshed every minute. */
export function NotificationBell({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation();
  const { data: unread = 0 } = useQuery({ queryKey: ['unread'], queryFn: notificationApi.unread, refetchInterval: 60_000 });
  const label = unread > 0 ? t('notify.unreadLabel', { count: unread }) : t('notify.title');
  return (
    <Link to="/notifications" onClick={onNavigate} aria-label={label}
      className="relative inline-flex h-10 w-10 items-center justify-center rounded-xl text-fg-muted hover:bg-surface-muted hover:text-fg">
      <Bell className="h-5 w-5" aria-hidden="true" />
      {unread > 0 && (
        <span className="absolute right-1.5 top-1.5 min-w-[1.1rem] rounded-full bg-brand-600 px-1 text-center text-[10px] font-bold leading-[1.1rem] text-white">
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </Link>
  );
}
