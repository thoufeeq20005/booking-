import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SelectField, TextAreaField } from '@/components/ui/FormFields';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import {
  usePublicCategories,
  type ConfirmationMode,
  type OrganizationDetail,
  type OrganizationInput,
} from '@/services/organizationApi';
import { useLocalized } from './shared';

interface Props {
  initial?: OrganizationDetail;
  /** After approval the name and category can't change. */
  identityLocked?: boolean;
  submitLabel: string;
  onSubmit: (input: OrganizationInput) => Promise<unknown>;
  onSaved?: () => void;
}

export function OrganizationForm({ initial, identityLocked = false, submitLabel, onSubmit, onSaved }: Props) {
  const { t } = useTranslation();
  const pick = useLocalized();
  const categories = usePublicCategories();
  const form = useApiForm();
  const [saved, setSaved] = useState(false);
  const [values, setValues] = useState({
    subcategoryId: initial ? String(initial.summary.subcategoryId) : '',
    name: initial?.summary.name ?? '',
    description: initial?.description ?? '',
    contactEmail: initial?.contactEmail ?? '',
    contactPhone: initial?.contactPhone ?? '',
    website: initial?.website ?? '',
    gstin: initial?.gstin ?? '',
    confirmationMode: (initial?.confirmationMode ?? 'INSTANT') as ConfirmationMode,
  });
  const set = (k: keyof typeof values) => (v: string) => {
    setSaved(false);
    setValues((s) => ({ ...s, [k]: v }));
  };

  if (categories.isLoading) return <LoadingState />;
  if (categories.error) return <ErrorState error={categories.error} onRetry={() => void categories.refetch()} />;

  const noContact = !values.contactEmail.trim() && !values.contactPhone.trim();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!values.subcategoryId || !values.name.trim()) return;
    const ok = await form.submit(() =>
      onSubmit({
        subcategoryId: Number(values.subcategoryId),
        name: values.name.trim(),
        description: values.description.trim() || undefined,
        contactEmail: values.contactEmail.trim() || undefined,
        contactPhone: values.contactPhone.trim() || undefined,
        website: values.website.trim() || undefined,
        gstin: values.gstin.trim() || undefined,
        confirmationMode: values.confirmationMode,
      }),
    );
    if (ok !== undefined) {
      setSaved(true);
      onSaved?.();
    }
  };

  // The current subcategory stays selectable even if an admin has since hidden it.
  const currentSub = initial?.summary.subcategoryId;
  const tree = categories.data ?? [];
  const knownIds = new Set(tree.flatMap((c) => c.subcategories.map((s) => s.id)));

  return (
    <form onSubmit={submit} className="space-y-5" noValidate>
      {saved && <Alert tone="success">{t('account.saved')}</Alert>}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {identityLocked && <Alert>{t('business.identityLocked')}</Alert>}

      <SelectField
        label={t('business.category')}
        value={values.subcategoryId}
        onChange={(e) => set('subcategoryId')(e.target.value)}
        disabled={identityLocked}
        required
        error={form.fieldErrors.subcategoryId}
      >
        <option value="">{t('business.chooseCategory')}</option>
        {currentSub && !knownIds.has(currentSub) && (
          <option value={currentSub}>{pick(initial?.summary.subcategoryName)}</option>
        )}
        {tree.map((c) => (
          <optgroup key={c.id} label={pick(c.name)}>
            {c.subcategories.map((s) => (
              <option key={s.id} value={s.id}>
                {pick(s.name)}
              </option>
            ))}
          </optgroup>
        ))}
      </SelectField>

      <TextField label={t('business.name')} value={values.name} maxLength={150} required disabled={identityLocked}
        onChange={(e) => set('name')(e.target.value)} error={form.fieldErrors.name} />
      <TextAreaField label={t('business.description')} value={values.description} maxLength={2000}
        hint={t('business.descriptionHint')} onChange={(e) => set('description')(e.target.value)}
        error={form.fieldErrors.description} />

      <div className="grid gap-5 sm:grid-cols-2">
        <TextField label={t('business.contactPhone')} type="tel" inputMode="tel" autoComplete="tel"
          value={values.contactPhone} onChange={(e) => set('contactPhone')(e.target.value)}
          error={form.fieldErrors.contactPhone} />
        <TextField label={t('business.contactEmail')} type="email" autoComplete="email" value={values.contactEmail}
          onChange={(e) => set('contactEmail')(e.target.value)} error={form.fieldErrors.contactEmail} />
      </div>
      {noContact && <p className="-mt-3 text-xs text-fg-muted">{t('business.contactRequired')}</p>}

      <div className="grid gap-5 sm:grid-cols-2">
        <TextField label={t('business.website')} value={values.website} placeholder="www.example.in" maxLength={255}
          onChange={(e) => set('website')(e.target.value)}
          error={form.fieldErrors.website} />
        <TextField label={t('business.gstin')} value={values.gstin} maxLength={15} hint={t('business.optional')}
          onChange={(e) => set('gstin')(e.target.value.toUpperCase())}
          error={form.fieldErrors.gstin} />
      </div>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">{t('business.confirmationMode')}</legend>
        {(['INSTANT', 'APPROVAL'] as const).map((mode) => (
          <label key={mode} className="flex cursor-pointer items-start gap-2.5 rounded-xl border border-border p-3 text-sm has-[:checked]:border-brand-500">
            <input type="radio" name="confirmationMode" value={mode} checked={values.confirmationMode === mode}
              onChange={() => set('confirmationMode')(mode)} className="mt-0.5 text-brand-600" />
            <span>
              <span className="font-medium">{t(`business.mode.${mode}`)}</span>
              <span className="block text-fg-muted">{t(`business.mode.${mode}_hint`)}</span>
            </span>
          </label>
        ))}
      </fieldset>

      <Button type="submit" loading={form.submitting} disabled={!values.subcategoryId || !values.name.trim()}>
        {submitLabel}
      </Button>
    </form>
  );
}
