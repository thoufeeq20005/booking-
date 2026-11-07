import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowRight, CalendarDays, Film, Search, Store, X } from 'lucide-react';
import clsx from 'clsx';
import { Spinner } from '@/components/ui/Spinner';
import { fileUrl, useLocalized } from '@/features/organization/shared';
import { useVenueLocation, useVenueFormat } from '@/features/venue/shared';
import { usePublicCategories } from '@/services/organizationApi';
import { discoveryApi, type QuickItem } from '@/services/discoveryApi';

/** Waits until typing pauses, so each keystroke doesn't start a request. */
function useDebounced<T>(value: T, ms: number) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = window.setTimeout(() => setV(value), ms);
    return () => window.clearTimeout(id);
  }, [value, ms]);
  return v;
}

/** "Search for 'biryani'", then 'dentist', 'gym', ... every few seconds while the box is empty. */
function useRotatingExample(active: boolean) {
  const { t } = useTranslation();
  const examples = t('quickSearch.examples').split('|');
  const [i, setI] = useState(0);
  useEffect(() => {
    if (!active) return undefined;
    const id = window.setInterval(() => setI((n) => n + 1), 2500);
    return () => window.clearInterval(id);
  }, [active]);
  return examples[i % examples.length];
}

function Thumb({ item }: { item: QuickItem }) {
  const Icon = item.kind === 'MOVIE' ? Film : item.kind === 'EVENT' ? CalendarDays : Store;
  return (
    <span
      className="relative flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-surface-muted text-fg-muted"
      style={item.posterFrom ? { backgroundImage: `linear-gradient(160deg, ${item.posterFrom}, ${item.posterTo})` } : undefined}
      aria-hidden="true"
    >
      {item.imageUrl ? (
        <img src={fileUrl(item.imageUrl)} alt="" loading="lazy" className="absolute inset-0 h-full w-full object-cover" />
      ) : (
        <Icon className={clsx('h-6 w-6', item.posterFrom && 'text-white/90')} />
      )}
    </span>
  );
}

function Row({ item, onPick }: { item: QuickItem; onPick: () => void }) {
  const { t } = useTranslation();
  const pick = useLocalized();
  const { day } = useVenueFormat();
  const label =
    item.kind === 'MOVIE'
      ? t('quickSearch.kind.movie')
      : item.kind === 'EVENT'
        ? item.until ? t('quickSearch.eventUntil', { date: day(item.until) }) : t('quickSearch.kind.event')
        : item.category ? pick(item.category) : '';
  return (
    <li>
      <Link
        to={item.href}
        onClick={onPick}
        className="flex items-center gap-3 rounded-xl p-2 outline-none transition hover:bg-surface-muted focus-visible:bg-surface-muted focus-visible:ring-2 focus-visible:ring-brand-500"
      >
        <Thumb item={item} />
        <span className="min-w-0">
          <span className="block truncate font-semibold text-fg">{item.title}</span>
          <span className="block truncate text-sm text-fg-muted">
            {label}
            {item.detail && <> · {item.detail}</>}
          </span>
        </span>
      </Link>
    </li>
  );
}

/**
 * The search window opened from the header: rotating examples in the box, category tabs, "Trending in <city>" while the
 * box is empty, and movies, businesses and events as you type.
 */
export function SearchOverlay({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const pick = useLocalized();
  const navigate = useNavigate();
  const [loc] = useVenueLocation();
  const categories = usePublicCategories();
  const [text, setText] = useState('');
  const [tab, setTab] = useState('all');
  const inputRef = useRef<HTMLInputElement>(null);
  const q = useDebounced(text.trim(), 250);
  const example = useRotatingExample(text === '');

  const results = useQuery({
    queryKey: ['quick-search', loc.lat, loc.lng, tab, q],
    queryFn: () => discoveryApi.quickSearch({ lat: loc.lat, lng: loc.lng, tab, ...(q ? { q } : {}) }),
    placeholderData: keepPreviousData,
    staleTime: 60 * 1000,
  });

  useEffect(() => {
    inputRef.current?.focus();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = overflow;
    };
  }, []);

  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') onClose();
  };
  const seeAll = () => {
    onClose();
    navigate(`/search?q=${encodeURIComponent(text.trim())}`);
  };

  const city = loc.label === 'gps' ? null : t(`search.city.${loc.label}`, { defaultValue: loc.label });
  const data = results.data;
  const items = data?.items ?? [];
  const heading = !data || data.trending
    ? city ? t('quickSearch.trendingIn', { city }) : t('quickSearch.trendingNearYou')
    : t('quickSearch.results', { q });
  const tabs = [{ slug: 'all', name: t('quickSearch.all') }, ...(categories.data ?? []).map((c) => ({ slug: c.slug, name: pick(c.name) }))];

  return (
    <div className="fixed inset-0 z-50 flex justify-center bg-black/50 backdrop-blur-sm sm:items-start sm:px-4 sm:pt-16" onKeyDown={onKey}>
      <button type="button" className="absolute inset-0 cursor-default" aria-label={t('quickSearch.close')} tabIndex={-1} onClick={onClose} />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('quickSearch.open')}
        className="relative flex h-full w-full max-w-3xl flex-col overflow-hidden bg-surface shadow-2xl sm:h-auto sm:max-h-[80vh] sm:rounded-2xl"
      >
        <form
          className="flex items-center gap-3 border-b border-border px-4 py-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (text.trim()) seeAll();
          }}
        >
          <Search className="h-5 w-5 shrink-0 text-fg-muted" aria-hidden="true" />
          <input
            ref={inputRef}
            type="search"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={t('quickSearch.placeholder', { term: example })}
            aria-label={t('quickSearch.open')}
            maxLength={60}
            className="h-10 min-w-0 flex-1 bg-transparent text-lg text-fg outline-none placeholder:text-fg-muted"
          />
          {results.isFetching && <Spinner size="sm" className="text-brand-600" />}
          <button
            type="button"
            onClick={onClose}
            aria-label={t('quickSearch.close')}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted hover:bg-surface-muted"
          >
            <X className="h-5 w-5" />
          </button>
        </form>

        <div className="flex gap-1 overflow-x-auto border-b border-border px-3" role="tablist" aria-label={t('quickSearch.tabs')}>
          {tabs.map((c) => (
            <button
              key={c.slug}
              type="button"
              role="tab"
              aria-selected={tab === c.slug}
              onClick={() => setTab(c.slug)}
              className={clsx(
                '-mb-px shrink-0 border-b-2 px-3 py-2.5 text-sm font-semibold transition',
                tab === c.slug ? 'border-brand-600 text-fg' : 'border-transparent text-fg-muted hover:text-fg',
              )}
            >
              {c.name}
            </button>
          ))}
        </div>

        <div className="flex-1 overflow-y-auto px-3 py-4">
          <h2 className="px-2 text-xs font-bold uppercase tracking-wider text-fg-muted">{heading}</h2>
          {results.isPending ? (
            <div className="flex justify-center py-10"><Spinner className="text-brand-600" /></div>
          ) : results.isError ? (
            <p className="px-2 py-8 text-center text-sm text-fg-muted">{t('quickSearch.failed')}</p>
          ) : items.length === 0 ? (
            <p className="px-2 py-8 text-center text-sm text-fg-muted">
              {data?.trending ? t('quickSearch.nothingNearby') : t('quickSearch.noResults', { q })}
            </p>
          ) : (
            <ul className="mt-2 grid gap-x-4 gap-y-1 sm:grid-cols-2">
              {items.map((item) => (
                <Row key={`${item.kind}:${item.href}:${item.title}`} item={item} onPick={onClose} />
              ))}
            </ul>
          )}
        </div>

        {text.trim().length >= 2 && (
          <button
            type="button"
            onClick={seeAll}
            className="flex items-center justify-between border-t border-border px-5 py-3 text-sm font-semibold text-brand-700 hover:bg-surface-muted"
          >
            {t('quickSearch.seeAll', { q: text.trim() })}
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </button>
        )}
      </div>
    </div>
  );
}

/** Header button that opens the search window (also Ctrl+K / ⌘K). */
export function SearchButton({ className }: { className?: string }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setOpen(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={t('quickSearch.open')}
        className={clsx(
          'inline-flex h-10 items-center gap-2 rounded-xl border border-border bg-surface px-3 text-sm text-fg-muted transition hover:border-brand-300 hover:text-fg',
          className,
        )}
      >
        <Search className="h-4 w-4" aria-hidden="true" />
        <span className="hidden lg:inline">{t('quickSearch.buttonHint')}</span>
      </button>
      {open && <SearchOverlay onClose={() => setOpen(false)} />}
    </>
  );
}
