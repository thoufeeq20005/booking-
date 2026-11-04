import { forwardRef, useId, useState, type InputHTMLAttributes, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Eye, EyeOff } from 'lucide-react';
import clsx from 'clsx';

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
}

export const inputClasses = (invalid: boolean) =>
  clsx(
    'h-11 w-full rounded-xl border bg-surface px-3.5 text-sm text-fg transition placeholder:text-fg-muted/70',
    'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30',
    invalid ? 'border-danger' : 'border-border hover:border-brand-300',
  );

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, type = 'text', className, id: idProp, ...rest },
  ref,
) {
  const { t } = useTranslation();
  const autoId = useId();
  const id = idProp ?? autoId;
  const [reveal, setReveal] = useState(false);
  const isPassword = type === 'password';
  const describedBy = [error ? `${id}-error` : null, hint ? `${id}-hint` : null].filter(Boolean).join(' ');

  return (
    <div className={clsx('space-y-1.5', className)}>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <div className="relative">
        <input
          ref={ref}
          id={id}
          type={isPassword && reveal ? 'text' : type}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy || undefined}
          className={clsx(inputClasses(Boolean(error)), isPassword && 'pr-11')}
          {...rest}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setReveal((v) => !v)}
            aria-label={reveal ? t('forms.hidePassword') : t('forms.showPassword')}
            className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-fg-muted hover:text-fg"
          >
            {reveal ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        )}
      </div>
      {hint && !error && (
        <p id={`${id}-hint`} className="text-xs text-fg-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-xs font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  );
});
