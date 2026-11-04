import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

const booking = { id: 77, reference: 'BEABCDEFGH', status: 'PENDING_PAYMENT', startsAt: '2026-11-10T03:30:00Z',
  endsAt: '2026-11-10T04:00:00Z', timezone: 'Asia/Kolkata', organizationId: 1, organizationName: 'Smile Studio',
  organizationSlug: 'smile', branchId: 3, branchName: 'Main', serviceId: 9, serviceName: 'Check-up', servicePrice: 500,
  platformFee: 25, totalAmount: 525, rescheduleCount: 0, paid: false, createdAt: new Date().toISOString() };

describe('online payment', () => {
  it('opens Razorpay Checkout and verifies the signed result on the server', async () => {
    let status = 'PENDING_PAYMENT';
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', sampleUser));
      if (r.url === '/public/config') return reply.ok({ support: null });
      if (r.url === '/customer/appointments/77') return reply.ok({ ...booking, status, paid: status !== 'PENDING_PAYMENT' });
      if (r.url === '/customer/appointments/77/payment-order') {
        return reply.ok({ keyId: 'rzp_test_key', orderId: 'order_1', amountPaise: 52500, currency: 'INR', reference: 'BEABCDEFGH',
          description: 'Check-up', expiresAt: '' });
      }
      if (r.url === '/customer/payments/verify') {
        status = 'CONFIRMED';
        return reply.ok(null);
      }
      return undefined;
    });
    let options: Record<string, unknown> = {};
    window.Razorpay = class {
      constructor(o: Record<string, unknown>) { options = o; }
      open() {
        (options.handler as (r: object) => void)({ razorpay_order_id: 'order_1', razorpay_payment_id: 'pay_9', razorpay_signature: 'sig' });
      }
      on() {}
    };

    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/77' });
    await userEvent.click(await screen.findByRole('button', { name: 'Pay ₹525' }));
    await waitFor(() => expect(requests.find((r) => r.url === '/customer/payments/verify')?.body).toEqual({
      orderId: 'order_1', paymentId: 'pay_9', signature: 'sig',
    }));
    expect(options).toMatchObject({ key: 'rzp_test_key', order_id: 'order_1', amount: 52500 });
    expect(await screen.findByText('Confirmed')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View receipt' })).toBeInTheDocument();
    delete window.Razorpay;
  });
});
