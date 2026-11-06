import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useAuth } from '@/contexts/AuthContext';
import { AuthCard } from '@/features/auth/AuthCard';
import { passwordProblem, useApiForm } from '@/hooks/useApiForm';
import { safeNext } from '@/routes/RequireAuth';
import { authApi } from '@/services/authApi';

export function RegisterPage() {
  const { t, i18n } = useTranslation();
  const { status, signIn } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'), '/');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);
  const form = useApiForm();

  if (status === 'authenticated') return <Navigate to={next} replace />;

  const localPasswordError = touched ? passwordProblem(password) : null;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (passwordProblem(password)) return;
    const res = await form.submit(() =>
      authApi.register({ fullName, email, password, preferredLanguage: i18n.resolvedLanguage }),
    );
    if (res) {
      signIn(res.session);
      navigate(next, { replace: true });
    }
  };

  return (
    <AuthCard
      title={t('auth.registerTitle')}
      subtitle={t('auth.registerSubtitle')}
      footer={
        <>
          {t('auth.haveAccount')}{' '}
          <Link to="/signin" className="font-semibold text-brand-700">
            {t('nav.signIn')}
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {form.formError && <Alert tone="error">{form.formError}</Alert>}
        <TextField
          label={t('auth.fullName')}
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          autoComplete="name"
          maxLength={120}
          error={form.fieldErrors.fullName}
          required
        />
        <TextField
          label={t('auth.email')}
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          error={form.fieldErrors.email}
          required
        />
        <TextField
          label={t('auth.password')}
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onBlur={() => setTouched(true)}
          autoComplete="new-password"
          hint={t('auth.passwordHint')}
          error={form.fieldErrors.password ?? (localPasswordError ? t(`validation.${localPasswordError}`) : undefined)}
          required
        />
        <Button type="submit" className="w-full" loading={form.submitting} disabled={!fullName.trim() || !email || !password}>
          {t('nav.register')}
        </Button>
        <p className="text-center text-xs text-fg-muted">{t('auth.businessLater')}</p>
      </form>
    </AuthCard>
  );
}
