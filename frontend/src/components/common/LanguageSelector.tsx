import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Languages } from 'lucide-react';
import { LANGUAGES } from '@/i18n';

export function LanguageSelector() {
  const { t, i18n } = useTranslation();
  const id = useId();
  return (
    <div className="relative inline-flex items-center">
      <label htmlFor={id} className="sr-only">
        {t('header.language')}
      </label>
      <Languages className="pointer-events-none absolute left-3 h-4 w-4 text-fg-muted" aria-hidden="true" />
      <select
        id={id}
        value={i18n.resolvedLanguage}
        onChange={(e) => void i18n.changeLanguage(e.target.value)}
        className="h-10 appearance-none rounded-xl border border-border bg-surface pl-9 pr-3 text-sm font-medium text-fg transition hover:border-brand-400"
      >
        {LANGUAGES.map((l) => (
          <option key={l.code} value={l.code}>
            {l.nativeName}
          </option>
        ))}
      </select>
    </div>
  );
}
