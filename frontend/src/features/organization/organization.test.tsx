import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { PublicLayout } from '@/layouts/PublicLayout';
import { Route, Routes } from 'react-router';
import { authenticated, installFakeApi, reply, sampleUser, type FakeRequest, type FakeResponse } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { OrganizationDetail } from '@/services/organizationApi';

const owner = { ...sampleUser, roles: ['BUSINESS_OWNER', 'CUSTOMER'], emailVerified: true };
const admin = { ...sampleUser, id: 9, fullName: 'Admin', roles: ['SUPER_ADMIN'] };

const categories = [
  {
    id: 1, slug: 'healthcare', name: { en: 'Healthcare', ta: 'மருத்துவம்' }, icon: 'stethoscope',
    bookingWorkflow: 'HEALTHCARE', active: true, sortOrder: 10,
    subcategories: [{ id: 2, categoryId: 1, slug: 'dental-clinic', name: { en: 'Dental clinic' }, active: true, sortOrder: 20 }],
  },
];

const requirement = {
  id: 7, categoryId: 1, code: 'OWNER_ID_PROOF', label: { en: 'Owner ID proof' }, mandatory: true,
  allowedTypes: ['application/pdf', 'image/png'], maxSizeBytes: 5 * 1024 * 1024, active: true, sortOrder: 10,
};

function detail(overrides: Partial<OrganizationDetail> & { status?: OrganizationDetail['summary']['status']; reason?: string } = {}): OrganizationDetail {
  const { status = 'DRAFT', reason, ...rest } = overrides;
  return {
    summary: {
      id: 42, name: 'Sunrise Dental', slug: 'sunrise-dental', status, statusReason: reason, categoryId: 1,
      categoryName: { en: 'Healthcare' }, subcategoryId: 2, subcategoryName: { en: 'Dental clinic' },
      createdAt: '2026-11-04T10:00:00Z', updatedAt: '2026-11-04T10:00:00Z',
      ownerName: 'Asha Kumar', ownerEmail: 'asha@example.test',
    },
    contactPhone: '+919876543210',
    confirmationMode: 'INSTANT',
    editable: { identity: status === 'DRAFT' || status === 'REJECTED', operations: status !== 'PENDING' && status !== 'SUSPENDED' },
    missingSteps: [],
    branches: [],
    documents: [{ requirement }],
    history: [],
    owners: [{ userId: 1, fullName: 'Asha Kumar', email: 'asha@example.test', emailVerified: true, phoneVerified: false, role: 'PRIMARY' }],
    ...rest,
  } as OrganizationDetail;
}

function api(user: object, handler: (r: FakeRequest) => FakeResponse | undefined) {
  return installFakeApi((r) => {
    if (r.url === '/auth/refresh') return reply.ok(authenticated('tok', user));
    if (r.url === '/public/config') return reply.ok({ support: null });
    return handler(r);
  });
}

describe('business registration', () => {
  it('creates a draft, refreshes the account roles and opens the setup page', async () => {
    let me = { ...sampleUser };
    const requests = api(sampleUser, (r) => {
      if (r.url === '/public/categories') return reply.ok(categories);
      if (r.url === '/business/organizations') {
        me = owner;
        return reply.ok(detail());
      }
      if (r.url === '/account/me') return reply.ok(me);
      if (r.url === '/owner/organizations/42') return reply.ok(detail());
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/business/register' });

    await userEvent.selectOptions(await screen.findByLabelText('Category'), 'Dental clinic');
    await userEvent.type(screen.getByLabelText('Business name'), 'Sunrise Dental');
    await userEvent.type(screen.getByLabelText('Business mobile number'), '98765 43210');
    await userEvent.click(screen.getByLabelText(/After I approve/));
    await userEvent.click(screen.getByRole('button', { name: 'Create and continue' }));

    expect(await screen.findByRole('heading', { name: 'Sunrise Dental' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Branches & hours/, selected: true })).toBeInTheDocument();
    expect(requests.find((r) => r.url === '/business/organizations')!.body).toEqual({
      subcategoryId: 2,
      name: 'Sunrise Dental',
      contactPhone: '98765 43210',
      confirmationMode: 'APPROVAL',
    });
  });

  it('shows field errors from the server next to the field', async () => {
    api(sampleUser, (r) => {
      if (r.url === '/public/categories') return reply.ok(categories);
      if (r.url === '/business/organizations') {
        return reply.fail(400, 'VALIDATION_FAILED', [{ field: 'gstin', code: 'INVALID_GSTIN', message: 'x' }]);
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/business/register' });
    await userEvent.selectOptions(await screen.findByLabelText('Category'), 'Dental clinic');
    await userEvent.type(screen.getByLabelText('Business name'), 'X');
    await userEvent.type(screen.getByLabelText('GSTIN'), 'bad');
    await userEvent.click(screen.getByRole('button', { name: 'Create and continue' }));
    expect(await screen.findByText('Enter a valid 15-character GSTIN.')).toBeInTheDocument();
  });

  it('asks anonymous visitors to sign in first', async () => {
    installFakeApi((r) => (r.url === '/auth/refresh' ? reply.fail(401, 'SESSION_EXPIRED') : undefined));
    renderWithProviders(<AppRoutes />, { route: '/business/register' });
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
  });
});

describe('owner setup', () => {
  it('lists missing steps and only enables submit when complete', async () => {
    let current = detail({ missingSteps: ['BRANCH', 'DOCUMENTS'] });
    api(owner, (r) => {
      if (r.url === '/owner/organizations/42') return reply.ok(current);
      if (r.url === '/owner/organizations/42/submit') return reply.ok(detail({ status: 'PENDING' }));
      return undefined;
    });
    const view = renderWithProviders(<AppRoutes />, { route: '/owner/organizations/42?step=review' });

    const submit = await screen.findByRole('button', { name: 'Submit for verification' });
    expect(submit).toBeDisabled();
    expect(screen.getAllByRole('button', { name: 'Fix' })).toHaveLength(2);
    view.unmount();

    current = detail();
    renderWithProviders(<AppRoutes />, { route: '/owner/organizations/42?step=review' });
    await userEvent.click(await screen.findByRole('button', { name: 'Submit for verification' }));
    expect(await screen.findByText(/Our team is reviewing your business/)).toBeInTheDocument();
  });

  it('shows the rejection reason and the reviewer note on the document', async () => {
    api(owner, (r) => {
      if (r.url === '/owner/organizations/42') {
        return reply.ok(detail({
          status: 'REJECTED', reason: 'ID scan is blurred',
          documents: [{ requirement, document: { id: 3, fileName: 'id.pdf', contentType: 'application/pdf',
            sizeBytes: 2048, status: 'REJECTED', reviewerNote: 'Too blurry', uploadedAt: '2026-11-04T10:00:00Z' } }],
        }));
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/owner/organizations/42?step=documents' });
    expect(await screen.findByText('Your application needs changes: ID scan is blurred')).toBeInTheDocument();
    expect(screen.getByText("Reviewer's note: Too blurry")).toBeInTheDocument();
    expect(screen.getByText('Rejected')).toBeInTheDocument();
    expect(screen.getByText('Replace')).toBeInTheDocument();
  });

  it('saves weekly hours for a branch', async () => {
    const branch = { id: 5, name: 'Main', addressLine1: '12 Anna Salai', city: 'Chennai', state: 'Tamil Nadu',
      pincode: '600018', latitude: 13.04, longitude: 80.23, timezone: 'Asia/Kolkata', active: true, hours: [] };
    const requests = api(owner, (r) => {
      if (r.url === '/owner/organizations/42') return reply.ok(detail({ branches: [branch] }));
      if (r.url === '/owner/organizations/42/branches/5/hours') return reply.ok({ ...branch, hours: [] });
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/owner/organizations/42?step=branches' });
    expect(await screen.findByText('Opening hours not set')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Hours' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Mon' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save hours' }));
    await waitFor(() =>
      expect(requests.find((r) => r.url === '/owner/organizations/42/branches/5/hours')?.body).toEqual({
        days: [{ dayOfWeek: 1, opensAt: '09:00', closesAt: '18:00' }],
      }),
    );
  });

  it('keeps other owners out of the owner area', async () => {
    api(sampleUser, () => undefined);
    renderWithProviders(<AppRoutes />, { route: '/owner/organizations' });
    expect(await screen.findByText("You don't have permission to do that.")).toBeInTheDocument();
  });
});

describe('admin review', () => {
  it('approves a pending business', async () => {
    const requests = api(admin, (r) => {
      if (r.url === '/admin/organizations/42' && r.method === 'GET') return reply.ok(detail({ status: 'PENDING' }));
      if (r.url === '/admin/organizations/42/approve') return reply.ok(detail({ status: 'APPROVED' }));
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/admin/organizations/42' });
    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm approval' }));
    expect(await screen.findByText('Approved')).toBeInTheDocument();
    expect(requests.some((r) => r.url === '/admin/organizations/42/approve' && r.method === 'POST')).toBe(true);
  });

  it('requires a reason before sending a business back', async () => {
    const requests = api(admin, (r) => {
      if (r.url === '/admin/organizations/42' && r.method === 'GET') return reply.ok(detail({ status: 'PENDING' }));
      if (r.url === '/admin/organizations/42/reject') return reply.ok(detail({ status: 'REJECTED', reason: r.body?.reason as string }));
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/admin/organizations/42' });
    await userEvent.click(await screen.findByRole('button', { name: 'Request changes' }));
    const send = screen.getByRole('button', { name: 'Send back to owner' });
    expect(send).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Reason (sent to the owner)'), 'Upload a clear licence');
    await userEvent.click(send);
    await waitFor(() => expect(requests.find((r) => r.url === '/admin/organizations/42/reject')?.body).toEqual({
      reason: 'Upload a clear licence',
    }));
  });

  it('lists the pending queue', async () => {
    api(admin, (r) => {
      if (r.url === '/admin/organizations') {
        return reply.ok({ items: [detail({ status: 'PENDING' }).summary], page: 0, size: 20, totalItems: 1, totalPages: 1 });
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/admin/organizations' });
    const row = (await screen.findByRole('link', { name: 'Sunrise Dental' })).closest('tr')!;
    expect(within(row).getByText('asha@example.test')).toBeInTheDocument();
    expect(within(row).getByText('Under review')).toBeInTheDocument();
  });
});

describe('admin categories', () => {
  it('creates a category with only the filled-in translations', async () => {
    const requests = api(admin, (r) => {
      if (r.url === '/admin/categories' && r.method === 'GET') return reply.ok(categories);
      if (r.url === '/admin/categories' && r.method === 'POST') return reply.ok(categories);
      return undefined;
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderWithProviders(<AppRoutes />, { route: '/admin/categories' });
    await userEvent.click(await screen.findByRole('button', { name: 'Add category' }));
    await userEvent.type(screen.getByLabelText('Slug'), 'Pet-Care');
    await userEvent.type(screen.getAllByLabelText('English *')[0]!, 'Pet care');
    await userEvent.selectOptions(screen.getByLabelText('Icon'), 'paw-print');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(requests.find((r) => r.method === 'POST' && r.url === '/admin/categories')?.body).toEqual({
      slug: 'pet-care', name: { en: 'Pet care' }, description: {}, icon: 'paw-print', bookingWorkflow: 'GENERIC',
      active: true, sortOrder: 100,
    }));
  });
});

describe('header', () => {
  it('shows owners their businesses and admins the admin area', async () => {
    api({ ...owner, roles: ['BUSINESS_OWNER', 'SUPER_ADMIN'] }, () => undefined);
    renderWithProviders(
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<p>home</p>} />
        </Route>
      </Routes>,
    );
    expect(await screen.findAllByRole('link', { name: 'My businesses' })).not.toHaveLength(0);
    expect(screen.getAllByRole('link', { name: 'Admin' })[0]).toHaveAttribute('href', '/admin/organizations');
  });
});
