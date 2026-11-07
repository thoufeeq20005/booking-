import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser, type FakeRequest, type FakeResponse } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';
import { expandRows } from './VenueSection';

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
    if (r.url === '/public/movies') return reply.ok([{ movie, theatres: 3, nextShow: '2026-11-10T12:30:00Z' }]);
    if (r.url === '/public/showtimes/40') return reply.ok(seatMap);
    if (r.url === '/public/organizations/dosa-corner') return reply.ok(dining);
    if (r.url === '/public/dining/dosa-corner') {
      return reply.ok({ cuisines: 'South Indian', costForTwo: 400, vegOnly: false, offer: '10% off before noon', features: ['Parking'],
        maxPartySize: 6, menu: [{ name: 'Tiffin', items: [{ id: 1, name: 'Ghee roast', price: 120, veg: true, bestseller: true },
          { id: 2, name: 'Chicken 65', price: 220, veg: false, bestseller: false }] }] });
    }
    if (r.url === '/public/dining/branches/30/slots') {
      return reply.ok([{ time: '12:00', startsAt: '2026-11-10T06:30:00Z', available: false },
        { time: '12:30', startsAt: '2026-11-10T07:00:00Z', available: true }]);
    }
    return handler(r);
  });
}

describe('movies', () => {
  it('lists what is playing nearby', async () => {
    api(null);
    renderWithProviders(<AppRoutes />, { route: '/movies?city=chennai' });
    const link = await screen.findByRole('link', { name: /Signal Thirteen/ });
    expect(link).toHaveAttribute('href', '/movies/5');
    expect(within(link).getByText('Playing in 3 theatres nearby')).toBeInTheDocument();
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
    expect(await screen.findByRole('button', { name: 'A2, Sold' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'A1' }));
    await userEvent.click(screen.getByRole('button', { name: 'B1' }));
    expect(screen.getByText('Tickets (2)')).toBeInTheDocument();
    expect(screen.getByText('₹388.5')).toBeInTheDocument(); // 150 + 220 + 5% fee
    await userEvent.click(screen.getByRole('button', { name: 'Book and pay' }));
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/tickets')?.body).toEqual({ showtimeId: 40, seats: ['A1', 'B1'] }));
    expect(await screen.findByRole('heading', { name: 'Payment' })).toBeInTheDocument();
  });

  it('shows a clear message when seats were just taken', async () => {
    api(sampleUser, (r) => (r.url === '/customer/tickets' ? reply.fail(409, 'SEATS_UNAVAILABLE') : undefined));
    renderWithProviders(<AppRoutes />, { route: '/shows/40' });
    await userEvent.click(await screen.findByRole('button', { name: 'A1' }));
    await userEvent.click(screen.getByRole('button', { name: 'Book and pay' }));
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
  it('shows the menu and books a free table', async () => {
    const table = { ...ticket, id: 90, serviceName: 'Table for 4', servicePrice: 0, platformFee: 0, totalAmount: 0, showtimeId: undefined,
      screenName: undefined, seats: [], partySize: 4 };
    const requests = api(sampleUser, (r) => {
      if (r.url === '/customer/table-bookings') return reply.ok(table);
      if (r.url === '/customer/appointments/90') return reply.ok(table);
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/b/dosa-corner' });
    expect(await screen.findByText('Ghee roast')).toBeInTheDocument();
    expect(screen.getByText('10% off before noon')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Veg only'));
    expect(screen.queryByText('Chicken 65')).not.toBeInTheDocument();

    expect(await screen.findByRole('radio', { name: '12:00 pm' })).toBeDisabled();
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Guests' })).getByRole('radio', { name: '4' }));
    await userEvent.click(await screen.findByRole('radio', { name: '12:30 pm' }));
    await userEvent.click(screen.getByRole('button', { name: 'Reserve a table for 4' }));
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/table-bookings')?.body).toEqual({
      branchId: 30, startsAt: '2026-11-10T07:00:00Z', partySize: 4,
    }));
    expect(await screen.findByText("You're booked!")).toBeInTheDocument();
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
