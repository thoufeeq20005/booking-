import { useTranslation } from 'react-i18next';
import { MessageSquareText } from 'lucide-react';

/** Development only: no SMS provider is configured, so the API hands back the code and we show it here. */
export function DemoCodeNotice({ code, onUse }: { code: string; onUse?: () => void }) {
  const { t } = useTranslation();
  return (
    <div role="status" className="flex items-start gap-3 rounded-xl border border-brand-200 bg-brand-50 p-3 text-sm">
      <MessageSquareText className="mt-0.5 h-5 w-5 shrink-0 text-brand-700" aria-hidden="true" />
      <div className="flex-1">
        <p className="font-medium text-brand-900">
          {t('auth.demoCode')}{' '}
          <span className="font-mono text-base font-bold tracking-widest" data-testid="demo-code">{code}</span>
        </p>
        <p className="text-xs text-fg-muted">{t('auth.demoCodeHint')}</p>
      </div>
      {onUse && (
        <button type="button" onClick={onUse} className="shrink-0 font-semibold text-brand-700 hover:underline">
          {t('auth.useCode')}
        </button>
      )}
    </div>
  );
}
