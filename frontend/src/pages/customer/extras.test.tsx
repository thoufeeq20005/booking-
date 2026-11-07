import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { authenticated, installFakeApi, reply, sampleUser } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

function api(handler: (url: string, method: string, body: unknown) => ReturnType<typeof reply.ok> | undefined) {
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', sampleUser));
    if (r.url === '/public/config') return reply.ok({ support: null });
    return handler(r.url, r.method, r.body);
  });
}

describe('family members', () => {
  it('adds a family member', async () => {
    let list: unknown[] = [];
    const requests = api((url, method) => {
      if (url === '/customer/family' && method === 'GET') return reply.ok(list);
      if (url === '/customer/family' && method === 'POST') {
        list = [{ id: 1, fullName: 'Ravi Kumar', relation: 'CHILD' }];
        return reply.ok(list[0]);
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/customer/family' });
    await userEvent.type(await screen.findByLabelText('Full name'), 'Ravi Kumar');
    await userEvent.click(screen.getByRole('button', { name: 'Add family member' }));
    expect(await screen.findByText('Ravi Kumar')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST' && r.url === '/customer/family')?.body).toEqual({ fullName: 'Ravi Kumar', relation: 'CHILD' });
  });
});

describe('support', () => {
  it('sends a support request', async () => {
    const requests = api((url, method) => {
      if (url === '/support-requests' && method === 'GET') return reply.ok([]);
      if (url === '/customer/appointments') return reply.ok([]);
      if (url === '/support-requests' && method === 'POST') return reply.ok({});
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/customer/support' });
    await userEvent.type(await screen.findByLabelText('Subject'), 'Refund');
    await userEvent.type(screen.getByLabelText('How can we help?'), 'Not received yet');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    await waitFor(() => expect(requests.find((r) => r.method === 'POST' && r.url === '/support-requests')?.body).toEqual({
      subject: 'Refund', message: 'Not received yet',
    }));
    expect(await screen.findByText("Thanks! We'll reply here and by email.")).toBeInTheDocument();
  });
});
