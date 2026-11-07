import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { buttonClasses } from '@/components/ui/Button';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="mx-auto flex max-w-lg flex-col items-center px-4 py-24 text-center">
      <p className="text-6xl font-extrabold text-brand-600">404</p>
      <h1 className="mt-4 text-2xl font-bold">{t('notFound.title')}</h1>
      <p className="mt-2 text-fg-muted">{t('notFound.body')}</p>
      <Link to="/" className={buttonClasses('primary', 'md', 'mt-8')}>
        {t('notFound.goHome')}
      </Link>
    </div>
  );
}
