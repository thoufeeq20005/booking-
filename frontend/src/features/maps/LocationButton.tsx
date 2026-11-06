import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { MapPin, X } from 'lucide-react';
import clsx from 'clsx';
import { buttonClasses } from '@/components/ui/Button';
import { LoadingState } from '@/components/ui/StateMessage';
import type { Place } from './RouteMap';

// Leaflet is only downloaded when someone actually opens a map.
const RouteMap = lazy(() => import('./RouteMap'));

/** "See location" button that opens a map dialog with a route from the viewer's location. */
export function LocationButton({ place, className, variant = 'secondary' }: {
  place: Place;
  className?: string;
  variant?: 'secondary' | 'ghost';
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const d = dialog.current;
    if (!d) return;
    if (open && !d.open) {
      if (typeof d.showModal === 'function') d.showModal();
      else d.setAttribute('open', '');
    }
  }, [open]);

  const close = () => {
    dialog.current?.close?.();
    setOpen(false);
  };

  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={clsx(buttonClasses(variant, 'sm'), className)}>
        <MapPin className="h-4 w-4" aria-hidden="true" />
        {t('maps.seeLocation')}
      </button>
      {open && (
        <dialog
          ref={dialog}
          aria-label={place.name}
          onClose={() => setOpen(false)}
          onClick={(e) => { if (e.target === e.currentTarget) close(); }}
          className="w-[min(48rem,calc(100vw-2rem))] rounded-2xl border border-border bg-surface p-0 text-fg shadow-lift backdrop:bg-black/40"
        >
          <div className="space-y-4 p-5">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold">{place.name}</h2>
                <p className="text-sm text-fg-muted">{place.address}</p>
              </div>
              <button type="button" onClick={close} aria-label={t('maps.close')}
                className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-fg-muted hover:bg-surface-muted">
                <X className="h-5 w-5" aria-hidden="true" />
              </button>
            </div>
            <Suspense fallback={<LoadingState />}>
              <RouteMap place={place} />
            </Suspense>
          </div>
        </dialog>
      )}
    </>
  );
}
