import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { BadgeCheck, Mail, Phone } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { accountApi } from '@/services/authApi';

function VerifiedBadge({ verified }: { verified: boolean }) {
  const { t } = useTranslation();
  return verified ? (
    <span className="inline-flex items-center gap-1 text-xs font-semibold text-success">
      <BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />
      {t('account.verified')}
    </span>
  ) : (
    <span className="text-xs font-semibold text-warning">{t('account.notVerified')}</span>
  );
}

export function ContactSection() {
  const { t } = useTranslation();
  const { user, setUser } = useAuth();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const form = useApiForm();

  if (!user) return null;

  const resend = async () => {
    setMessage(null);
    const ok = await form.submit(async () => {
      await accountApi.resendVerification();
      return true;
    });
    if (ok) setMessage(t('account.verificationResent'));
  };

  const onPhoneSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setMessage(null);
    if (!codeSent) {
      if (await form.submit(() => accountApi.sendPhoneCode(phone))) setCodeSent(true);
      return;
    }
    const u = await form.submit(() => accountApi.verifyPhone(phone, code));
    if (u) {
      setUser(u);
      setCodeSent(false);
      setPhone('');
      setCode('');
      setMessage(t('account.phoneVerified'));
    }
  };

  return (
    <div className="space-y-5">
      {message && <Alert tone="success">{message}</Alert>}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Mail className="h-5 w-5 text-fg-muted" aria-hidden="true" />
          <div>
            <p className="text-sm font-medium">{user.email ?? t('account.noEmail')}</p>
            {user.email && <VerifiedBadge verified={user.emailVerified} />}
          </div>
        </div>
        {user.email && !user.emailVerified && (
          <Button size="sm" variant="secondary" onClick={() => void resend()} loading={form.submitting}>
            {t('account.resendVerification')}
          </Button>
        )}
      </div>

      <div className="flex items-center gap-3">
        <Phone className="h-5 w-5 text-fg-muted" aria-hidden="true" />
        <div>
          <p className="text-sm font-medium">{user.phone ?? t('account.noPhone')}</p>
          {user.phone && <VerifiedBadge verified={user.phoneVerified} />}
        </div>
      </div>

      <form onSubmit={onPhoneSubmit} className="space-y-3 rounded-xl border border-dashed border-border p-4" noValidate>
        <p className="text-sm font-medium">{user.phone ? t('account.changePhone') : t('account.addPhone')}</p>
        <TextField label={t('auth.mobileNumber')} type="tel" value={phone} onChange={(e) => setPhone(e.target.value)}
          disabled={codeSent} error={form.fieldErrors.phone} placeholder="98765 43210" />
        {codeSent && (
          <TextField label={t('auth.smsCode')} value={code} inputMode="numeric" autoComplete="one-time-code"
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} />
        )}
        <Button type="submit" size="sm" loading={form.submitting} disabled={codeSent ? code.length !== 6 : !phone.trim()}>
          {codeSent ? t('auth.verify') : t('auth.sendCode')}
        </Button>
      </form>
    </div>
  );
}
