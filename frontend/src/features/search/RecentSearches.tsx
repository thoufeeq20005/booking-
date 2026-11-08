import { useState } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Clock, History } from 'lucide-react';
import { fileUrl, useLocalized } from '@/features/organization/shared';
import { recent, useRecent } from './recent';
import { SearchOverlay } from './SearchOverlay';

/**
 * Home page: the visitor's recent searches (tap one to search again) and the places they opened recently. Hidden until
 * they have searched for something. Kept in this browser only.
 */
export function RecentSearches() {
  const { t } = useTranslation();
  const pick = useLocalized();
  const { terms, viewed } = useRecent();
  const [open, setOpen] = useState<string | null>(null);
  if (terms.length === 0 && viewed.length === 0) return null;

  return (
    <section className="mx-auto mt-10 max-w-7xl px-4 sm:px-6" aria-labelledby="recent-searches">
      <div className="flex items-center justify-between gap-3">
        <h2 id="recent-searches" className="flex items-center gap-2 text-xl font-bold tracking-tight">
          <History className="h-5 w-5 text-brand-600" aria-hidden="true" />{t('quickSearch.recentTitle')}
        </h2>
        <button type="button" onClick={() => recent.clear()} className="text-sm font-semibold text-brand-700 hover:underline">
          {t('quickSearch.clearRecent')}
        </button>
      </div>

      {terms.length > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2">
          {terms.map((term) => (
            <li key={term}>
              <button type="button" onClick={() => setOpen(term)}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-4 py-2 text-sm font-medium hover:border-brand-300 hover:text-brand-700">
                <Clock className="h-4 w-4 text-fg-muted" aria-hidden="true" />{term}
              </button>
            </li>
          ))}
        </ul>
      )}

      {viewed.length > 0 && (
        <>
          <h3 className="mt-6 text-sm font-semibold text-fg-muted">{t('quickSearch.recentlyViewed')}</h3>
          <ul className="-mx-1 mt-3 flex snap-x gap-4 overflow-x-auto px-1 pb-2">
            {viewed.map((item) => (
              <li key={item.href} className="w-56 shrink-0 snap-start">
                <Link to={item.href} className="group block">
                  <div
                    className="aspect-[4/3] overflow-hidden rounded-2xl bg-surface-muted"
                    style={item.posterFrom ? { backgroundImage: `linear-gradient(160deg, ${item.posterFrom}, ${item.posterTo})` } : undefined}
                  >
                    {item.imageUrl && (
                      <img src={fileUrl(item.imageUrl)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]" />
                    )}
                  </div>
                  <p className="mt-2 truncate font-semibold group-hover:text-brand-700">{item.title}</p>
                  <p className="truncate text-sm text-fg-muted">
                    {item.kind === 'MOVIE' ? t('quickSearch.kind.movie') : item.kind === 'EVENT' ? t('quickSearch.kind.event') : pick(item.category)}
                    {item.detail && <> · {item.detail}</>}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}

      {open !== null && <SearchOverlay initialText={open} onClose={() => setOpen(null)} />}
    </section>
  );
}
