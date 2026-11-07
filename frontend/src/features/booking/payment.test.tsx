import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

const booking = { id: 77, reference: 'BEABCDEFGH', status: 'PENDING_PAYMENT', startsAt: '2026-11-10T03:30:00Z',
  endsAt: '2026-11-10T04:00:00Z', timezone: 'Asia/Kolkata', organizationId: 1, organizationName: 'Smile Studio',
  organizationSlug: 'smile', branchId: 3, branchName: 'Main', serviceId: 9, serviceName: 'Check-up', servicePrice: 500,
  platformFee: 25, totalAmount: 525, rescheduleCount: 0, paid: false, createdAt: new Date().toISOString() };

// Record what each QR code contains (the image itself isn't needed in tests).
const qrTexts: string[] = [];
vi.mock('qrcode', () => ({
  default: { toDataURL: async (text: string) => { qrTexts.push(text); return 'data:image/png;base64,AAAA'; } },
}));

const inTenMinutes = () => new Date(Date.now() + 10 * 60000).toISOString();

function api(order: object, onPay: (url: string, body: unknown) => void) {
  let status = 'PENDING_PAYMENT';
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', sampleUser));
    if (r.url === '/public/config') return reply.ok({ support: null });
    if (r.url === '/customer/appointments/77') return reply.ok({ ...booking, status, paid: status !== 'PENDING_PAYMENT' });
    if (r.url === '/customer/appointments/77/payment-order') return reply.ok(order);
    if (r.url === '/customer/payments/verify' || r.url === '/customer/appointments/77/demo-payment') {
      onPay(r.url, r.body);
      status = 'CONFIRMED';
      return reply.ok(null);
    }
    return undefined;
  });
}

afterEach(() => {
  delete window.Razorpay;
});

describe('payment page', () => {
  it('shows the bill and methods, opens Razorpay with the chosen method and verifies on the server', async () => {
    const paid: unknown[] = [];
    api({ keyId: 'rzp_test_key', orderId: 'order_1', amountPaise: 52500, currency: 'INR', reference: 'BEABCDEFGH',
      description: 'Check-up', expiresAt: inTenMinutes(), demo: false }, (_u, body) => paid.push(body));
    let options: Record<string, unknown> = {};
    window.Razorpay = class {
      constructor(o: Record<string, unknown>) { options = o; }
      open() {
        (options.handler as (r: object) => void)({ razorpay_order_id: 'order_1', razorpay_payment_id: 'pay_9', razorpay_signature: 'sig' });
      }
      on() {}
    };

    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/77/pay' });
    expect(await screen.findByRole('heading', { name: 'Payment' })).toBeInTheDocument();
    expect(screen.getByText('Smile Studio')).toBeInTheDocument();
    expect(await screen.findByRole('timer')).toHaveTextContent(/Slot held for \d+:\d\d/);
    for (const m of ['UPI', 'Credit / debit card', 'Net banking', 'Wallets']) expect(screen.getByText(m)).toBeInTheDocument();
    expect(screen.queryByText(/Demo mode/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: /Credit \/ debit card/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹525' }));

    await waitFor(() => expect(paid).toEqual([{ orderId: 'order_1', paymentId: 'pay_9', signature: 'sig' }]));
    expect(options).toMatchObject({ key: 'rzp_test_key', order_id: 'order_1', amount: 52500, prefill: { method: 'card' } });
    expect(await screen.findByText('Confirmed')).toBeInTheDocument();
  });

  it('demo UPI: Continue shows a QR, Next confirms and opens the booking details', async () => {
    const paid: unknown[] = [];
    api({ keyId: null, orderId: 'demo_order_x', amountPaise: 52500, currency: 'INR', reference: 'BEABCDEFGH',
      description: 'Check-up', expiresAt: inTenMinutes(), demo: true }, (url, body) => paid.push({ url, body }));

    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/77/pay' });
    expect(await screen.findByText(/Demo mode/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Continue/ })); // UPI is pre-selected

    expect(await screen.findByRole('heading', { name: 'Scan & pay with UPI' })).toBeInTheDocument();
    expect(await screen.findByTestId('payment-qr')).toBeInTheDocument();
    expect(qrTexts.at(-1)).toContain('DEMO');
    expect(qrTexts.at(-1)).not.toMatch(/^upi:/i); // never a payable UPI request
    expect(paid).toEqual([]); // nothing is paid until Next

    await userEvent.click(screen.getByRole('button', { name: /Next/ }));
    expect(await screen.findByText('Confirmed', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText("You're booked!")).toBeInTheDocument();
    expect(paid).toEqual([{ url: '/customer/appointments/77/demo-payment', body: { method: 'upi' } }]);
  });

  it('demo net banking: no QR, Next completes it; Change method goes back', async () => {
    const paid: unknown[] = [];
    api({ keyId: null, orderId: 'demo_order_x', amountPaise: 52500, currency: 'INR', reference: 'BEABCDEFGH',
      description: 'Check-up', expiresAt: inTenMinutes(), demo: true }, (url, body) => paid.push({ url, body }));

    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/77/pay' });
    await userEvent.click(await screen.findByRole('radio', { name: /Net banking/ }));
    await userEvent.click(screen.getByRole('button', { name: /Continue/ }));
    expect(await screen.findByText(/Demo Net banking payment/)).toBeInTheDocument();
    expect(screen.queryByTestId('payment-qr')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Change payment method/ }));
    expect(screen.getByRole('radio', { name: /Net banking/ })).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: /Continue/ }));
    await userEvent.click(screen.getByRole('button', { name: /Next/ }));

    expect(await screen.findByText('Confirmed', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(paid).toEqual([{ url: '/customer/appointments/77/demo-payment', body: { method: 'netbanking' } }]);
  });

  it('the booking page links to the payment page while payment is pending', async () => {
    api({ demo: true, expiresAt: inTenMinutes() }, () => {});
    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/77' });
    expect(await screen.findByRole('link', { name: 'Continue to payment' })).toHaveAttribute('href', '/customer/bookings/77/pay');
  });
});
