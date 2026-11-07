import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

const qrTexts: string[] = [];
vi.mock('qrcode', () => ({
  default: { toDataURL: async (text: string) => { qrTexts.push(text); return 'data:image/png;base64,AAAA'; } },
}));

const payload = 'BOOKEASY:BEJ5AGLLET:0123456789abcdef0123';

describe('booking pass QR', () => {
  it('the booking QR is a link that opens the pass page', async () => {
    installFakeApi((r) => {
      if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', sampleUser));
      if (r.url === '/customer/appointments/374') {
        return reply.ok({ id: 374, reference: 'BEJ5AGLLET', status: 'CONFIRMED', startsAt: '2026-11-07T11:45:00Z',
          endsAt: '2026-11-07T12:05:00Z', timezone: 'Asia/Kolkata', organizationId: 1, organizationName: 'Marudhamalai Vision Centre',
          organizationSlug: 'mvc', branchId: 3, branchName: 'Main branch', serviceId: 9, serviceName: 'Cataract consultation',
          servicePrice: 600, platformFee: 30, totalAmount: 630, rescheduleCount: 0, paid: true, qrPayload: payload,
          createdAt: '2026-11-06T10:00:00Z' });
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/customer/bookings/374' });
    expect(await screen.findByAltText(/booking/i)).toBeInTheDocument();
    expect(qrTexts.at(-1)).toBe(`${window.location.origin}/pass/${encodeURIComponent(payload)}`);
  });

  it('scanning opens the public pass with the booking details, no sign-in needed', async () => {
    const requests = installFakeApi((r) =>
      r.url.startsWith('/public/passes/')
        ? reply.ok({ reference: 'BEJ5AGLLET', status: 'CONFIRMED', startsAt: '2026-11-07T11:45:00Z', endsAt: '2026-11-07T12:05:00Z',
          timezone: 'Asia/Kolkata', organizationName: 'Marudhamalai Vision Centre', organizationSlug: 'mvc', branchName: 'Main branch',
          branchAddress: 'No. 243, Car Street, R.S. Puram, Coimbatore', serviceName: 'Cataract consultation',
          staffName: 'Dr. Swathi Chandran', guestFirstName: 'Asha', paid: true })
        : undefined);
    renderWithProviders(<AppRoutes />, { route: `/pass/${encodeURIComponent(payload)}` });

    expect(await screen.findByRole('heading', { name: 'Cataract consultation' })).toBeInTheDocument();
    expect(screen.getByText('BEJ5AGLLET')).toBeInTheDocument();
    expect(screen.getByText(/Marudhamalai Vision Centre/)).toBeInTheDocument();
    expect(screen.getByText('Dr. Swathi Chandran')).toBeInTheDocument();
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
    expect(screen.getByText('Paid')).toBeInTheDocument();
    expect(requests.find((r) => r.url.startsWith('/public/passes/'))!.url)
      .toBe(`/public/passes/${encodeURIComponent(payload)}`);
  });
});
