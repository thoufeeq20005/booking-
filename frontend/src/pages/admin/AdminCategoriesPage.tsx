import { useState, type FormEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CheckboxField, SelectField } from '@/components/ui/FormFields';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { AdminNav } from '@/features/admin/AdminNav';
import { CATEGORY_ICONS, CategoryIcon, formatBytes, typeLabels, useLocalized } from '@/features/organization/shared';
import { useApiForm } from '@/hooks/useApiForm';
import { LANGUAGES } from '@/i18n';
import {
  adminCategoryApi as api,
  orgKeys,
  type BookingWorkflow,
  type Category,
  type DocumentRequirement,
  type LocalizedText,
  type Subcategory,
} from '@/services/organizationApi';

const WORKFLOWS: BookingWorkflow[] = ['HEALTHCARE', 'SALON', 'EDUCATION', 'GENERIC'];
const DOC_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];
const MB = 1024 * 1024;

function LocalizedInputs({ label, value, onChange, required, error }: {
  label: string; value: LocalizedText; onChange: (v: LocalizedText) => void; required?: boolean; error?: string;
}) {
  const { t } = useTranslation();
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{label}</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {LANGUAGES.map((l) => (
          <TextField key={l.code} label={`${l.nativeName}${l.code === 'en' && required ? ' *' : ''}`} value={value[l.code] ?? ''}
            onChange={(e) => onChange({ ...value, [l.code]: e.target.value })} />
        ))}
      </div>
      {error && <p className="text-xs font-medium text-danger">{error}</p>}
      {required && <p className="text-xs text-fg-muted">{t('admin.englishRequired')}</p>}
    </fieldset>
  );
}

/** Drops empty translations so the API stores only real text. */
const clean = (v: LocalizedText) => Object.fromEntries(Object.entries(v).filter(([, s]) => s.trim()));

function FormActions({ submitting, onCancel }: { submitting: boolean; onCancel: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex gap-2">
      <Button type="submit" loading={submitting}>{t('common.save')}</Button>
      <Button variant="ghost" onClick={onCancel}>{t('common.cancel')}</Button>
    </div>
  );
}

function CategoryForm({ category, onDone }: { category?: Category; onDone: (tree?: Category[]) => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [slug, setSlug] = useState(category?.slug ?? '');
  const [name, setName] = useState<LocalizedText>(category?.name ?? {});
  const [description, setDescription] = useState<LocalizedText>(category?.description ?? {});
  const [icon, setIcon] = useState(category?.icon ?? 'store');
  const [workflow, setWorkflow] = useState<BookingWorkflow>(category?.bookingWorkflow ?? 'GENERIC');
  const [active, setActive] = useState(category?.active ?? true);
  const [sortOrder, setSortOrder] = useState(String(category?.sortOrder ?? 100));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const body = { slug: slug.trim(), name: clean(name), description: clean(description), icon,
      bookingWorkflow: workflow, active, sortOrder: Number(sortOrder) || 0 };
    const tree = await form.submit(() => (category ? api.update(category.id, body) : api.create(body)));
    if (tree) onDone(tree);
  };

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label={t('admin.slug')} value={slug} onChange={(e) => setSlug(e.target.value.toLowerCase())}
          hint={t('admin.slugHint')} error={form.fieldErrors.slug} />
        <SelectField label={t('admin.icon')} value={icon} onChange={(e) => setIcon(e.target.value)}>
          {Object.keys(CATEGORY_ICONS).map((k) => <option key={k} value={k}>{k}</option>)}
        </SelectField>
        <SelectField label={t('admin.workflow')} value={workflow} onChange={(e) => setWorkflow(e.target.value as BookingWorkflow)}
          hint={t('admin.workflowHint')}>
          {WORKFLOWS.map((w) => <option key={w} value={w}>{t(`admin.workflowName.${w}`)}</option>)}
        </SelectField>
      </div>
      <LocalizedInputs label={t('admin.name')} value={name} onChange={setName} required error={form.fieldErrors.name} />
      <LocalizedInputs label={t('admin.description')} value={description} onChange={setDescription} error={form.fieldErrors.description} />
      <div className="flex flex-wrap items-end gap-6">
        <TextField label={t('admin.sortOrder')} type="number" value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} className="w-32" />
        <CheckboxField label={t('admin.active')} checked={active} onChange={setActive} hint={t('admin.activeHint')} />
      </div>
      <FormActions submitting={form.submitting} onCancel={() => onDone()} />
    </form>
  );
}

function SubcategoryForm({ categoryId, sub, onDone }: { categoryId: number; sub?: Subcategory; onDone: (tree?: Category[]) => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [slug, setSlug] = useState(sub?.slug ?? '');
  const [name, setName] = useState<LocalizedText>(sub?.name ?? {});
  const [active, setActive] = useState(sub?.active ?? true);
  const [sortOrder, setSortOrder] = useState(String(sub?.sortOrder ?? 100));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const body = { slug: slug.trim(), name: clean(name), active, sortOrder: Number(sortOrder) || 0 };
    const tree = await form.submit(() =>
      sub ? api.updateSubcategory(categoryId, sub.id, body) : api.createSubcategory(categoryId, body));
    if (tree) onDone(tree);
  };

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-border p-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <TextField label={t('admin.slug')} value={slug} onChange={(e) => setSlug(e.target.value.toLowerCase())}
        error={form.fieldErrors.slug} className="max-w-xs" />
      <LocalizedInputs label={t('admin.name')} value={name} onChange={setName} required error={form.fieldErrors.name} />
      <div className="flex flex-wrap items-end gap-6">
        <TextField label={t('admin.sortOrder')} type="number" value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} className="w-32" />
        <CheckboxField label={t('admin.active')} checked={active} onChange={setActive} />
      </div>
      <FormActions submitting={form.submitting} onCancel={() => onDone()} />
    </form>
  );
}

function RequirementForm({ categoryId, req, onDone }: { categoryId: number; req?: DocumentRequirement; onDone: (tree?: Category[]) => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [code, setCode] = useState(req?.code ?? '');
  const [label, setLabel] = useState<LocalizedText>(req?.label ?? {});
  const [mandatory, setMandatory] = useState(req?.mandatory ?? true);
  const [types, setTypes] = useState<string[]>(req?.allowedTypes ?? ['application/pdf', 'image/jpeg', 'image/png']);
  const [maxMb, setMaxMb] = useState(String(req ? req.maxSizeBytes / MB : 5));
  const [active, setActive] = useState(req?.active ?? true);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const body = { code: code.trim(), label: clean(label), mandatory, allowedTypes: types,
      maxSizeBytes: Math.round(Number(maxMb) * MB), active, sortOrder: req?.sortOrder ?? 100 };
    const tree = await form.submit(() =>
      req ? api.updateRequirement(categoryId, req.id, body) : api.createRequirement(categoryId, body));
    if (tree) onDone(tree);
  };

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-border p-4" noValidate>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label={t('admin.code')} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}
          hint={t('admin.codeHint')} error={form.fieldErrors.code} />
        <TextField label={t('admin.maxSizeMb')} type="number" min={0.01} max={10} step={0.5} value={maxMb}
          onChange={(e) => setMaxMb(e.target.value)} error={form.fieldErrors.maxSizeBytes} />
      </div>
      <LocalizedInputs label={t('admin.label')} value={label} onChange={setLabel} required error={form.fieldErrors.label} />
      <fieldset>
        <legend className="text-sm font-medium">{t('admin.allowedTypes')}</legend>
        <div className="mt-2 flex flex-wrap gap-4">
          {DOC_TYPES.map((ty) => (
            <CheckboxField key={ty} label={typeLabels([ty])} checked={types.includes(ty)}
              onChange={(on) => setTypes((s) => (on ? [...s, ty] : s.filter((x) => x !== ty)))} />
          ))}
        </div>
        {form.fieldErrors.allowedTypes && <p className="mt-1 text-xs text-danger">{form.fieldErrors.allowedTypes}</p>}
      </fieldset>
      <div className="flex flex-wrap gap-6">
        <CheckboxField label={t('admin.mandatory')} checked={mandatory} onChange={setMandatory} />
        <CheckboxField label={t('admin.active')} checked={active} onChange={setActive} />
      </div>
      <FormActions submitting={form.submitting} onCancel={() => onDone()} />
    </form>
  );
}

type Editing =
  | { kind: 'category'; id?: number }
  | { kind: 'sub'; categoryId: number; id?: number }
  | { kind: 'req'; categoryId: number; id?: number }
  | null;

function Row({ children, onEdit, onDelete, inactive }: { children: ReactNode; onEdit: () => void; onDelete: () => void; inactive?: boolean }) {
  const { t } = useTranslation();
  return (
    <li className="flex items-center gap-2 py-2 text-sm">
      <div className="min-w-0 flex-1">{children}{inactive && <span className="ml-2 text-xs text-fg-muted">({t('business.inactive')})</span>}</div>
      <Button variant="ghost" size="sm" aria-label={t('business.edit')} onClick={onEdit}><Pencil className="h-4 w-4" aria-hidden="true" /></Button>
      <Button variant="ghost" size="sm" aria-label={t('admin.delete')} onClick={onDelete}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
    </li>
  );
}

export function AdminCategoriesPage() {
  const { t } = useTranslation();
  const pick = useLocalized();
  const qc = useQueryClient();
  const form = useApiForm();
  const query = useQuery({ queryKey: orgKeys.adminCategories, queryFn: api.list });
  const [editing, setEditing] = useState<Editing>(null);

  const done = (tree?: Category[]) => {
    if (tree) {
      qc.setQueryData(orgKeys.adminCategories, tree);
      void qc.invalidateQueries({ queryKey: ['public-categories'] });
    }
    setEditing(null);
  };
  const remove = async (fn: () => Promise<Category[]>) => {
    if (!window.confirm(t('admin.confirmDelete'))) return;
    done(await form.submit(fn));
  };
  const is = (e: Editing) => JSON.stringify(e) === JSON.stringify(editing);

  if (query.isLoading) return <LoadingState />;
  if (query.error || !query.data) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{t('admin.categoriesTitle')}</h1>
        <AdminNav />
      </div>
      <p className="text-sm text-fg-muted">{t('admin.categoriesIntro')}</p>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}

      {is({ kind: 'category' }) ? (
        <Card className="p-6"><CategoryForm onDone={done} /></Card>
      ) : (
        <Button onClick={() => setEditing({ kind: 'category' })}><Plus className="h-4 w-4" aria-hidden="true" />{t('admin.addCategory')}</Button>
      )}

      {query.data.map((c) => (
        <Card key={c.id} className="p-6">
          {is({ kind: 'category', id: c.id }) ? (
            <CategoryForm category={c} onDone={done} />
          ) : (
            <div className="flex flex-wrap items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-700"><CategoryIcon name={c.icon} /></span>
              <div className="min-w-0 flex-1">
                <h2 className="font-semibold">{pick(c.name)} {!c.active && <span className="text-xs font-normal text-fg-muted">({t('business.inactive')})</span>}</h2>
                <p className="text-xs text-fg-muted">{c.slug} · {t(`admin.workflowName.${c.bookingWorkflow}`)}</p>
              </div>
              <Button variant="secondary" size="sm" onClick={() => setEditing({ kind: 'category', id: c.id })}>
                <Pencil className="h-4 w-4" aria-hidden="true" />{t('business.edit')}
              </Button>
              <Button variant="ghost" size="sm" aria-label={t('admin.delete')} onClick={() => void remove(() => api.remove(c.id))}>
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          )}

          <div className="mt-5 grid gap-6 md:grid-cols-2">
            <section>
              <h3 className="text-sm font-semibold uppercase tracking-wide text-fg-muted">{t('admin.subcategories')}</h3>
              <ul className="mt-2 divide-y divide-border">
                {c.subcategories.map((s) => is({ kind: 'sub', categoryId: c.id, id: s.id }) ? (
                  <li key={s.id} className="py-2"><SubcategoryForm categoryId={c.id} sub={s} onDone={done} /></li>
                ) : (
                  <Row key={s.id} inactive={!s.active} onEdit={() => setEditing({ kind: 'sub', categoryId: c.id, id: s.id })}
                    onDelete={() => void remove(() => api.removeSubcategory(c.id, s.id))}>
                    {pick(s.name)} <span className="text-xs text-fg-muted">{s.slug}</span>
                  </Row>
                ))}
              </ul>
              {is({ kind: 'sub', categoryId: c.id }) ? (
                <SubcategoryForm categoryId={c.id} onDone={done} />
              ) : (
                <Button variant="ghost" size="sm" onClick={() => setEditing({ kind: 'sub', categoryId: c.id })}>
                  <Plus className="h-4 w-4" aria-hidden="true" />{t('admin.addSubcategory')}
                </Button>
              )}
            </section>
            <section>
              <h3 className="text-sm font-semibold uppercase tracking-wide text-fg-muted">{t('admin.requirements')}</h3>
              <ul className="mt-2 divide-y divide-border">
                {c.documentRequirements?.map((r) => is({ kind: 'req', categoryId: c.id, id: r.id }) ? (
                  <li key={r.id} className="py-2"><RequirementForm categoryId={c.id} req={r} onDone={done} /></li>
                ) : (
                  <Row key={r.id} inactive={!r.active} onEdit={() => setEditing({ kind: 'req', categoryId: c.id, id: r.id })}
                    onDelete={() => void remove(() => api.removeRequirement(c.id, r.id))}>
                    {pick(r.label)}
                    <span className="block text-xs text-fg-muted">
                      {r.mandatory ? t('business.required') : t('business.optional')} · {typeLabels(r.allowedTypes)} · {formatBytes(r.maxSizeBytes)}
                    </span>
                  </Row>
                ))}
              </ul>
              {is({ kind: 'req', categoryId: c.id }) ? (
                <RequirementForm categoryId={c.id} onDone={done} />
              ) : (
                <Button variant="ghost" size="sm" onClick={() => setEditing({ kind: 'req', categoryId: c.id })}>
                  <Plus className="h-4 w-4" aria-hidden="true" />{t('admin.addRequirement')}
                </Button>
              )}
            </section>
          </div>
        </Card>
      ))}
    </div>
  );
}
