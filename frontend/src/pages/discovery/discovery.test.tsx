import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { installFakeApi, reply } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

const hit = { organizationId: 1, slug: 'smile-studio', name: 'Smile Studio', ratingAvg: 4.6, reviewCount: 12, categoryId: 1,
  subcategoryId: 2, subcategoryName: { en: 'Dental clinic' }, branchId: 3, branchName: 'Main', locality: 'Adyar', city: 'Chennai',
  latitude: 13.0, longitude: 80.2, distanceKm: 1.4, fromPrice: 500 };

function api(extra: (url: string) => ReturnType<typeof reply.ok> | undefined = () => undefined) {
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return reply.fail(401, 'SESSION_EXPIRED');
    if (r.url === '/public/config') return reply.ok({ support: null });
    if (r.url === '/public/categories') return reply.ok([]);
    if (r.url === '/public/search') return reply.ok({ items: [hit], page: 0, size: 20, hasMore: false });
    return extra(r.url);
  });
}

describe('search', () => {
  it('searches around the chosen city and shows distance, rating and price', async () => {
    const requests = api();
    renderWithProviders(<AppRoutes />, { route: '/search' });
    expect(await screen.findByRole('link', { name: 'Smile Studio' })).toHaveAttribute('href', '/b/smile-studio');
    expect(screen.getByText(/1.4 km away/)).toBeInTheDocument();
    expect(screen.getByText('4.6 (12)')).toBeInTheDocument();
    expect(screen.getByText(/from ₹500/)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Location'), 'bengaluru');
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'RATING');
    await waitFor(() => expect(requests.filter((r) => r.url === '/public/search').at(-1)?.params).toMatchObject({
      lat: 12.9716, lng: 77.5946, sort: 'RATING', radiusKm: 10,
    }));
  });
});

describe('business page', () => {
  it('shows services with prices, branch hours and contact', async () => {
    api((url) => url === '/public/organizations/smile-studio' ? reply.ok({
      id: 1, slug: 'smile-studio', name: 'Smile Studio', categoryName: { en: 'Healthcare' }, subcategoryName: { en: 'Dental clinic' },
      categoryId: 1, bookingWorkflow: 'HEALTHCARE', ratingAvg: 0, reviewCount: 0, contactPhone: '+919876543210',
      confirmationMode: 'APPROVAL',
      branches: [{ id: 3, name: 'Main', addressLine1: '1 Beach Rd', city: 'Chennai', state: 'TN', pincode: '600020', latitude: 13, longitude: 80.2,
        timezone: 'Asia/Kolkata', hours: [{ dayOfWeek: 1, opensAt: '09:00:00', closesAt: '18:00:00' }] }],
      services: [{ id: 9, name: 'Check-up', durationMinutes: 30, price: 500, consultationType: 'IN_PERSON', requiresStaff: true, branchPrices: { 3: 450 } }],
      staff: [{ id: 4, displayName: 'Meena', title: 'Dr.', branchIds: [3], serviceIds: [9] }],
    }) : undefined);
    renderWithProviders(<AppRoutes />, { route: '/b/smile-studio' });
    expect(await screen.findByRole('heading', { name: 'Smile Studio' })).toBeInTheDocument();
    expect(screen.getByText('30 min · ₹450')).toBeInTheDocument();
    expect(screen.getByText('Dr. Meena')).toBeInTheDocument();
    expect(screen.getByText('The business confirms each booking request.')).toBeInTheDocument();
  });
});
