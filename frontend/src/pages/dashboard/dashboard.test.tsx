import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { homeFor } from '@/services/dashboardApi';
import { authenticated, installFakeApi, reply, sampleUser, type FakeRequest, type FakeResponse } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

const admin = { ...sampleUser, id: 9, fullName: 'Admin Ravi', roles: ['SUPER_ADMIN'] };
const owner = { ...sampleUser, roles: ['BUSINESS_OWNER', 'CUSTOMER'] };

function api(user: object, handler: (r: FakeRequest) => FakeResponse | undefined = () => undefined) {
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', user));
    if (r.url === '/public/config') return reply.ok({ support: null });
    return handler(r);
  });
}

const adminDashboard = {
  users: { total: 1250, newLast7Days: 12, suspended: 2, byRole: { CUSTOMER: 1200, BUSINESS_OWNER: 40, STAFF: 0, SUPER_ADMIN: 2 } },
  organizations: { DRAFT: 5, PENDING: 3, APPROVED: 30, REJECTED: 1, SUSPENDED: 0 },
  reviewQueue: { pending: 3, oldestSubmittedAt: '2026-11-01T05:00:00Z', oldestOrganizationId: 7,
    oldestOrganizationName: 'Lotus Clinic', documentsAwaitingReview: 8 },
  recentActivity: [{ id: 1, createdAt: '2026-11-04T10:00:00Z', actorUserId: 9, actorName: 'Admin Ravi',
    actorRole: 'SUPER_ADMIN', action: 'ORGANIZATION_APPROVED', entityType: 'ORGANIZATION', entityId: '7' }],
};

describe('dashboard landing', () => {
  it.each([
    [['CUSTOMER'], '/customer'],
    [['CUSTOMER', 'BUSINESS_OWNER'], '/owner'],
    [['STAFF'], '/staff'],
    [['BUSINESS_OWNER', 'SUPER_ADMIN'], '/admin'],
  ])('%j lands on %s', (roles, home) => {
    expect(homeFor(roles as never)).toBe(home);
  });

  it('sends a Super Admin from /dashboard to the platform overview', async () => {
    api(admin, (r) => (r.url === '/admin/dashboard' ? reply.ok(adminDashboard) : undefined));
    renderWithProviders(<AppRoutes />, { route: '/dashboard' });
    expect(await screen.findByRole('heading', { name: 'Platform overview' })).toBeInTheDocument();
  });

  it('shows staff an honest empty state until they are invited', async () => {
    api({ ...sampleUser, roles: ['STAFF'] }, (r) => (r.url === '/staff/workplaces' ? reply.ok([]) : undefined));
    renderWithProviders(<AppRoutes />, { route: '/dashboard' });
    expect(await screen.findByText("You're not part of a business team yet.")).toBeInTheDocument();
  });

  it('keeps customers out of the admin area', async () => {
    api(sampleUser);
    renderWithProviders(<AppRoutes />, { route: '/admin/users' });
    expect(await screen.findByText("You don't have permission to do that.")).toBeInTheDocument();
  });
});

describe('customer overview', () => {
  it('lists what is left to secure the account and offers business registration', async () => {
    api({ ...sampleUser, emailVerified: true });
    renderWithProviders(<AppRoutes />, { route: '/customer' });
    expect(await screen.findByRole('heading', { name: 'Hello, Asha Kumar' })).toBeInTheDocument();
    // Only two-step verification is left; email/phone verification are no longer checklist items.
    expect(screen.getByText('Steps left: 1')).toBeInTheDocument();
    expect(screen.queryByText('Mobile number verified')).not.toBeInTheDocument();
    expect(screen.getByText('You have no upcoming appointments.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'List your business' })).toHaveAttribute('href', '/business/register');
  });
});

describe('admin overview', () => {
  it('shows live numbers that link to the right lists', async () => {
    api(admin, (r) => (r.url === '/admin/dashboard' ? reply.ok(adminDashboard) : undefined));
    renderWithProviders(<AppRoutes />, { route: '/admin' });
    const pending = (await screen.findByText('Waiting for review')).closest('a')!;
    expect(pending).toHaveAttribute('href', '/admin/organizations?status=PENDING');
    expect(within(pending).getByText('3')).toBeInTheDocument();
    expect(screen.getByText('1,250')).toBeInTheDocument();
    expect(screen.getByText('+12 in the last 7 days')).toBeInTheDocument();
    expect(screen.getByText('Business approved')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Admin' })).toBeInTheDocument();
  });
});

describe('owner overview', () => {
  it('shows each business with document progress and the next step', async () => {
    api(owner, (r) =>
      r.url === '/owner/dashboard'
        ? reply.ok({
            byStatus: { DRAFT: 1 },
            businesses: [{
              summary: { id: 4, name: 'Sunrise Dental', slug: 's', status: 'DRAFT', subcategoryId: 2,
                subcategoryName: { en: 'Dental clinic' }, city: 'Chennai', createdAt: '', updatedAt: '' },
              missingSteps: ['DOCUMENTS'], activeBranches: 1, branchesWithoutHours: 0,
              documents: { required: 3, uploaded: 1, accepted: 0, rejected: 0 },
            }],
            upcomingHolidays: [{ organizationId: 4, organizationName: 'Sunrise Dental', branchId: 1, branchName: 'Main',
              date: '2026-11-10', reason: 'Diwali' }],
          })
        : undefined,
    );
    renderWithProviders(<AppRoutes />, { route: '/owner' });
    expect(await screen.findByRole('heading', { name: 'Business overview' })).toBeInTheDocument();
    expect(screen.getByText('1 of 3 uploaded')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '33');
    expect(screen.getByText('All required documents (none rejected)')).toBeInTheDocument();
    expect(screen.getByText(/Sunrise Dental · Main · Diwali/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Sunrise Dental/ })).toHaveAttribute('href', '/owner/organizations/4?step=review');
  });
});

describe('admin users', () => {
  const user2fa = { ...sampleUser, id: 21, fullName: 'Meena Iyer', email: 'meena@example.test', twoFactorEnabled: true };

  it('resets two-step verification only with a recorded reason', async () => {
    const requests = api(admin, (r) => {
      if (r.url === '/admin/users') return reply.ok({ items: [user2fa], page: 0, size: 20, totalItems: 1, totalPages: 1 });
      if (r.url === '/admin/users/21/two-factor/reset') return reply.ok({ ...user2fa, twoFactorEnabled: false });
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/admin/users?role=CUSTOMER' });
    await userEvent.click(await screen.findByRole('button', { name: 'Reset two-step' }));
    const confirm = screen.getByRole('button', { name: 'Turn off two-step verification' });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText('How did you confirm their identity?'), 'Video call with Aadhaar');
    await userEvent.click(confirm);
    await waitFor(() =>
      expect(requests.find((r) => r.url === '/admin/users/21/two-factor/reset')?.body).toEqual({ reason: 'Video call with Aadhaar' }),
    );
    expect(requests.find((r) => r.url === '/admin/users')?.params).toMatchObject({ role: 'CUSTOMER' });
  });

  it('does not offer actions on your own account', async () => {
    api(admin, (r) =>
      r.url === '/admin/users' ? reply.ok({ items: [admin], page: 0, size: 20, totalItems: 1, totalPages: 1 }) : undefined,
    );
    renderWithProviders(<AppRoutes />, { route: '/admin/users' });
    expect(await screen.findByText('Admin Ravi')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Suspend' })).not.toBeInTheDocument();
  });
});

describe('audit log', () => {
  it('filters by action and shows readable entries', async () => {
    const requests = api(admin, (r) =>
      r.url === '/admin/audit-logs'
        ? reply.ok({ items: [{ ...adminDashboard.recentActivity[0], details: { from: 'PENDING', to: 'APPROVED' } }],
            page: 0, size: 50, totalItems: 1, totalPages: 1 })
        : undefined,
    );
    renderWithProviders(<AppRoutes />, { route: '/admin/audit' });
    expect(await screen.findByText('from: PENDING · to: APPROVED')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Action code'), 'organization_approved');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() =>
      expect(requests.filter((r) => r.url === '/admin/audit-logs').at(-1)?.params).toMatchObject({ action: 'ORGANIZATION_APPROVED' }),
    );
  });
});
