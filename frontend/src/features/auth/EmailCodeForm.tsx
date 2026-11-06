import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import type { Session } from '@/services/apiClient';
import { authApi } from '@/services/authApi';
import { DemoCodeNotice } from './DemoCodeNotice';

/** Passwordless sign-in for existing accounts: email → 6-digit code from the inbox → signed in. */
export function EmailCodeForm({ onSession }: { onSession: (s: Session) => void }) {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState(false);
  const [demoCode, setDemoCode] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);
  const form = useApiForm();

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = window.setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => window.clearTimeout(id);
  }, [cooldown]);

  const send = async () => {
    const res = await form.submit(() => authApi.sendEmailCode(email.trim()));
    if (res) {
      setSent(true);
      setCooldown(res.resendAfterSeconds);
      setDemoCode(res.demoCode ?? null);
    }
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!sent) return send();
    const s = await form.submit(() => authApi.emailCodeLogin(email.trim(), code));
    if (s) onSession(s);
  };

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {sent && !form.formError && !demoCode && <Alert tone="success">{t('auth.codeEmailed')}</Alert>}
      {sent && demoCode && <DemoCodeNotice code={demoCode} onUse={() => setCode(demoCode)} />}
      <TextField
        label={t('auth.email')}
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        hint={sent ? undefined : t('auth.emailCodeHint')}
        error={form.fieldErrors.email}
        disabled={sent}
        required
      />
      {sent && (
        <TextField
          label={t('auth.emailCode')}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          required
        />
      )}
      <Button type="submit" className="w-full" loading={form.submitting} disabled={sent ? code.length !== 6 : !email.trim()}>
        {sent ? t('auth.verifyAndContinue') : t('auth.sendCode')}
      </Button>
      {sent && (
        <div className="flex items-center justify-between text-sm">
          <button
            type="button"
            className="text-fg-muted hover:text-fg"
            onClick={() => {
              setSent(false);
              setCode('');
              setDemoCode(null);
            }}
          >
            {t('auth.changeEmail')}
          </button>
          <button
            type="button"
            className="font-medium text-brand-700 disabled:text-fg-muted"
            disabled={cooldown > 0 || form.submitting}
            onClick={() => void send()}
          >
            {cooldown > 0 ? t('auth.resendIn', { seconds: cooldown }) : t('auth.resendCode')}
          </button>
        </div>
      )}
    </form>
  );
}
