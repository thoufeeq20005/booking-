export interface LatLng {
  lat: number;
  lng: number;
}

export interface RouteSummary {
  distanceKm: number;
  durationMinutes: number;
  /** [lat, lng] points along the road. */
  path: [number, number][];
}

/**
 * Public OSRM demo router (OpenStreetMap data, no API key). It is fair-use only with no uptime promise, so a
 * production launch should point this at a self-hosted OSRM or a paid routing API.
 */
const OSRM = 'https://router.project-osrm.org/route/v1/driving';

/** Driving route between two points, or null if the router can't be reached or finds no road route. */
export async function fetchDrivingRoute(from: LatLng, to: LatLng): Promise<RouteSummary | null> {
  const url = `${OSRM}/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson`;
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    const data = (await res.json()) as {
      code: string;
      routes?: { distance: number; duration: number; geometry: { coordinates: [number, number][] } }[];
    };
    const r = data.code === 'Ok' ? data.routes?.[0] : undefined;
    if (!r) return null;
    return {
      distanceKm: r.distance / 1000,
      durationMinutes: r.duration / 60,
      path: r.geometry.coordinates.map(([lng, lat]) => [lat, lng]),
    };
  } catch {
    return null;
  }
}

/** Google Maps turn-by-turn directions. Without an origin, Google starts from the viewer's current location. */
export function directionsLink(to: LatLng, from?: LatLng | null): string {
  const p = new URLSearchParams({ api: '1', destination: `${to.lat},${to.lng}` });
  if (from) p.set('origin', `${from.lat},${from.lng}`);
  return `https://www.google.com/maps/dir/?${p.toString()}`;
}
