import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser, type FakeRequest, type FakeResponse } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';
import { expandRows } from './VenueSection';
import { pickSeats } from '@/pages/venue/SeatsPage';

const movie = { id: 5, title: 'Signal Thirteen', languages: 'Tamil, Telugu', certificate: 'UA', genres: 'Thriller', durationMinutes: 142,
  synopsis: 'A train that is not on any timetable.', releaseDate: '2026-11-01', posterFrom: '#0f766e', posterTo: '#022c22' };
const seatMap = { showtimeId: 40, startsAt: '2026-11-10T12:30:00Z', format: '2D', language: 'Tamil', movie, theatreName: 'Marina Screens',
  branchName: 'Main', screenName: 'Screen 1', bookable: true, taken: ['A2'],
  prices: { CLASSIC: 150, PRIME: 220 },
  rows: [{ row: 'A', tier: 'CLASSIC', seats: 4, gapsAfter: [2] }, { row: 'B', tier: 'PRIME', seats: 4, gapsAfter: [] }] };
const ticket = { id: 88, reference: 'BETICKET01', status: 'CONFIRMED', startsAt: '2026-11-10T12:30:00Z', endsAt: '2026-11-10T15:10:00Z',
  timezone: 'Asia/Kolkata', organizationId: 2, organizationName: 'Marina Screens', organizationSlug: 'marina', branchId: 3, branchName: 'Main',
  serviceId: 9, serviceName: 'Signal Thirteen (2D) · A1, B1', servicePrice: 370, platformFee: 18.5, totalAmount: 388.5, rescheduleCount: 0,
  paid: true, reviewed: false, qrPayload: 'BOOKEASY:BETICKET01:abc', createdAt: '', showtimeId: 40, screenName: 'Screen 1', seats: ['A1', 'B1'] };
const dining = {
  id: 7, slug: 'dosa-corner', name: 'Dosa Corner', categoryName: { en: 'Dining' }, subcategoryName: { en: 'Restaurant' }, categoryId: 6,
  bookingWorkflow: 'DINING', ratingAvg: 0, reviewCount: 0, confirmationMode: 'INSTANT', services: [], staff: [],
  branches: [{ id: 30, name: 'Main', addressLine1: 'x', city: 'Chennai', state: 'TN', pincode: '600004', latitude: 13, longitude: 80, timezone: 'Asia/Kolkata', hours: [] }],
};

function api(user: object | null, handler: (r: FakeRequest) => FakeResponse | undefined = () => undefined) {
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return user ? reply.ok(authenticated('tok', user)) : reply.fail(401, 'SESSION_EXPIRED');
    if (r.url === '/public/config') return reply.ok({ commissionPercent: 5, support: null });
    if (r.url === '/public/movies') {
      return reply.ok([{ movie, theatres: 3, nextShow: '2026-11-10T12:30:00Z', formats: ['2D'], languages: ['Tamil', 'Telugu'] },
        { movie: { ...movie, id: 6, title: 'Orbit Cafe', genres: 'Comedy' }, theatres: 1, nextShow: '2026-11-10T12:30:00Z', formats: ['IMAX'], languages: ['English'] }]);
    }
    if (r.url === '/public/movies/upcoming') return reply.ok([{ ...movie, id: 9, title: 'Kaveri Kingdom', releaseDate: '2030-01-20' }]);
    if (r.url === '/public/movies/5') {
      return reply.ok({ movie: { ...movie, cast: 'Arjun Selvan, Meera Nandakumar' }, date: '2026-11-10', dates: ['2026-11-10', '2026-11-11'],
        formats: ['2D'], languages: ['Tamil', 'Telugu'],
        theatres: [{ organizationId: 2, theatreName: 'Marina Screens', slug: 'marina', branchId: 3, branchName: 'Main', locality: 'T. Nagar',
          city: 'Chennai', distanceKm: 1.2, shows: [
            { id: 40, startsAt: '2026-11-10T12:30:00Z', format: '2D', language: 'Tamil', screenName: 'Screen 1', seatsLeft: 7, totalSeats: 8, fromPrice: 150 },
            { id: 41, startsAt: '2026-11-10T16:00:00Z', format: '2D', language: 'Telugu', screenName: 'Screen 2', seatsLeft: 1, totalSeats: 8, fromPrice: 150 }] }] });
    }
    if (r.url === '/public/showtimes/40') return reply.ok(seatMap);
    if (r.url === '/public/organizations/dosa-corner') return reply.ok(dining);
    if (r.url === '/public/dining/dosa-corner') {
      return reply.ok({ cuisines: 'South Indian', costForTwo: 400, vegOnly: false, offer: 'Live music on Fridays', features: ['Parking'],
        maxPartySize: 6, tier: 'PREMIUM', coverPerGuest: 25,
        offers: [{ id: 7, title: 'Dinner special', percent: 30, meal: 'DINNER' }, { id: 8, title: 'Flat 20% off', percent: 20, meal: 'ALL_DAY' }],
        menu: [{ name: 'Tiffin', items: [{ id: 1, name: 'Ghee roast', price: 120, veg: true, bestseller: true },
          { id: 2, name: 'Chicken 65', price: 220, veg: false, bestseller: false }] }] });
    }
    if (r.url === '/public/dining/branches/30/slots') {
      return reply.ok([{ time: '12:00', startsAt: '2026-11-10T06:30:00Z', available: false, meal: 'LUNCH' },
        { time: '12:30', startsAt: '2026-11-10T07:00:00Z', available: true, meal: 'LUNCH' },
        { time: '19:00', startsAt: '2026-11-10T13:30:00Z', available: true, meal: 'DINNER' }]);
    }
    if (r.url === '/public/dining') {
      const base = { kind: 'restaurant', ratingAvg: 4.5, reviewCount: 12, vegOnly: false, branchId: 30, locality: 'Mylapore', city: 'Chennai', distanceKm: 1.1 };
      return reply.ok([
        { ...base, organizationId: 7, slug: 'dosa-corner', name: 'Dosa Corner', tier: 'PREMIUM', bestOfferPercent: 30, bestOfferTitle: 'Dinner special', costForTwo: 400 },
        { ...base, organizationId: 8, slug: 'brew', name: 'Brew Cafe', kind: 'cafe', tier: 'CAFE', branchId: 31, vegOnly: true, reviewCount: 0 },
      ]);
    }
    return handler(r);
  });
}

describe('movies', () => {
  it('lists what is playing nearby, filters by language and shows what is coming', async () => {
    api(null);
    renderWithProviders(<AppRoutes />, { route: '/movies?city=chennai' });
    const link = await screen.findByRole('link', { name: /Signal Thirteen/ });
    expect(link).toHaveAttribute('href', '/movies/5');
    expect(within(link).getByText('Playing in 3 theatres nearby')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /Kaveri Kingdom/ })).toHaveAttribute('href', '/movies/9');
    await userEvent.click(within(screen.getByRole('complementary', { name: 'Filters' })).getByRole('button', { name: 'English' }));
    expect(screen.queryByRole('link', { name: /Signal Thirteen/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Orbit Cafe/ })).toBeInTheDocument();
  });

  it('shows the movie and its cast, then theatres and screens after Book tickets', async () => {
    api(null);
    renderWithProviders(<AppRoutes />, { route: '/movies/5' });
    expect(await screen.findByRole('heading', { name: 'Signal Thirteen' })).toBeInTheDocument();
    expect(screen.getByText('Meera Nandakumar')).toBeInTheDocument();
    expect(screen.getByText('A train that is not on any timetable.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Book tickets' }));
    expect(await screen.findByRole('link', { name: 'Marina Screens: T. Nagar' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Screen 1 · 2D · Tamil/ })).toHaveAttribute('href', '/shows/40');
    await userEvent.selectOptions(within(screen.getByRole('main')).getByLabelText('Language'), 'Telugu');
    expect(screen.queryByRole('link', { name: /Screen 1 · 2D · Tamil/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Screen 2 · 2D · Telugu/ })).toHaveAttribute('href', '/shows/41');
  });

  it('books the chosen seats and goes to payment', async () => {
    const pending = { ...ticket, status: 'PENDING_PAYMENT', paid: false, qrPayload: undefined, createdAt: new Date().toISOString() };
    const requests = api(sampleUser, (r) => {
      if (r.url === '/customer/tickets') return reply.ok(pending);
      if (r.url === '/customer/appointments/88') return reply.ok(pending);
      if (r.url === '/customer/appointments/88/payment-order') {
        return reply.ok({ orderId: 'demo_order_1', amountPaise: 38850, currency: 'INR', reference: 'BETICKET01',
          description: 'Tickets', expiresAt: new Date(Date.now() + 600000).toISOString(), demo: true });
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/shows/40' });
    const dialog = await screen.findByRole('dialog', { name: 'How many seats?' });
    expect(within(dialog).getByText('₹220')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('radio', { name: '2' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Select seats' }));
    expect(screen.getByRole('button', { name: 'A2, Sold' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'A1' })); // A2 is sold, so only A1
    expect(screen.getByText('Tap a seat to pick 1 more.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'B1' }));
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹388.5' })); // 150 + 220 + 5% fee
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/tickets')?.body).toEqual({ showtimeId: 40, seats: ['A1', 'B1'] }));
    expect(await screen.findByRole('heading', { name: 'Payment' })).toBeInTheDocument();
  });

  it('shows a clear message when seats were just taken', async () => {
    api(sampleUser, (r) => (r.url === '/customer/tickets' ? reply.fail(409, 'SEATS_UNAVAILABLE') : undefined));
    renderWithProviders(<AppRoutes />, { route: '/shows/40' });
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('radio', { name: '1' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Select seats' }));
    await userEvent.click(screen.getByRole('button', { name: 'A1' }));
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹157.5' }));
    expect(await screen.findByText('Some of those seats were just taken. Please choose again.')).toBeInTheDocument();
  });

  it('shows the seats on the booking and offers no reschedule', async () => {
    api(sampleUser, (r) => {
      if (r.url === '/customer/appointments/88') return reply.ok(ticket);
      if (r.url === '/customer/appointments/88/cancel-preview') return reply.ok({ totalPaid: 388.5, refundAmount: 388.5, refundPercent: 100, allowed: true });
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/88' });
    expect(await screen.findByText('B1')).toBeInTheDocument();
    expect(screen.getByText('Screen 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reschedule' })).not.toBeInTheDocument();
  });
});

describe('dining', () => {
  const table = { ...ticket, id: 90, organizationSlug: 'dosa-corner', serviceName: 'Table for 4 · Flat 20% off', servicePrice: 100,
    platformFee: 0, totalAmount: 100, showtimeId: undefined, screenName: undefined, seats: [], partySize: 4, status: 'PENDING_PAYMENT',
    paid: false, qrPayload: undefined, offerTitle: 'Flat 20% off', offerPercent: 20, createdAt: new Date().toISOString() };

  it('filters restaurants by mood on the Dining home', async () => {
    api(null);
    renderWithProviders(<AppRoutes />, { route: '/dining?city=chennai' });
    // Home: rows (spotlight, cafés, premium, family) plus the full list, so a place can appear more than once.
    expect((await screen.findAllByRole('link', { name: /Brew Cafe/ })).length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { name: 'Cozy cafés' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Premium dining' }));
    expect(screen.queryAllByRole('link', { name: /Brew Cafe/ })).toHaveLength(0);
    expect(screen.getAllByRole('link', { name: /Dosa Corner/ }).length).toBeGreaterThan(0);
  });

  it('shows offers and menu, then books a table with an offer and goes to pay the cover charge', async () => {
    const requests = api(sampleUser, (r) => {
      if (r.url === '/customer/table-bookings') return reply.ok(table);
      if (r.url === '/customer/appointments/90') return reply.ok(table);
      if (r.url === '/customer/appointments/90/payment-order') {
        return reply.ok({ orderId: 'demo_order_9', amountPaise: 10000, currency: 'INR', reference: 'BETICKET01', description: 'Table',
          expiresAt: new Date(Date.now() + 600000).toISOString(), demo: true });
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/b/dosa-corner' });
    expect(await screen.findByText('Ghee roast')).toBeInTheDocument();
    expect(screen.getByText('Dinner special')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Veg only'));
    expect(screen.queryByText('Chicken 65')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Book a table' }));
    const sheet = await screen.findByRole('dialog', { name: 'Booking at Dosa Corner' });
    await userEvent.click(within(sheet).getByRole('radio', { name: '4' }));
    await userEvent.click(within(sheet).getByRole('radio', { name: 'Lunch' }));
    expect(await within(sheet).findByRole('radio', { name: /12:00 pm/ })).toBeDisabled();
    await userEvent.click(within(sheet).getByRole('radio', { name: /12:30 pm/ }));
    // Only all-day offers apply at lunch; the cover is ₹25 × 4.
    expect(within(sheet).queryByRole('radio', { name: /Dinner special/ })).not.toBeInTheDocument();
    expect(within(sheet).getByRole('radio', { name: /Flat 20% off/ })).toBeInTheDocument();
    expect(within(sheet).getByText(/₹100 total cover charge/)).toBeInTheDocument();
    await userEvent.click(within(sheet).getByRole('button', { name: 'Proceed to book' }));
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/table-bookings')?.body).toEqual({
      branchId: 30, startsAt: '2026-11-10T07:00:00Z', partySize: 4, offerId: 8,
    }));
    expect(await screen.findByRole('heading', { name: 'Payment' })).toBeInTheDocument();
  });

  it('pays the bill with the offer and the cover charge taken off', async () => {
    const bill = { ...table, id: 91, serviceName: 'Bill payment · Flat 20% off', servicePrice: 700, totalAmount: 700, partySize: undefined,
      billAmount: 1000, discountAmount: 200, coverCredit: 100 };
    const requests = api(sampleUser, (r) => {
      if (r.url === '/customer/dining/bill-quote') {
        return reply.ok({ billAmount: r.params.amount, offerTitle: 'Flat 20% off', offerPercent: 20, discount: 200, coverCredit: 100,
          payable: 700, bookingId: 90 });
      }
      if (r.url === '/customer/dining/bills') return reply.ok(bill);
      if (r.url === '/customer/appointments/91') return reply.ok(bill);
      if (r.url === '/customer/appointments/91/payment-order') {
        return reply.ok({ orderId: 'demo_order_10', amountPaise: 70000, currency: 'INR', reference: 'BETICKET01', description: 'Bill',
          expiresAt: new Date(Date.now() + 600000).toISOString(), demo: true });
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/dining/dosa-corner/pay-bill' });
    await userEvent.type(await screen.findByLabelText('Enter the bill amount'), '1000');
    expect(await screen.findByText('Cover charge paid when booking')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: /Pay ₹700/ }));
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/dining/bills')?.body).toEqual({
      branchId: 30, amount: 1000, bookingId: 90,
    }));
    expect(await screen.findByRole('heading', { name: 'Payment' })).toBeInTheDocument();
  });
});

describe('screen layout builder', () => {
  it('expands row groups into rows with aisles', () => {
    const rows = expandRows([
      { from: 'a', to: 'c', seats: '10', tier: 'classic', aisles: '3, 7, 12' },
      { from: 'D', to: '', seats: '6', tier: 'RECLINER', aisles: '' },
      { from: 'Z', to: 'X', seats: '5', tier: 'PRIME', aisles: '' }, // backwards: ignored
    ]);
    expect(rows.map((r) => r.row)).toEqual(['A', 'B', 'C', 'D']);
    expect(rows[0]).toEqual({ row: 'A', tier: 'CLASSIC', seats: 10, gapsAfter: [3, 7] });
    expect(rows[3]!.tier).toBe('RECLINER');
  });
});

describe('seat picking', () => {
  const map = { ...seatMap, taken: ['B3'], rows: [{ row: 'B', tier: 'PRIME', seats: 8, gapsAfter: [4] }] } as never;
  it('fills the chosen number of seats to the right, stopping at an aisle or a sold seat', () => {
    expect(pickSeats(map, 'B1', 4, [])).toEqual(['B1', 'B2']); // B3 sold
    expect(pickSeats(map, 'B4', 3, [])).toEqual(['B4']); // aisle after 4
    expect(pickSeats(map, 'B5', 3, ['B4'])).toEqual(['B4', 'B5', 'B6']);
    expect(pickSeats(map, 'B5', 2, ['B1', 'B2'])).toEqual(['B5', 'B6']); // full: start again
    expect(pickSeats(map, 'B2', 2, ['B1', 'B2'])).toEqual(['B1']); // tap a picked seat to drop it
  });
});
