import { useTranslation } from 'react-i18next';
import { BadgeCheck, Mail } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';

/** The account's email address. Verification happens right after sign-up (or by signing in with an email code). */
export function ContactSection() {
  const { t } = useTranslation();
  const { user } = useAuth();
  if (!user) return null;

  return (
    <div className="flex items-center gap-3">
      <Mail className="h-5 w-5 text-fg-muted" aria-hidden="true" />
      <div>
        <p className="text-sm font-medium">{user.email ?? t('account.noEmail')}</p>
        {user.email && user.emailVerified && (
          <span className="inline-flex items-center gap-1 text-xs font-semibold text-success">
            <BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />
            {t('account.verified')}
          </span>
        )}
      </div>
    </div>
  );
}
