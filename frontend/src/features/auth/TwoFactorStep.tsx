import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { ShieldCheck } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import type { Session } from '@/services/apiClient';
import { authApi } from '@/services/authApi';

export function TwoFactorStep({ challengeToken, onDone }: { challengeToken: string; onDone: (s: Session) => void }) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const form = useApiForm();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const s = await form.submit(() => authApi.completeTwoFactor(challengeToken, code));
    if (s) onDone(s);
  };

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <div className="flex items-start gap-3 rounded-xl bg-brand-50 p-3.5 text-sm text-brand-900">
        <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-brand-600" aria-hidden="true" />
        <p>{t('auth.twoFactorPrompt')}</p>
      </div>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <TextField
        label={t('auth.authenticatorCode')}
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        inputMode="numeric"
        autoComplete="one-time-code"
        autoFocus
        required
      />
      <Button type="submit" className="w-full" loading={form.submitting} disabled={code.length !== 6}>
        {t('auth.verify')}
      </Button>
    </form>
  );
}
