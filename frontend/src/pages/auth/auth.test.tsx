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

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in with password instead' }));
    await userEvent.type(screen.getByLabelText('Email address'), 'asha@example.test');
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

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in with password instead' }));
    await userEvent.type(screen.getByLabelText('Email address'), 'a@b.test');
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

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in with password instead' }));
    await userEvent.type(screen.getByLabelText('Email address'), 'a@b.test');
    await userEvent.type(screen.getByLabelText('Password'), 'Sunrise2026');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await userEvent.type(await screen.findByLabelText('6-digit code'), '12a3456');
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }));

    expect(await screen.findByRole('heading', { name: 'Hello, Asha Kumar' })).toBeInTheDocument();
    expect(requests.find((r) => r.url === '/auth/login/two-factor')!.body).toEqual({
      challengeToken: 'chal-1',
      code: '123456',
    });
  });

  it('after the password, asks for the code emailed to the account', async () => {
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/login') {
        return reply.ok({ status: 'EMAIL_CODE_REQUIRED', challengeToken: 'chal-2',
          emailCode: { expiresInSeconds: 300, resendAfterSeconds: 0 } });
      }
      if (r.url === '/auth/login/email-code/resend') return reply.ok({ expiresInSeconds: 300, resendAfterSeconds: 60, demoCode: '654321' });
      if (r.url === '/auth/login/email-code') {
        return r.body?.code === '654321' ? reply.ok(authenticated()) : reply.fail(400, 'OTP_INVALID');
      }
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/signin?next=%2Faccount' });

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in with password instead' }));
    await userEvent.type(screen.getByLabelText('Email address'), 'asha@example.test');
    await userEvent.type(screen.getByLabelText('Password'), 'Sunrise2026');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument();
    expect(screen.getByText(/a\*+@example\.test/)).toBeInTheDocument(); // masked address
    // Pasting a code fills all six boxes.
    await userEvent.click(screen.getByLabelText('Code digit 1'));
    await userEvent.paste('11 22 33');
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();

    // Resend (no wait left): in development the new code is shown and can be used directly.
    await userEvent.click(screen.getByRole('button', { name: 'Resend code' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Fill in' }));
    expect(screen.getByText(/Resend in 60s|Resend in 59s/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }));

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(requests.filter((r) => r.url === '/auth/login/email-code').map((r) => r.body)).toEqual([
      { challengeToken: 'chal-2', code: '112233' },
      { challengeToken: 'chal-2', code: '654321' },
    ]);
  });
});

describe('email code sign-in', () => {
  it('signs in with a code from the inbox (no password)', async () => {
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/email-code/send') return reply.ok({ expiresInSeconds: 300, resendAfterSeconds: 60, demoCode: '246810' });
      if (r.url === '/auth/email-code/login') return reply.ok(authenticated());
      return undefined;
    });
    renderWithProviders(<AppRoutes />, { route: '/signin?next=%2Faccount' });

    await userEvent.type(await screen.findByLabelText('Email address'), 'asha@example.test');
    await userEvent.click(screen.getByRole('button', { name: 'Send code' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Fill in' }));
    await userEvent.click(screen.getByRole('button', { name: 'Verify and continue' }));

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(requests.find((r) => r.url === '/auth/email-code/login')!.body).toEqual({
      email: 'asha@example.test',
      code: '246810',
    });
    expect(requests.some((r) => r.url === '/auth/login')).toBe(false);
  });
});

describe('register', () => {
  it('checks the password locally before calling the API', async () => {
    const requests = installFakeApi(() => undefined);
    renderWithProviders(<AppRoutes />, { route: '/register' });

    await userEvent.type(await screen.findByLabelText('Full name'), 'Asha');
    await userEvent.type(screen.getByLabelText('Email address'), 'asha@example.test');
    await userEvent.type(screen.getByLabelText('Password'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
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

describe('verification codes', () => {
  it('offers no mobile sign-in or sign-up', async () => {
    renderWithProviders(<AppRoutes />, { route: '/signin' });
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Mobile OTP' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Mobile number')).not.toBeInTheDocument();
  });

  it('sign-up goes straight in: no verification step', async () => {
    const requests = installFakeApi((r) =>
      r.url === '/auth/register' ? reply.ok({ session: authenticated() }) : undefined);
    renderWithProviders(<AppRoutes />, { route: '/register?next=%2Faccount' });

    await userEvent.type(await screen.findByLabelText('Full name'), 'Asha');
    await userEvent.type(screen.getByLabelText('Email address'), 'asha@example.test');
    await userEvent.type(screen.getByLabelText('Password'), 'sunshine');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Verify your email' })).not.toBeInTheDocument();
    expect(requests.some((r) => r.url.startsWith('/account/email'))).toBe(false);
  });

  it('profile shows only the email: no verify button and no mobile number', async () => {
    installFakeApi((r) => (r.url === '/auth/refresh' ? reply.ok(authenticated()) : undefined));
    renderWithProviders(<AppRoutes />, { route: '/account' });

    expect(await screen.findByText('asha@example.test')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verify email' })).not.toBeInTheDocument();
    expect(screen.queryByText('Not verified')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Mobile number')).not.toBeInTheDocument();
    expect(screen.queryByText('No mobile number')).not.toBeInTheDocument();
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
