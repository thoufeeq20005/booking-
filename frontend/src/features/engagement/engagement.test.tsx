import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('notifications', () => {
  it('shows the unread count and opens the linked page', async () => {
    let read = false;
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', sampleUser));
      if (r.url === '/public/config') return reply.ok({ support: null });
      if (r.url === '/account/notifications/unread-count') return reply.ok(read ? 0 : 1);
      if (r.url === '/account/notifications') {
        return reply.ok([{ id: 5, type: 'BOOKING_CONFIRMED', title: 'Booking confirmed: Check-up', body: 'See you soon',
          link: '/customer/bookings', readAt: read ? '2026-11-04T10:00:00Z' : undefined, createdAt: '2026-11-04T10:00:00Z' }]);
      }
      if (r.url === '/account/notifications/5/read') { read = true; return reply.ok(null); }
      if (r.url === '/customer/appointments') return reply.ok([]);
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/notifications' });
    expect((await screen.findAllByRole('link', { name: '1 unread notifications' })).length).toBeGreaterThan(0);
    await userEvent.click(await screen.findByText('Booking confirmed: Check-up'));
    await waitFor(() => expect(requests.some((r) => r.url === '/account/notifications/5/read')).toBe(true));
    expect(await screen.findByRole('heading', { name: 'My bookings' })).toBeInTheDocument();
  });
});

describe('reviews', () => {
  it('lets the customer rate a completed booking', async () => {
    const done = { id: 77, reference: 'BEABCDEFGH', status: 'COMPLETED', startsAt: '2026-11-10T03:30:00Z', endsAt: '2026-11-10T04:00:00Z',
      timezone: 'Asia/Kolkata', organizationId: 1, organizationName: 'Smile Studio', organizationSlug: 'smile', branchId: 3,
      branchName: 'Main', serviceId: 9, serviceName: 'Check-up', servicePrice: 500, platformFee: 25, totalAmount: 525,
      rescheduleCount: 0, paid: true, reviewed: false, createdAt: '' };
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', sampleUser));
      if (r.url === '/public/config') return reply.ok({ support: null });
      if (r.url === '/customer/appointments/77') return reply.ok(done);
      if (r.url === '/customer/appointments/77/review') return reply.ok({});
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/77' });
    await userEvent.click(await screen.findByRole('radio', { name: '4 out of 5 stars' }));
    await userEvent.type(screen.getByLabelText('Tell others about your experience (optional)'), 'Gentle and on time');
    await userEvent.click(screen.getByRole('button', { name: 'Post review' }));
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/appointments/77/review')?.body).toEqual({
      rating: 4, comment: 'Gentle and on time',
    }));
  });
});
