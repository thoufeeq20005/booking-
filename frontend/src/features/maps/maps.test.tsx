import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { directionsLink, fetchDrivingRoute } from './routing';
import { LocationButton } from './LocationButton';

// Leaflet needs a real browser layout; the dialog behaviour is what we test here.
vi.mock('./RouteMap', () => ({
  default: ({ place }: { place: { name: string } }) => <div data-testid="route-map">map of {place.name}</div>,
}));

afterEach(() => vi.unstubAllGlobals());

describe('routing helpers', () => {
  it('builds Google Maps directions, from the current location unless an origin is known', () => {
    expect(directionsLink({ lat: 11.0168, lng: 76.9558 })).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=11.0168%2C76.9558',
    );
    expect(directionsLink({ lat: 11, lng: 77 }, { lat: 10.5, lng: 76.5 })).toContain('origin=10.5%2C76.5');
  });

  it('turns an OSRM response into km, minutes and a [lat, lng] path', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 'Ok',
        routes: [{ distance: 5400, duration: 900, geometry: { coordinates: [[76.95, 11.0], [76.96, 11.02]] } }],
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const r = await fetchDrivingRoute({ lat: 11.0, lng: 76.95 }, { lat: 11.02, lng: 76.96 });

    expect(fetchMock.mock.calls[0]![0]).toContain('/route/v1/driving/76.95,11;76.96,11.02');
    expect(r).toEqual({ distanceKm: 5.4, durationMinutes: 15, path: [[11.0, 76.95], [11.02, 76.96]] });
  });

  it('returns null when the router is unreachable, so the UI can fall back', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    expect(await fetchDrivingRoute({ lat: 1, lng: 2 }, { lat: 3, lng: 4 })).toBeNull();
  });
});

describe('LocationButton', () => {
  it('opens a map dialog for the place and closes it again', async () => {
    renderWithProviders(
      <LocationButton place={{ name: 'Kovai Family Clinic', address: 'R.S. Puram, Coimbatore', latitude: 11.0081, longitude: 76.9505 }} />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'See location' }));
    expect(await screen.findByTestId('route-map')).toHaveTextContent('map of Kovai Family Clinic');
    expect(screen.getByText('R.S. Puram, Coimbatore')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByTestId('route-map')).not.toBeInTheDocument();
  });
});
