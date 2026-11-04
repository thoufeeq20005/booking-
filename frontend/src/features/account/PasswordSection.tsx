import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useAuth } from '@/contexts/AuthContext';
import { passwordProblem, useApiForm } from '@/hooks/useApiForm';
import { accountApi } from '@/services/authApi';

export function PasswordSection() {
  const { t } = useTranslation();
  const { user, signIn } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [done, setDone] = useState(false);
  const form = useApiForm();

  if (!user) return null;
  const problem = next ? passwordProblem(next) : null;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (problem) return;
    const s = await form.submit(() =>
      accountApi.changePassword({ currentPassword: user.hasPassword ? current : undefined, newPassword: next }),
    );
    if (s) {
      signIn(s);
      setCurrent('');
      setNext('');
      setDone(true);
    }
  };

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {done && <Alert tone="success">{t('account.passwordChanged')}</Alert>}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {!user.hasPassword && <p className="text-sm text-fg-muted">{t('account.noPasswordYet')}</p>}
      {user.hasPassword && (
        <TextField label={t('account.currentPassword')} type="password" autoComplete="current-password" value={current}
          onChange={(e) => setCurrent(e.target.value)} error={form.fieldErrors.currentPassword} />
      )}
      <TextField label={t('auth.newPassword')} type="password" autoComplete="new-password" value={next}
        onChange={(e) => setNext(e.target.value)} hint={t('auth.passwordHint')}
        error={form.fieldErrors.newPassword ?? (problem ? t(`validation.${problem}`) : undefined)} />
      <Button type="submit" variant="secondary" loading={form.submitting}
        disabled={!next || Boolean(problem) || (user.hasPassword && !current)}>
        {user.hasPassword ? t('account.changePassword') : t('account.setPassword')}
      </Button>
    </form>
  );
}
