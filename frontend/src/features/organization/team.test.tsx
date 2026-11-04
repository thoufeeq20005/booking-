import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser, type FakeRequest, type FakeResponse } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

const owner = { ...sampleUser, roles: ['BUSINESS_OWNER', 'CUSTOMER'] };
const branch = { id: 5, name: 'Main', addressLine1: 'x', city: 'Chennai', state: 'TN', pincode: '600018',
  latitude: 13, longitude: 80, timezone: 'Asia/Kolkata', active: true, hours: [] };
const org = {
  summary: { id: 42, name: 'Sunrise Dental', slug: 's', status: 'DRAFT', subcategoryId: 2, createdAt: '', updatedAt: '' },
  confirmationMode: 'INSTANT', editable: { identity: true, operations: true }, missingSteps: [],
  branches: [branch], documents: [], history: [],
};
const service = { id: 9, name: 'Check-up', durationMinutes: 30, price: 500, consultationType: 'IN_PERSON',
  requiresStaff: true, bufferMinutes: 0, slotIntervalMinutes: 30, maxAdvanceDays: 60, minNoticeMinutes: 60, active: true,
  branches: [{ branchId: 5, priceOverride: null }] };

function api(user: object, handler: (r: FakeRequest) => FakeResponse | undefined) {
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', user));
    if (r.url === '/public/config') return reply.ok({ support: null });
    if (r.url === '/owner/organizations/42') return reply.ok(org);
    return handler(r);
  });
}

describe('services tab', () => {
  it('creates a service offered at the branch and previews slots', async () => {
    let list: unknown[] = [];
    const requests = api(owner, (r) => {
      if (r.url === '/owner/organizations/42/services' && r.method === 'GET') return reply.ok(list);
      if (r.url === '/owner/organizations/42/services' && r.method === 'POST') {
        list = [service];
        return reply.ok(service);
      }
      if (r.url === '/owner/organizations/42/slots') {
        return reply.ok({ date: r.params.date, timezone: 'Asia/Kolkata', price: 500, durationMinutes: 30, outcome: 'OPEN', staff: [],
          slots: [{ time: '09:00', startsAt: '', endsAt: '', staffIds: [1] }, { time: '09:30', startsAt: '', endsAt: '', staffIds: [1] }] });
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/owner/organizations/42?step=services' });
    await userEvent.click(await screen.findByRole('button', { name: 'Add a service' }));
    await userEvent.type(screen.getByLabelText('Service name'), 'Check-up');
    await userEvent.type(screen.getByLabelText('Price (₹)'), '500');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(requests.find((r) => r.method === 'POST' && r.url.endsWith('/services'))?.body).toMatchObject({
      name: 'Check-up', price: '500', durationMinutes: 30, requiresStaff: true, branches: [{ branchId: 5, priceOverride: null }],
    }));
    expect(await screen.findByText('09:30')).toBeInTheDocument();
    expect(screen.getByText(/2 slots/)).toBeInTheDocument();
  });
});

describe('staff tab', () => {
  it('invites a staff member with assignments and permissions', async () => {
    const requests = api(owner, (r) => {
      if (r.url === '/owner/organizations/42/staff' && r.method === 'GET') return reply.ok([]);
      if (r.url === '/owner/organizations/42/services') return reply.ok([service]);
      if (r.url === '/owner/organizations/42/staff/invitations') return reply.ok({});
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/owner/organizations/42?step=staff' });
    await userEvent.click(await screen.findByRole('button', { name: 'Invite a staff member' }));
    await userEvent.type(screen.getByLabelText('Email address'), 'meena@example.test');
    await userEvent.type(screen.getByLabelText('Name shown to customers'), 'Meena Iyer');
    await userEvent.click(screen.getByLabelText('Edit own hours and leave'));
    await userEvent.click(await screen.findByRole('button', { name: 'Send invitation' }));
    await waitFor(() => expect(requests.find((r) => r.url.endsWith('/staff/invitations'))?.body).toEqual({
      email: 'meena@example.test',
      profile: { displayName: 'Meena Iyer', permissions: ['CAN_EDIT_HOURS'], branchIds: [5], serviceIds: [9] },
    }));
  });
});

describe('invitation page', () => {
  it('asks visitors to sign in, then lets the invited account accept', async () => {
    const invitee = { ...sampleUser, email: 'meena@example.test' };
    const requests = api(invitee, (r) => {
      if (r.url === '/public/staff-invitations/preview') {
        return reply.ok({ organizationName: 'Sunrise Dental', displayName: 'Meena Iyer', email: 'meena@example.test', expiresAt: '' });
      }
      if (r.url === '/staff-invitations/accept') return reply.ok({});
      if (r.url === '/account/me') return reply.ok({ ...invitee, roles: ['CUSTOMER', 'STAFF'] });
      if (r.url === '/staff/workplaces') return reply.ok([]);
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/staff/invitations/accept?token=abc' });
    expect(await screen.findByRole('heading', { name: 'Join Sunrise Dental on BookEasy' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Accept invitation' }));
    await waitFor(() => expect(requests.find((r) => r.url === '/staff-invitations/accept')?.body).toEqual({ token: 'abc' }));
    expect(await screen.findByText("You're not part of a business team yet.")).toBeInTheDocument();
  });

  it('warns when signed in with a different email', async () => {
    api(sampleUser, (r) => r.url === '/public/staff-invitations/preview'
      ? reply.ok({ organizationName: 'Sunrise Dental', displayName: 'Meena', email: 'meena@example.test', expiresAt: '' })
      : undefined);
    renderWithProviders(<AppRoutes />, { route: '/staff/invitations/accept?token=abc' });
    expect(await screen.findByText('Sign in with the email address the invitation was sent to.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Accept invitation' })).not.toBeInTheDocument();
  });
});
