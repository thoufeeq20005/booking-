import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Heart } from 'lucide-react';
import clsx from 'clsx';
import { useAuth } from '@/contexts/AuthContext';
import { extrasApi } from '@/services/extrasApi';

/** Heart toggle on a business page; only shown to signed-in customers. */
export function FavouriteButton({ orgId }: { orgId: number }) {
  const { t } = useTranslation();
  const { status, hasRole } = useAuth();
  const qc = useQueryClient();
  const enabled = status === 'authenticated' && hasRole('CUSTOMER');
  const { data = [] } = useQuery({ queryKey: ['favourites'], queryFn: extrasApi.favourites, enabled });
  if (!enabled) return null;
  const on = data.some((f) => f.organizationId === orgId);
  const toggle = () =>
    void (on ? extrasApi.removeFavourite(orgId) : extrasApi.addFavourite(orgId)).then(() => qc.invalidateQueries({ queryKey: ['favourites'] }));
  return (
    <button type="button" onClick={toggle} aria-pressed={on} aria-label={on ? t('extras.removeFavourite') : t('extras.addFavourite')}
      className="rounded-full p-1.5 hover:bg-surface-muted">
      <Heart className={clsx('h-6 w-6', on ? 'fill-rose-500 text-rose-500' : 'text-fg-muted')} aria-hidden="true" />
    </button>
  );
}
