import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '@/routes/AppRoutes';
import { safeNext } from '@/routes/RequireAuth';
import { authenticated, installFakeApi, reply, sampleUser } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('sign in', () => {
  it('signs in with email and password, then lands on the requested page', async () => {
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/login') return reply.ok(authenticated());
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/signin?next=%2Faccount' });

    await userEvent.type(await screen.findByLabelText('Email address'), 'asha@example.test');
    await userEvent.type(screen.getByLabelText('Password'), 'Sunrise2026');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(screen.getByText('Signed in as Asha Kumar')).toBeInTheDocument();
    expect(requests.find((r) => r.url === '/auth/login')!.body).toEqual({
      email: 'asha@example.test',
      password: 'Sunrise2026',
    });
  });

  it('shows a translated error for wrong credentials', async () => {
    installFakeApi((r) => (r.url === '/auth/login' ? reply.fail(401, 'INVALID_CREDENTIALS') : undefined));
    renderWithProviders(<AppRoutes />, { route: '/signin' });

    await userEvent.type(await screen.findByLabelText('Email address'), 'a@b.test');
    await userEvent.type(screen.getByLabelText('Password'), 'Wrong12345');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
  });

  it('asks for the authenticator code when two-step verification is on', async () => {
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/login') return reply.ok({ status: 'TWO_FACTOR_REQUIRED', challengeToken: 'chal-1' });
      if (r.url === '/auth/login/two-factor') return reply.ok(authenticated());
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/signin' });

    await userEvent.type(await screen.findByLabelText('Email address'), 'a@b.test');
    await userEvent.type(screen.getByLabelText('Password'), 'Sunrise2026');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await userEvent.type(await screen.findByLabelText('6-digit code'), '12a3456');
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }));

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(requests.find((r) => r.url === '/auth/login/two-factor')!.body).toEqual({
      challengeToken: 'chal-1',
      code: '123456',
    });
  });

  it('mobile OTP asks for a name only when the number is new', async () => {
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/otp/send') return reply.ok({ expiresInSeconds: 300, resendAfterSeconds: 60 });
      if (r.url === '/auth/otp/login') {
        return r.body?.fullName ? reply.ok(authenticated()) : reply.fail(400, 'FULL_NAME_REQUIRED');
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/signin' });

    await userEvent.click(await screen.findByRole('tab', { name: 'Mobile OTP' }));
    await userEvent.type(screen.getByLabelText('Mobile number'), '98765 43210');
    await userEvent.click(screen.getByRole('button', { name: 'Send code' }));
    await userEvent.type(await screen.findByLabelText('Code from SMS'), '482913');
    await userEvent.click(screen.getByRole('button', { name: 'Verify and continue' }));

    await userEvent.type(await screen.findByLabelText('Full name'), 'Ravi');
    await userEvent.click(screen.getByRole('button', { name: 'Verify and continue' }));

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(requests.filter((r) => r.url === '/auth/otp/login').at(-1)!.body).toMatchObject({
      phone: '98765 43210',
      code: '482913',
      fullName: 'Ravi',
    });
  });
});

describe('register', () => {
  it('checks the password locally before calling the API', async () => {
    const requests = installFakeApi(() => undefined);
    renderWithProviders(<AppRoutes />, { route: '/register' });

    await userEvent.type(await screen.findByLabelText('Full name'), 'Asha');
    await userEvent.type(screen.getByLabelText('Email address'), 'asha@example.test');
    await userEvent.type(screen.getByLabelText('Password'), 'onlyletters');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Use both letters and numbers.')).toBeInTheDocument();
    expect(requests.some((r) => r.url === '/auth/register')).toBe(false);
  });

  it('shows server field errors next to the field', async () => {
    installFakeApi((r) =>
      r.url === '/auth/register'
        ? reply.fail(400, 'VALIDATION_FAILED', [{ field: 'email', code: 'Email', message: 'bad' }])
        : undefined,
    );
    renderWithProviders(<AppRoutes />, { route: '/register' });
    await userEvent.type(await screen.findByLabelText('Full name'), 'Asha');
    await userEvent.type(screen.getByLabelText('Email address'), 'x@y');
    await userEvent.type(screen.getByLabelText('Password'), 'Sunrise2026');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
  });
});

describe('route protection', () => {
  it('sends anonymous visitors to sign in and remembers where they were going', async () => {
    renderWithProviders(<AppRoutes />, { route: '/account' });
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
  });

  it('restores the session from the refresh cookie on load', async () => {
    installFakeApi((r) => (r.url === '/auth/refresh' ? reply.ok(authenticated()) : undefined));
    renderWithProviders(<AppRoutes />, { route: '/account' });
    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText(sampleUser.fullName).length).toBeGreaterThan(0));
  });

  it('only accepts same-site relative redirect targets', () => {
    expect(safeNext('/account')).toBe('/account');
    expect(safeNext('//evil.example')).toBe('/account');
    expect(safeNext('https://evil.example')).toBe('/account');
    expect(safeNext('/\\evil.example')).toBe('/account');
    expect(safeNext(null)).toBe('/account');
  });
});
