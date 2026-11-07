import { useEffect, useRef, useState, type ClipboardEvent, type FormEvent, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { MailCheck } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { useApiForm } from '@/hooks/useApiForm';
import type { Session } from '@/services/apiClient';
import { authApi, type OtpSent } from '@/services/authApi';
import { DemoCodeNotice } from './DemoCodeNotice';

/** a****@gmail.com: enough to recognise the inbox without showing the whole address. */
export function maskEmail(email: string) {
  const [name = '', domain = ''] = email.split('@');
  return `${name.slice(0, 1)}${'*'.repeat(Math.max(2, Math.min(6, name.length - 1)))}@${domain}`;
}

/** Six one-digit boxes; typing moves forward, Backspace moves back, pasting a code fills them all. */
function CodeBoxes({ value, onChange, label }: { value: string; onChange: (v: string) => void; label: string }) {
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const digits = Array.from({ length: 6 }, (_, i) => value[i] ?? '');
  const set = (i: number, d: string) => {
    const next = digits.map((x, j) => (j === i ? d : x)).join('').slice(0, 6);
    onChange(next);
    if (d && i < 5) refs.current[i + 1]?.focus();
  };
  const onKey = (i: number) => (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[i] && i > 0) refs.current[i - 1]?.focus();
  };
  const onPaste = (e: ClipboardEvent<HTMLInputElement>) => {
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (pasted) {
      e.preventDefault();
      onChange(pasted);
      refs.current[Math.min(pasted.length, 5)]?.focus();
    }
  };
  return (
    <div role="group" aria-label={label} className="flex justify-center gap-2">
      {digits.map((d, i) => (
        <input key={i} ref={(el) => { refs.current[i] = el; }} value={d} inputMode="numeric" maxLength={1}
          autoComplete={i === 0 ? 'one-time-code' : 'off'} aria-label={`${label} ${i + 1}`} autoFocus={i === 0}
          onChange={(e) => set(i, e.target.value.replace(/\D/g, '').slice(-1))} onKeyDown={onKey(i)} onPaste={onPaste}
          className="h-14 w-12 rounded-xl border border-border bg-surface text-center text-2xl font-bold focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30" />
      ))}
    </div>
  );
}

/** Second step of password sign-in: the 6-digit code emailed to the account. */
export function PasswordEmailCodeStep({ email, challengeToken, sent, onDone, onBack }: {
  email: string; challengeToken: string; sent?: OtpSent; onDone: (s: Session) => void; onBack: () => void;
}) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [code, setCode] = useState('');
  const [demoCode, setDemoCode] = useState(sent?.demoCode ?? null);
  const [cooldown, setCooldown] = useState(sent?.resendAfterSeconds ?? 60);
  const [resent, setResent] = useState(false);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = window.setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => window.clearTimeout(id);
  }, [cooldown]);

  const verify = async (e?: FormEvent) => {
    e?.preventDefault();
    if (code.length !== 6) return;
    const s = await form.submit(() => authApi.completeLoginCode(challengeToken, code));
    if (s) onDone(s);
  };
  const resend = async () => {
    const res = await form.submit(() => authApi.resendLoginCode(challengeToken));
    if (res) {
      setCooldown(res.resendAfterSeconds);
      setDemoCode(res.demoCode ?? null);
      setCode('');
      setResent(true);
    }
  };

  return (
    <form onSubmit={(e) => void verify(e)} className="space-y-5" noValidate>
      <div className="flex items-start gap-3 rounded-xl bg-brand-50 p-3.5 text-sm text-brand-900">
        <MailCheck className="mt-0.5 h-5 w-5 shrink-0 text-brand-600" aria-hidden="true" />
        <p>{t('auth.loginCodeSent', { email: maskEmail(email), minutes: Math.round((sent?.expiresInSeconds ?? 300) / 60) })}</p>
      </div>
      {resent && !form.formError && !demoCode && <Alert tone="success">{t('auth.codeEmailed')}</Alert>}
      {demoCode && <DemoCodeNotice code={demoCode} onUse={() => setCode(demoCode)} />}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <CodeBoxes value={code} onChange={setCode} label={t('auth.emailCodeLabel')} />
      <Button type="submit" className="w-full" loading={form.submitting} disabled={code.length !== 6}>{t('auth.verify')}</Button>
      <div className="flex items-center justify-between text-sm">
        <button type="button" onClick={onBack} className="font-medium text-fg-muted hover:text-fg">{t('auth.differentAccount')}</button>
        {cooldown > 0
          ? <span className="text-fg-muted">{t('auth.resendIn', { seconds: cooldown })}</span>
          : <button type="button" onClick={() => void resend()} className="font-semibold text-brand-700 hover:underline">{t('auth.resendCode')}</button>}
      </div>
    </form>
  );
}
