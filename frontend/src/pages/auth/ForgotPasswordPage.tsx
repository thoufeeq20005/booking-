import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { AuthCard } from '@/features/auth/AuthCard';
import { useApiForm } from '@/hooks/useApiForm';
import { authApi } from '@/services/authApi';

export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const form = useApiForm();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await form.run(() => authApi.forgotPassword(email));
      setSent(true);
    } catch {
      /* shown by the form */
    }
  };

  return (
    <AuthCard
      title={t('auth.forgotTitle')}
      subtitle={t('auth.forgotSubtitle')}
      footer={
        <Link to="/signin" className="font-semibold text-brand-700">
          {t('auth.backToSignIn')}
        </Link>
      }
    >
      {sent ? (
        // Same message whether or not the account exists.
        <Alert tone="success">{t('auth.forgotSent')}</Alert>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {form.formError && <Alert tone="error">{form.formError}</Alert>}
          <TextField
            label={t('auth.email')}
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={form.fieldErrors.email}
            required
          />
          <Button type="submit" className="w-full" loading={form.submitting} disabled={!email}>
            {t('auth.sendResetLink')}
          </Button>
        </form>
      )}
    </AuthCard>
  );
}
