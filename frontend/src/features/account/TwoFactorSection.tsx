import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import QRCode from 'qrcode';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { accountApi, type TwoFactorSetup } from '@/services/authApi';

const sixDigits = (v: string) => v.replace(/\D/g, '').slice(0, 6);

export function TwoFactorSection() {
  const { t } = useTranslation();
  const { user, setUser } = useAuth();
  const [setup, setSetup] = useState<TwoFactorSetup | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const form = useApiForm();

  useEffect(() => {
    if (!setup) return setQr(null);
    QRCode.toDataURL(setup.otpauthUri, { margin: 1, width: 200 }).then(setQr).catch(() => setQr(null));
  }, [setup]);

  if (!user) return null;

  const begin = async () => {
    setMessage(null);
    const s = await form.submit(() => accountApi.setupTwoFactor());
    if (s) setSetup(s);
  };

  const enable = async (e: FormEvent) => {
    e.preventDefault();
    const u = await form.submit(() => accountApi.enableTwoFactor(code));
    if (u) {
      setUser(u);
      setSetup(null);
      setCode('');
      setMessage(t('account.twoFactorOn'));
    }
  };

  const disable = async (e: FormEvent) => {
    e.preventDefault();
    const u = await form.submit(() => accountApi.disableTwoFactor(code, user.hasPassword ? password : undefined));
    if (u) {
      setUser(u);
      setCode('');
      setPassword('');
      setMessage(t('account.twoFactorOff'));
    }
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-fg-muted">{t('account.twoFactorDescription')}</p>
      {message && <Alert tone="success">{message}</Alert>}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}

      {user.twoFactorEnabled ? (
        <form onSubmit={disable} className="space-y-4" noValidate>
          <p className="text-sm font-semibold text-success">{t('account.twoFactorActive')}</p>
          <TextField label={t('auth.authenticatorCode')} value={code} onChange={(e) => setCode(sixDigits(e.target.value))}
            inputMode="numeric" autoComplete="one-time-code" />
          {user.hasPassword && (
            <TextField label={t('auth.password')} type="password" value={password} autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)} error={form.fieldErrors.password} />
          )}
          <Button type="submit" variant="secondary" loading={form.submitting} disabled={code.length !== 6}>
            {t('account.turnOffTwoFactor')}
          </Button>
        </form>
      ) : setup ? (
        <form onSubmit={enable} className="space-y-4" noValidate>
          <ol className="list-decimal space-y-1 pl-5 text-sm">
            <li>{t('account.twoFactorStep1')}</li>
            <li>{t('account.twoFactorStep2')}</li>
          </ol>
          <div className="flex flex-col items-center gap-3 rounded-xl border border-border bg-white p-4 sm:flex-row">
            {qr && <img src={qr} alt={t('account.twoFactorQrAlt')} width={160} height={160} />}
            <div className="min-w-0 text-sm text-gray-800">
              <p className="font-medium">{t('account.twoFactorManual')}</p>
              <code className="mt-1 block break-all rounded bg-gray-100 px-2 py-1 font-mono text-xs tracking-wider">{setup.secret}</code>
            </div>
          </div>
          <TextField label={t('auth.authenticatorCode')} value={code} onChange={(e) => setCode(sixDigits(e.target.value))}
            inputMode="numeric" autoComplete="one-time-code" autoFocus />
          <div className="flex gap-2">
            <Button type="submit" loading={form.submitting} disabled={code.length !== 6}>
              {t('account.turnOnTwoFactor')}
            </Button>
            <Button variant="ghost" onClick={() => setSetup(null)}>
              {t('common.cancel')}
            </Button>
          </div>
        </form>
      ) : (
        <Button variant="secondary" onClick={() => void begin()} loading={form.submitting}>
          {t('account.setUpTwoFactor')}
        </Button>
      )}
    </div>
  );
}
