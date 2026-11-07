import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser, type FakeRequest, type FakeResponse } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

const profile = {
  id: 1, slug: 'smile', name: 'Smile Studio', categoryName: { en: 'Healthcare' }, subcategoryName: { en: 'Dental' }, categoryId: 1,
  bookingWorkflow: 'HEALTHCARE', ratingAvg: 0, reviewCount: 0, confirmationMode: 'INSTANT',
  branches: [{ id: 3, name: 'Main', addressLine1: 'x', city: 'Chennai', state: 'TN', pincode: '600020', latitude: 13, longitude: 80, timezone: 'Asia/Kolkata', hours: [] }],
  services: [{ id: 9, name: 'Check-up', durationMinutes: 30, price: 500, consultationType: 'IN_PERSON', requiresStaff: true, branchPrices: { 3: 500 } }],
  staff: [],
};
const booking = { id: 77, reference: 'BEABCDEFGH', status: 'CONFIRMED', startsAt: '2026-11-10T03:30:00Z', endsAt: '2026-11-10T04:00:00Z',
  timezone: 'Asia/Kolkata', organizationId: 1, organizationName: 'Smile Studio', organizationSlug: 'smile', branchId: 3, branchName: 'Main',
  serviceId: 9, serviceName: 'Check-up', servicePrice: 500, platformFee: 25, totalAmount: 525, rescheduleCount: 0,
  qrPayload: 'BOOKEASY:BEABCDEFGH:abc', createdAt: '' };

function api(user: object | null, handler: (r: FakeRequest) => FakeResponse | undefined) {
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return user ? reply.ok(authenticated('tok', user)) : reply.fail(401, 'SESSION_EXPIRED');
    if (r.url === '/public/config') return reply.ok({ commissionPercent: 5, support: null });
    if (r.url === '/public/organizations/smile') return reply.ok(profile);
    if (r.url === '/public/branches/3/services/9/slots') {
      return reply.ok({ date: r.params.date, timezone: 'Asia/Kolkata', price: 500, durationMinutes: 30, outcome: 'OPEN', staff: [],
        slots: [{ time: '09:00', startsAt: '2026-11-10T03:30:00Z', endsAt: '2026-11-10T04:00:00Z', staffIds: [4] }] });
    }
    return handler(r);
  });
}

describe('booking wizard', () => {
  it('books the chosen slot and opens the booking pass', async () => {
    const requests = api({ ...sampleUser, emailVerified: true }, (r) => {
      if (r.url === '/customer/appointments' && r.method === 'POST') return reply.ok(booking);
      if (r.url === '/customer/appointments/77') return reply.ok(booking);
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/b/smile' });
    expect(await screen.findByText('₹525')).toBeInTheDocument(); // 500 + 5% fee
    await userEvent.click(await screen.findByRole('radio', { name: '9:00 am' }));
    await userEvent.type(screen.getByLabelText('Note for the business (optional)'), 'Tooth pain');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm booking' }));
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/appointments' && r.method === 'POST')?.body).toEqual({
      branchId: 3, serviceId: 9, startsAt: '2026-11-10T03:30:00Z', note: 'Tooth pain',
    }));
    expect(await screen.findByText("You're booked!")).toBeInTheDocument();
    expect(screen.getByText('BEABCDEFGH')).toBeInTheDocument();
  });

  it('goes to the payment page after Confirm booking when online payment is needed', async () => {
    const pending = { ...booking, status: 'PENDING_PAYMENT', paid: false, qrPayload: undefined, createdAt: new Date().toISOString() };
    api(sampleUser, (r) => {
      if (r.url === '/customer/appointments' && r.method === 'POST') return reply.ok(pending);
      if (r.url === '/customer/appointments/77') return reply.ok(pending);
      if (r.url === '/customer/appointments/77/payment-order') {
        return reply.ok({ orderId: 'demo_order_1', amountPaise: 52500, currency: 'INR', reference: 'BEABCDEFGH',
          description: 'Check-up', expiresAt: new Date(Date.now() + 600000).toISOString(), demo: true });
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/b/smile' });
    await userEvent.click(await screen.findByRole('radio', { name: '9:00 am' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm booking' }));

    expect(await screen.findByRole('heading', { name: 'Payment' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /Continue/ })).toBeInTheDocument(); // demo: choose a method first
  });

  it('asks visitors to sign in first', async () => {
    api(null, () => undefined);
    renderWithProviders(<AppRoutes />, { route: '/b/smile' });
    expect(await screen.findByRole('link', { name: 'Sign in to book' })).toHaveAttribute('href', '/signin?next=%2Fb%2Fsmile');
  });
});

describe('booking detail', () => {
  it('shows the refund before cancelling', async () => {
    const requests = api(sampleUser, (r) => {
      if (r.url === '/customer/appointments/77') return reply.ok(booking);
      if (r.url === '/customer/appointments/77/cancel-preview') return reply.ok({ totalPaid: 525, refundAmount: 250, refundPercent: 50, allowed: true });
      if (r.url === '/customer/appointments/77/cancel') return reply.ok({ ...booking, status: 'CANCELLED', refundAmount: 250, qrPayload: undefined });
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/77' });
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }));
    expect(await screen.findByText(/refunds ₹250 \(50% of the service fee/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel booking' }));
    expect(await screen.findByText('Cancelled')).toBeInTheDocument();
    expect(requests.some((r) => r.url === '/customer/appointments/77/cancel')).toBe(true);
  });
});
