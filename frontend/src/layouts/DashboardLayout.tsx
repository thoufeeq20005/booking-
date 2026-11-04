import { Suspense } from 'react';
import { NavLink, Outlet } from 'react-router';
import { useTranslation } from 'react-i18next';
import {
  Building2,
  CalendarDays,
  ClipboardCheck,
  FolderTree,
  LayoutDashboard,
  ScrollText,
  ShieldCheck,
  Star,
  UserRound,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import clsx from 'clsx';
import { LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import type { Role } from '@/services/apiClient';

export type DashboardArea = 'customer' | 'owner' | 'staff' | 'admin';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

const AREA_ROLE: Record<DashboardArea, Role | null> = {
  customer: null,
  owner: 'BUSINESS_OWNER',
  staff: 'STAFF',
  admin: 'SUPER_ADMIN',
};

function useNav(area: DashboardArea): NavItem[] {
  const { t } = useTranslation();
  switch (area) {
    case 'admin':
      return [
        { to: '/admin', label: t('dashboard.overview'), icon: LayoutDashboard, end: true },
        { to: '/admin/organizations', label: t('admin.organizations'), icon: ClipboardCheck },
        { to: '/admin/users', label: t('dashboard.users'), icon: Users },
        { to: '/admin/categories', label: t('admin.categories'), icon: FolderTree },
        { to: '/admin/settlements', label: t('payment.settlements'), icon: Wallet },
        { to: '/admin/reviews', label: t('review.moderation'), icon: Star },
        { to: '/admin/audit', label: t('dashboard.auditLog'), icon: ScrollText },
      ];
    case 'owner':
      return [
        { to: '/owner', label: t('dashboard.overview'), icon: LayoutDashboard, end: true },
        { to: '/owner/appointments', label: t('booking.appointments'), icon: CalendarDays },
        { to: '/owner/organizations', label: t('business.myBusinesses'), icon: Building2 },
        { to: '/owner/reviews', label: t('review.title'), icon: Star },
      ];
    case 'staff':
      return [{ to: '/staff', label: t('dashboard.overview'), icon: CalendarDays, end: true }];
    default:
      return [
        { to: '/customer', label: t('dashboard.overview'), icon: LayoutDashboard, end: true },
        { to: '/customer/bookings', label: t('booking.myBookings'), icon: CalendarDays },
        { to: '/account', label: t('account.title'), icon: UserRound },
      ];
  }
}

/** Sidebar shell shared by every role's area. Lists the other areas the person can switch to. */
export function DashboardLayout({ area }: { area: DashboardArea }) {
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const nav = useNav(area);
  const otherAreas = (Object.keys(AREA_ROLE) as DashboardArea[]).filter((a) => {
    const role = AREA_ROLE[a];
    return a !== area && (role === null || hasRole(role));
  });

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    clsx(
      'flex shrink-0 items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium transition',
      isActive ? 'bg-brand-50 text-brand-700' : 'text-fg-muted hover:bg-surface-muted hover:text-fg',
    );

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 pt-4 sm:px-6 lg:flex-row lg:gap-8 lg:pt-8">
      <aside className="lg:w-56 lg:shrink-0">
        <div className="lg:sticky lg:top-24">
          <p className="mb-2 hidden items-center gap-2 px-3 text-xs font-semibold uppercase tracking-wider text-fg-muted lg:flex">
            {area === 'admin' && <ShieldCheck className="h-4 w-4" aria-hidden="true" />}
            {t(`dashboard.area.${area}`)}
          </p>
          <nav aria-label={t(`dashboard.area.${area}`)} className="-mx-1 flex gap-1 overflow-x-auto px-1 pb-1 lg:flex-col lg:overflow-visible">
            {nav.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                <item.icon className="h-4 w-4" aria-hidden="true" />
                {item.label}
              </NavLink>
            ))}
          </nav>
          {otherAreas.length > 0 && (
            <div className="mt-6 hidden lg:block">
              <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-fg-muted">{t('dashboard.switchTo')}</p>
              <div className="flex flex-col gap-1">
                {otherAreas.map((a) => (
                  <NavLink key={a} to={`/${a}`} className={linkClass}>
                    {t(`dashboard.area.${a}`)}
                  </NavLink>
                ))}
              </div>
            </div>
          )}
        </div>
      </aside>
      {/* Pages keep their own centred containers; the shell only removes the outer gutter on large screens. */}
      <div className="min-w-0 flex-1 [&>div]:px-0 [&>div]:sm:px-0">
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </div>
    </div>
  );
}
