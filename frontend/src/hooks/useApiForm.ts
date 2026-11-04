import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError, toApiError } from '@/services/apiClient';

/**
 * Tracks submission state and turns API errors into a translated form-level message plus
 * per-field messages (keyed by the backend field name).
 */
export function useApiForm() {
  const { t } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const translate = useCallback(
    (err: ApiError) => t(`errors.${err.code}`, { defaultValue: t('errors.INTERNAL_ERROR') }),
    [t],
  );

  const run = useCallback(
    async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
      setSubmitting(true);
      setFormError(null);
      setFieldErrors({});
      try {
        return await fn();
      } catch (e) {
        const err = toApiError(e);
        const fields: Record<string, string> = {};
        for (const f of err.fieldErrors) {
          fields[f.field] = t(`validation.${f.code}`, { defaultValue: t('validation.invalid') });
        }
        setFieldErrors(fields);
        if (err.code !== 'VALIDATION_FAILED' || err.fieldErrors.length === 0) setFormError(translate(err));
        throw err;
      } finally {
        setSubmitting(false);
      }
    },
    [t, translate],
  );

  /** Like run, but swallows the error after it has been shown. */
  const submit = useCallback(
    async <T,>(fn: () => Promise<T>) => {
      try {
        return await run(fn);
      } catch {
        return undefined;
      }
    },
    [run],
  );

  return { submitting, formError, fieldErrors, setFieldErrors, setFormError, run, submit };
}

/** Client-side mirror of the backend password policy, for instant feedback. */
export function passwordProblem(pw: string): string | null {
  if (pw.length < 8) return 'PASSWORD_TOO_SHORT';
  if (new TextEncoder().encode(pw).length > 72) return 'PASSWORD_TOO_LONG';
  if (!/\p{L}/u.test(pw) || !/\p{N}/u.test(pw)) return 'PASSWORD_TOO_WEAK';
  return null;
}
