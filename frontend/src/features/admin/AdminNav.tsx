import { NavLink } from 'react-router';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';

export function AdminNav() {
  const { t } = useTranslation();
  const cls = ({ isActive }: { isActive: boolean }) =>
    clsx('rounded-lg px-3 py-1.5 text-sm font-medium', isActive ? 'bg-brand-50 text-brand-700' : 'text-fg-muted hover:text-fg');
  return (
    <nav aria-label={t('admin.nav')} className="flex flex-wrap gap-1">
      <NavLink to="/admin/organizations" className={cls}>{t('admin.organizations')}</NavLink>
      <NavLink to="/admin/categories" className={cls}>{t('admin.categories')}</NavLink>
    </nav>
  );
}
