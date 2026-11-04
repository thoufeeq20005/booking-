import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Eye, FileText, Trash2, Upload } from 'lucide-react';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { useApiForm } from '@/hooks/useApiForm';
import { orgKeys, ownerOrganizationApi as api, type DocumentSlot, type OrganizationDetail } from '@/services/organizationApi';
import { formatBytes, openBlob, StatusBadge, typeLabels, useLocalized } from './shared';

function SlotRow({ orgId, slot, editable }: { orgId: number; slot: DocumentSlot; editable: boolean }) {
  const { t } = useTranslation();
  const pick = useLocalized();
  const qc = useQueryClient();
  const form = useApiForm();
  const inputId = useId();
  const [busy, setBusy] = useState(false);
  const { requirement: req, document: doc } = slot;
  const refresh = () => qc.invalidateQueries({ queryKey: orgKeys.detail(orgId) });

  const onFile = async (file?: File) => {
    if (!file) return;
    if (file.size > req.maxSizeBytes) {
      form.setFormError(t('errors.PAYLOAD_TOO_LARGE'));
      return;
    }
    const ok = await form.submit(() => api.uploadDocument(orgId, req.id, file));
    if (ok) void refresh();
  };
  const view = async () => {
    setBusy(true);
    const blob = await form.submit(() => api.documentFile(orgId, req.id));
    setBusy(false);
    if (blob) openBlob(blob);
  };
  const remove = async () => {
    const ok = await form.submit(() => api.deleteDocument(orgId, req.id).then(() => true));
    if (ok) void refresh();
  };

  return (
    <li className="space-y-2 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium">
            {pick(req.label)}{' '}
            <span className="text-xs font-normal text-fg-muted">
              {req.mandatory ? t('business.required') : t('business.optional')}
            </span>
          </p>
          {req.description && <p className="text-sm text-fg-muted">{pick(req.description)}</p>}
          <p className="text-xs text-fg-muted">
            {t('business.fileRules', { types: typeLabels(req.allowedTypes), size: formatBytes(req.maxSizeBytes) })}
          </p>
        </div>
        {doc && <StatusBadge status={doc.status} kind="doc" />}
      </div>

      {doc && (
        <div className="flex flex-wrap items-center gap-2 rounded-lg bg-surface-muted px-3 py-2 text-sm">
          <FileText className="h-4 w-4 text-brand-600" aria-hidden="true" />
          <span className="min-w-0 flex-1 truncate">{doc.fileName}</span>
          <span className="text-xs text-fg-muted">{formatBytes(doc.sizeBytes)}</span>
          <Button variant="ghost" size="sm" onClick={() => void view()} loading={busy}>
            <Eye className="h-4 w-4" aria-hidden="true" />
            {t('business.view')}
          </Button>
          {editable && (
            <Button variant="ghost" size="sm" aria-label={t('business.removeDocument')} onClick={() => void remove()}>
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </Button>
          )}
        </div>
      )}
      {doc?.status === 'REJECTED' && doc.reviewerNote && (
        <Alert tone="error">{t('business.reviewerSaid', { note: doc.reviewerNote })}</Alert>
      )}
      {form.formError && <Alert tone="error">{form.formError}</Alert>}

      {editable && (
        <div>
          <input
            id={inputId}
            type="file"
            className="sr-only"
            accept={req.allowedTypes.join(',')}
            onChange={(e) => {
              void onFile(e.target.files?.[0]);
              e.target.value = '';
            }}
          />
          <label htmlFor={inputId} className={clsx(buttonClasses('secondary', 'sm'), 'cursor-pointer')}>
            {form.submitting ? <Spinner size="sm" /> : <Upload className="h-4 w-4" aria-hidden="true" />}
            {doc ? t('business.replaceFile') : t('business.uploadFile')}
          </label>
        </div>
      )}
    </li>
  );
}

export function DocumentsSection({ org }: { org: OrganizationDetail }) {
  const { t } = useTranslation();
  const editable = org.editable.identity;
  return (
    <div className="space-y-3">
      <p className="text-sm text-fg-muted">{t('business.documentsIntro')}</p>
      {!editable && <Alert>{t('business.documentsLocked')}</Alert>}
      <ul className="divide-y divide-border rounded-2xl border border-border bg-surface">
        {org.documents.map((slot) => (
          <SlotRow key={slot.requirement.id} orgId={org.summary.id} slot={slot} editable={editable} />
        ))}
      </ul>
    </div>
  );
}
