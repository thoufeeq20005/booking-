import { Suspense, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { CircleUser, Clock, LogOut, Mail, Menu, Phone, X } from 'lucide-react';
import clsx from 'clsx';
import { Logo } from '@/components/brand/Logo';
import { Button, buttonClasses } from '@/components/ui/Button';
import { LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { NotificationBell } from '@/features/engagement/NotificationBell';
import { LanguageSelector } from '@/components/common/LanguageSelector';
import { usePublicConfig } from '@/services/platformApi';
import { formatWallTime } from '@/utils/format';
import { intlLocale } from '@/i18n';

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  clsx(
    'rounded-lg px-3 py-2 text-sm font-medium transition',
    isActive ? 'text-brand-700' : 'text-fg-muted hover:text-fg',
  );

function AuthControls({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation();
  const { status, user, signOut } = useAuth();
  const navigate = useNavigate();

  if (status === 'loading') return null;
  if (status === 'authenticated' && user) {
    return (
      <div className="flex items-center gap-2">
        <NotificationBell onNavigate={onNavigate} />
        <Link
          to="/account"
          onClick={onNavigate}
          className="inline-flex h-10 items-center gap-2 rounded-xl px-3 text-sm font-medium text-fg hover:bg-surface-muted"
        >
          <CircleUser className="h-5 w-5 text-brand-600" aria-hidden="true" />
          <span className="max-w-[10rem] truncate">{user.fullName}</span>
        </Link>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            onNavigate?.();
            void signOut().finally(() => navigate('/'));
          }}
        >
          <LogOut className="h-4 w-4" aria-hidden="true" />
          {t('nav.signOut')}
        </Button>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-2">
      <Link to="/signin" onClick={onNavigate} className={buttonClasses('ghost', 'sm')}>
        {t('nav.signIn')}
      </Link>
      <Link to="/register" onClick={onNavigate} className={buttonClasses('primary', 'sm')}>
        {t('nav.register')}
      </Link>
    </div>
  );
}

/** Signed-in people get their dashboard; visitors and customers without a business see "For business". */
function RoleLinks({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation();
  const { status, hasRole } = useAuth();
  return (
    <>
      <NavLink to="/search" className={navLinkClass} onClick={onNavigate}>
        {t('nav.findServices')}
      </NavLink>
      <NavLink to="/movies" className={navLinkClass} onClick={onNavigate}>
        {t('nav.movies')}
      </NavLink>
      <NavLink to="/dining" className={navLinkClass} onClick={onNavigate}>
        {t('nav.dining')}
      </NavLink>
      {status === 'authenticated' && (
        <NavLink to="/dashboard" className={navLinkClass} onClick={onNavigate}>
          {t('nav.dashboard')}
        </NavLink>
      )}
      {!hasRole('BUSINESS_OWNER') && (
        <NavLink to="/business/register" className={navLinkClass} onClick={onNavigate}>
          {t('nav.forBusiness')}
        </NavLink>
      )}
    </>
  );
}

function SiteHeader() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <header className="sticky top-0 z-40 border-b border-border/70 bg-surface/80 backdrop-blur-lg">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <Link to="/" aria-label={t('common.appName')} className="shrink-0">
          <Logo />
        </Link>
        <nav className="ml-6 hidden items-center gap-1 md:flex" aria-label="Main">
          <NavLink to="/" end className={navLinkClass}>
            {t('nav.home')}
          </NavLink>
          <NavLink to="/status" className={navLinkClass}>
            {t('nav.status')}
          </NavLink>
          <RoleLinks />
        </nav>
        <div className="ml-auto hidden items-center gap-2 md:flex">
          <LanguageSelector />
          <span className="mx-1 h-6 w-px bg-border" aria-hidden="true" />
          <AuthControls />
        </div>
        <button
          type="button"
          className="ml-auto inline-flex h-10 w-10 items-center justify-center rounded-xl text-fg-muted hover:bg-surface-muted md:hidden"
          aria-label={open ? t('header.closeMenu') : t('header.openMenu')}
          aria-expanded={open}
          aria-controls="mobile-menu"
          onClick={() => setOpen((v) => !v)}
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>
      {open && (
        <div id="mobile-menu" className="border-t border-border bg-surface px-4 pb-4 pt-2 md:hidden">
          <nav className="flex flex-col" aria-label="Mobile">
            <NavLink to="/" end className={navLinkClass} onClick={() => setOpen(false)}>
              {t('nav.home')}
            </NavLink>
            <NavLink to="/status" className={navLinkClass} onClick={() => setOpen(false)}>
              {t('nav.status')}
            </NavLink>
            <RoleLinks onNavigate={() => setOpen(false)} />
          </nav>
          <div className="mt-3 flex items-center gap-2">
            <LanguageSelector />
          </div>
          <div className="mt-3 border-t border-border pt-3">
            <AuthControls onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}
    </header>
  );
}

function SiteFooter() {
  const { t, i18n } = useTranslation();
  const { data: config } = usePublicConfig();
  const support = config?.support;
  const locale = intlLocale(i18n.language);
  const hasContact = Boolean(support?.email || support?.phone);

  return (
    <footer className="mt-24 border-t border-border bg-surface">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-3">
        <div className="space-y-3">
          <Logo />
          <p className="max-w-xs text-sm text-fg-muted">{t('common.tagline')}</p>
          <p className="text-xs text-fg-muted">{t('footer.currencyNote')}</p>
        </div>
        <div className="md:col-span-2">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-fg-muted">{t('footer.support')}</h2>
          <ul className="mt-4 space-y-2 text-sm">
            {support?.email && (
              <li className="flex items-center gap-2">
                <Mail className="h-4 w-4 text-brand-600" aria-hidden="true" />
                <span className="sr-only">{t('footer.email')}:</span>
                <a className="hover:text-brand-700" href={`mailto:${support.email}`}>
                  {support.email}
                </a>
              </li>
            )}
            {support?.phone && (
              <li className="flex items-center gap-2">
                <Phone className="h-4 w-4 text-brand-600" aria-hidden="true" />
                <span className="sr-only">{t('footer.phone')}:</span>
                <a className="hover:text-brand-700" href={`tel:${support.phone}`}>
                  {support.phone}
                </a>
              </li>
            )}
            {support && (
              <li className="flex items-center gap-2 text-fg-muted">
                <Clock className="h-4 w-4 text-brand-600" aria-hidden="true" />
                <span>
                  {t('footer.hours')}: {support.workingDays.map((d) => t(`days.${d}`)).join(', ')} ·{' '}
                  {formatWallTime(support.opensAt, locale)} – {formatWallTime(support.closesAt, locale)}
                </span>
              </li>
            )}
            {config && !hasContact && <li className="text-fg-muted">{t('footer.notConfigured')}</li>}
          </ul>
        </div>
      </div>
      <div className="border-t border-border py-5 text-center text-xs text-fg-muted">
        {t('footer.rights', { year: new Date().getFullYear() })}
      </div>
    </footer>
  );
}

export function PublicLayout() {
  const { t } = useTranslation();
  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-white"
      >
        {t('nav.skipToContent')}
      </a>
      <SiteHeader />
      <main id="main" className="flex-1">
        {/* Lazy pages load inside the layout, so the header and footer stay put. */}
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </main>
      <SiteFooter />
    </div>
  );
}
