import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { buttonClasses } from '@/components/ui/Button';
import { LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { AuthCard } from '@/features/auth/AuthCard';
import { toApiError } from '@/services/apiClient';
import { accountApi, authApi } from '@/services/authApi';

export function VerifyEmailPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token');
  const { status: authStatus, setUser } = useAuth();
  const [state, setState] = useState<'working' | 'done' | 'error'>(token ? 'working' : 'error');
  const [errorCode, setErrorCode] = useState(token ? '' : 'INVALID_TOKEN');
  const started = useRef(false);

  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    authApi
      .verifyEmail(token)
      .then(() => setState('done'))
      .catch((e) => {
        setErrorCode(toApiError(e).code);
        setState('error');
      });
  }, [token]);

  // Refresh the cached account so the "verified" badge updates without a reload.
  useEffect(() => {
    if (state === 'done' && authStatus === 'authenticated') accountApi.me().then(setUser).catch(() => undefined);
  }, [state, authStatus, setUser]);

  return (
    <AuthCard title={t('auth.verifyEmailTitle')}>
      {state === 'working' && <LoadingState />}
      {state === 'done' && <Alert tone="success">{t('auth.emailVerified')}</Alert>}
      {state === 'error' && <Alert tone="error">{t(`errors.${errorCode}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>}
      {state !== 'working' && (
        <Link to={authStatus === 'authenticated' ? '/account' : '/signin'} className={buttonClasses('primary', 'md', 'mt-6 w-full')}>
          {authStatus === 'authenticated' ? t('account.title') : t('nav.signIn')}
        </Link>
      )}
    </AuthCard>
  );
}
