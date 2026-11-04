import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useAuth } from '@/contexts/AuthContext';
import { AuthCard, Divider } from '@/features/auth/AuthCard';
import { GoogleSignInButton, googleSignInAvailable } from '@/features/auth/GoogleSignInButton';
import { PhoneOtpForm } from '@/features/auth/PhoneOtpForm';
import { TwoFactorStep } from '@/features/auth/TwoFactorStep';
import { useApiForm } from '@/hooks/useApiForm';
import { safeNext } from '@/routes/RequireAuth';
import type { Session } from '@/services/apiClient';
import { authApi } from '@/services/authApi';

type Method = 'email' | 'phone';

export function SignInPage() {
  const { t } = useTranslation();
  const { status, signIn } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const [method, setMethod] = useState<Method>('email');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [challenge, setChallenge] = useState<string | null>(null);
  const form = useApiForm();

  if (status === 'authenticated' && !challenge) return <Navigate to={next} replace />;

  const handleSession = (s: Session) => {
    if (s.status === 'TWO_FACTOR_REQUIRED' && s.challengeToken) {
      setChallenge(s.challengeToken);
      return;
    }
    signIn(s);
    navigate(next, { replace: true });
  };

  const onEmailSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const s = await form.submit(() => authApi.login(email, password));
    if (s) handleSession(s);
  };

  const onGoogle = async (idToken: string) => {
    const s = await form.submit(() => authApi.google(idToken));
    if (s) handleSession(s);
  };

  if (challenge) {
    return (
      <AuthCard title={t('auth.twoFactorTitle')}>
        <TwoFactorStep challengeToken={challenge} onDone={handleSession} />
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title={t('auth.signInTitle')}
      subtitle={t('auth.signInSubtitle')}
      footer={
        <>
          {t('auth.noAccount')}{' '}
          <Link to={`/register${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-semibold text-brand-700">
            {t('nav.register')}
          </Link>
        </>
      }
    >
      <div role="tablist" aria-label={t('auth.signInMethod')} className="mb-6 grid grid-cols-2 rounded-xl bg-surface-muted p-1 text-sm font-medium">
        {(['email', 'phone'] as const).map((m) => (
          <button
            key={m}
            role="tab"
            type="button"
            aria-selected={method === m}
            onClick={() => {
              setMethod(m);
              form.setFormError(null);
            }}
            className={clsx('rounded-lg py-2 transition', method === m ? 'bg-surface text-fg shadow-sm' : 'text-fg-muted hover:text-fg')}
          >
            {m === 'email' ? t('auth.withEmail') : t('auth.withMobile')}
          </button>
        ))}
      </div>

      {/* Errors from email sign-in and from Google; the phone form shows its own. */}
      {form.formError && <Alert tone="error" className="mb-4">{form.formError}</Alert>}
      {method === 'email' ? (
        <form onSubmit={onEmailSubmit} className="space-y-4" noValidate>
          <TextField
            label={t('auth.email')}
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={form.fieldErrors.email}
            required
          />
          <TextField
            label={t('auth.password')}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={form.fieldErrors.password}
            required
          />
          <div className="flex justify-end">
            <Link to="/forgot-password" className="text-sm font-medium text-brand-700">
              {t('auth.forgotPassword')}
            </Link>
          </div>
          <Button type="submit" className="w-full" loading={form.submitting} disabled={!email || !password}>
            {t('nav.signIn')}
          </Button>
        </form>
      ) : (
        <PhoneOtpForm onSession={handleSession} />
      )}

      {googleSignInAvailable && (
        <>
          <Divider label={t('auth.or')} />
          <GoogleSignInButton onCredential={(token) => void onGoogle(token)} />
        </>
      )}
    </AuthCard>
  );
}
