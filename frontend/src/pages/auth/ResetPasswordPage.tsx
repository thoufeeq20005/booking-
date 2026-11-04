import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { AuthCard } from '@/features/auth/AuthCard';
import { passwordProblem, useApiForm } from '@/hooks/useApiForm';
import { authApi } from '@/services/authApi';

export function ResetPasswordPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState(false);
  const [done, setDone] = useState(false);
  const form = useApiForm();

  const problem = touched ? passwordProblem(password) : null;
  const mismatch = touched && confirm.length > 0 && confirm !== password;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (passwordProblem(password) || confirm !== password) return;
    try {
      await form.run(() => authApi.resetPassword(token, password));
      setDone(true);
    } catch {
      /* shown by the form */
    }
  };

  if (!token) {
    return (
      <AuthCard title={t('auth.resetTitle')}>
        <Alert tone="error">{t('errors.INVALID_TOKEN')}</Alert>
      </AuthCard>
    );
  }

  return (
    <AuthCard title={t('auth.resetTitle')}>
      {done ? (
        <>
          <Alert tone="success">{t('auth.resetDone')}</Alert>
          <Link to="/signin" className={buttonClasses('primary', 'md', 'mt-6 w-full')}>
            {t('nav.signIn')}
          </Link>
        </>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {form.formError && <Alert tone="error">{form.formError}</Alert>}
          <TextField
            label={t('auth.newPassword')}
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onBlur={() => setTouched(true)}
            hint={t('auth.passwordHint')}
            error={form.fieldErrors.newPassword ?? (problem ? t(`validation.${problem}`) : undefined)}
            required
          />
          <TextField
            label={t('auth.confirmPassword')}
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            error={mismatch ? t('validation.PASSWORD_MISMATCH') : undefined}
            required
          />
          <Button type="submit" className="w-full" loading={form.submitting} disabled={!password || !confirm}>
            {t('auth.setNewPassword')}
          </Button>
        </form>
      )}
    </AuthCard>
  );
}
