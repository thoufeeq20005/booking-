import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { ImagePlus, Trash2 } from 'lucide-react';
import clsx from 'clsx';
import { Button, buttonClasses } from '@/components/ui/Button';
import { useApiForm } from '@/hooks/useApiForm';
import { fileUrl } from './shared';

interface PhotoFieldProps<T> {
  url?: string;
  alt: string;
  editable: boolean;
  /** "wide" for shop cover photos, "thumb" for small service photos. */
  shape: 'wide' | 'thumb';
  hint?: string;
  upload: (file: File) => Promise<T>;
  remove: () => Promise<T>;
  onChange: (result: T) => void;
}

/** Shows a public photo with upload / replace / remove controls for the owner. */
export function PhotoField<T>({ url, alt, editable, shape, hint, upload, remove, onChange }: PhotoFieldProps<T>) {
  const { t } = useTranslation();
  const form = useApiForm();
  const inputId = useId();
  if (!editable && !url) return null;
  const box = shape === 'wide' ? 'aspect-[16/9] w-full max-w-md rounded-2xl' : 'h-16 w-24 rounded-xl';
  const apply = (r?: T) => {
    if (r !== undefined) onChange(r);
  };

  return (
    <div className={clsx('flex gap-4', shape === 'wide' ? 'flex-col' : 'items-center')}>
      {url ? (
        <img src={fileUrl(url)} alt={alt} className={clsx(box, 'border border-border object-cover')} />
      ) : (
        <span className={clsx(box, 'flex items-center justify-center border border-dashed border-border bg-surface-muted text-fg-muted')}>
          <ImagePlus className="h-6 w-6" aria-hidden="true" />
        </span>
      )}
      {editable && (
        <div className="space-y-1">
          <div className="flex gap-2">
            <input id={inputId} type="file" accept="image/png,image/jpeg,image/webp" className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0];
                e.target.value = '';
                if (f) void form.submit(() => upload(f)).then(apply);
              }} />
            <label htmlFor={inputId} className={clsx(buttonClasses('secondary', 'sm'), 'cursor-pointer')}>
              {url ? t('photo.change') : t('photo.upload')}
            </label>
            {url && (
              <Button variant="ghost" size="sm" aria-label={t('photo.remove')}
                onClick={() => void form.submit(remove).then(apply)}>
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </Button>
            )}
          </div>
          {hint && <p className="text-xs text-fg-muted">{hint}</p>}
          {form.formError && <p className="text-xs text-danger">{form.formError}</p>}
        </div>
      )}
    </div>
  );
}
