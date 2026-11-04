import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import { toApiError, type Session } from '@/services/apiClient';
import { authApi } from '@/services/authApi';

/** Two steps: send a code to the number, then sign in with it (asking for a name only for new numbers). */
export function PhoneOtpForm({ onSession }: { onSession: (s: Session) => void }) {
  const { t, i18n } = useTranslation();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [fullName, setFullName] = useState('');
  const [sent, setSent] = useState(false);
  const [needsName, setNeedsName] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const form = useApiForm();

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = window.setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => window.clearTimeout(id);
  }, [cooldown]);

  const send = async () => {
    const res = await form.submit(() => authApi.sendOtp(phone));
    if (res) {
      setSent(true);
      setCooldown(res.resendAfterSeconds);
    }
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!sent) return send();
    try {
      const s = await form.run(() =>
        authApi.otpLogin({
          phone,
          code,
          fullName: needsName ? fullName : undefined,
          preferredLanguage: i18n.resolvedLanguage,
        }),
      );
      if (s) onSession(s);
    } catch (err) {
      if (toApiError(err).code === 'FULL_NAME_REQUIRED') {
        setNeedsName(true);
        form.setFormError(null);
      }
    }
  };

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {sent && !form.formError && <Alert tone="success">{t('auth.codeSent')}</Alert>}
      <TextField
        label={t('auth.mobileNumber')}
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        type="tel"
        inputMode="tel"
        autoComplete="tel-national"
        placeholder="98765 43210"
        hint={t('auth.mobileHint')}
        error={form.fieldErrors.phone}
        disabled={sent}
        required
      />
      {sent && (
        <>
          <TextField
            label={t('auth.smsCode')}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            required
          />
          {needsName && (
            <TextField
              label={t('auth.fullName')}
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              autoComplete="name"
              hint={t('auth.newAccountHint')}
              required
            />
          )}
        </>
      )}
      <Button
        type="submit"
        className="w-full"
        loading={form.submitting}
        disabled={sent ? code.length !== 6 || (needsName && !fullName.trim()) : !phone.trim()}
      >
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
              setNeedsName(false);
            }}
          >
            {t('auth.changeNumber')}
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
