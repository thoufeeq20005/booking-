import { useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { useAuth } from '@/contexts/AuthContext';
import { ContactSection } from '@/features/account/ContactSection';
import { PasswordSection } from '@/features/account/PasswordSection';
import { TwoFactorSection } from '@/features/account/TwoFactorSection';
import { NotificationPreferencesSection } from '@/features/account/NotificationPreferencesSection';
import { useApiForm } from '@/hooks/useApiForm';
import { session } from '@/services/apiClient';
import { accountApi } from '@/services/authApi';

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{title}</h2>
      <div className="mt-4">{children}</div>
    </Card>
  );
}

function ProfileSection() {
  const { t } = useTranslation();
  const { user, setUser } = useAuth();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [saved, setSaved] = useState(false);
  const form = useApiForm();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaved(false);
    const u = await form.submit(() => accountApi.update({ fullName }));
    if (u) {
      setUser(u);
      setSaved(true);
    }
  };

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {saved && <Alert tone="success">{t('account.saved')}</Alert>}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <TextField label={t('auth.fullName')} value={fullName} maxLength={120} autoComplete="name"
        onChange={(e) => setFullName(e.target.value)} error={form.fieldErrors.fullName} />
      <p className="text-xs text-fg-muted">{t('account.languageHint')}</p>
      <Button type="submit" variant="secondary" loading={form.submitting}
        disabled={!fullName.trim() || fullName.trim() === user?.fullName}>
        {t('common.save')}
      </Button>
    </form>
  );
}

export function AccountPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const form = useApiForm();

  if (!user) return null;

  const logoutEverywhere = async () => {
    const ok = await form.submit(async () => {
      await accountApi.logoutAll();
      return true;
    });
    if (ok) {
      session.clear();
      navigate('/signin', { replace: true });
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('account.title')}</h1>
        <p className="mt-1 text-fg-muted">{t('account.greeting', { name: user.fullName })}</p>
      </div>
      <Section title={t('account.profile')}>
        <ProfileSection />
      </Section>
      <Section title={t('account.contact')}>
        <ContactSection />
      </Section>
      <Section title={t('account.password')}>
        <PasswordSection />
      </Section>
      <Section title={t('account.twoFactor')}>
        <TwoFactorSection />
      </Section>
      <Section title={t('notify.prefsTitle')}>
        <NotificationPreferencesSection />
      </Section>
      <Section title={t('account.sessions')}>
        <p className="mb-4 text-sm text-fg-muted">{t('account.logoutAllDescription')}</p>
        {form.formError && <Alert tone="error" className="mb-4">{form.formError}</Alert>}
        <Button variant="danger" onClick={() => void logoutEverywhere()} loading={form.submitting}>
          {t('account.logoutAll')}
        </Button>
      </Section>
    </div>
  );
}
