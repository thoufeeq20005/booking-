import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { LocateFixed, Navigation } from 'lucide-react';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Alert';
import { directionsLink, fetchDrivingRoute, type RouteSummary } from './routing';

export interface Place {
  name: string;
  address: string;
  latitude: number;
  longitude: number;
}

const BRAND = '#2563eb';
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';

/** Map of one place, with an on-demand driving route from the viewer's current location. */
export default function RouteMap({ place }: { place: Place }) {
  const { t } = useTranslation();
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const routeLayer = useRef<L.LayerGroup | null>(null);
  const [state, setState] = useState<'idle' | 'locating' | 'routing' | 'done' | 'denied' | 'failed'>('idle');
  const [route, setRoute] = useState<RouteSummary | null>(null);
  const [origin, setOrigin] = useState<{ lat: number; lng: number } | null>(null);

  useEffect(() => {
    if (!box.current) return;
    const m = L.map(box.current, { scrollWheelZoom: false }).setView([place.latitude, place.longitude], 15);
    L.tileLayer(TILES, { maxZoom: 19, attribution: ATTRIBUTION }).addTo(m);
    L.circleMarker([place.latitude, place.longitude], { radius: 10, color: '#fff', weight: 3, fillColor: BRAND, fillOpacity: 1 })
      .addTo(m)
      .bindPopup(`<strong>${escapeHtml(place.name)}</strong><br>${escapeHtml(place.address)}`)
      .openPopup();
    routeLayer.current = L.layerGroup().addTo(m);
    map.current = m;
    return () => {
      m.remove();
      map.current = null;
    };
  }, [place.latitude, place.longitude, place.name, place.address]);

  const showRoute = () => {
    if (!navigator.geolocation) {
      setState('denied');
      return;
    }
    setState('locating');
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const from = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setOrigin(from);
        setState('routing');
        const r = await fetchDrivingRoute(from, { lat: place.latitude, lng: place.longitude });
        const m = map.current;
        const layer = routeLayer.current;
        if (!m || !layer) return;
        layer.clearLayers();
        L.circleMarker([from.lat, from.lng], { radius: 8, color: '#fff', weight: 3, fillColor: '#16a34a', fillOpacity: 1 })
          .addTo(layer)
          .bindTooltip(t('maps.you'), { permanent: true, direction: 'top', offset: [0, -8] });
        const line = r
          ? L.polyline(r.path, { color: BRAND, weight: 5, opacity: 0.85 })
          : L.polyline([[from.lat, from.lng], [place.latitude, place.longitude]], { color: BRAND, weight: 3, dashArray: '6 8' });
        line.addTo(layer);
        m.fitBounds(line.getBounds(), { padding: [30, 30] });
        setRoute(r);
        setState(r ? 'done' : 'failed');
      },
      () => setState('denied'),
      { enableHighAccuracy: true, timeout: 15000 },
    );
  };

  return (
    <div className="space-y-3">
      <div ref={box} className="h-80 w-full overflow-hidden rounded-xl border border-border sm:h-96" data-testid="route-map" />
      {state === 'denied' && <Alert tone="error">{t('maps.locationDenied')}</Alert>}
      {state === 'failed' && <Alert tone="error">{t('maps.routeFailed')}</Alert>}
      {route && (
        <p className="flex items-center gap-2 text-sm font-medium" role="status">
          <Navigation className="h-4 w-4 text-brand-600" aria-hidden="true" />
          {t('maps.summary', { km: route.distanceKm.toFixed(1), minutes: Math.max(1, Math.round(route.durationMinutes)) })}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button onClick={showRoute} loading={state === 'locating' || state === 'routing'}>
          <LocateFixed className="h-4 w-4" aria-hidden="true" />
          {t('maps.showRoute')}
        </Button>
        <a
          className={buttonClasses('secondary')}
          href={directionsLink({ lat: place.latitude, lng: place.longitude }, origin)}
          target="_blank"
          rel="noopener noreferrer"
        >
          {t('maps.openGoogle')}
        </a>
      </div>
      <p className="text-xs text-fg-muted">{t('maps.privacy')}</p>
    </div>
  );
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}
