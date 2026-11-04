import { describe, expect, it } from 'vitest';
import { authenticated, installFakeApi, reply, sampleUser } from '@/test/fakeApi';
import { apiGet, refreshSession, session } from './apiClient';

describe('apiClient session handling', () => {
  it('sends the bearer token to protected endpoints but never to auth endpoints', async () => {
    const requests = installFakeApi((r) => (r.url === '/account/me' ? reply.ok(sampleUser) : reply.ok(null)));
    session.apply(authenticated('tok-A') as never);

    await apiGet('/account/me');
    await apiGet('/auth/anything');

    expect(requests[0]!.headers.authorization).toBe('Bearer tok-A');
    expect(requests[1]!.headers.authorization).toBeUndefined();
  });

  it('refreshes once on 401 and retries the original request with the new token', async () => {
    let calls = 0;
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/refresh') return reply.ok(authenticated('tok-B'));
      if (r.url === '/account/me') {
        calls++;
        return r.headers.authorization === 'Bearer tok-B' ? reply.ok(sampleUser) : reply.fail(401, 'UNAUTHENTICATED');
      }
      return undefined;
    });
    session.apply(authenticated('tok-A') as never);

    const results = await Promise.all([apiGet('/account/me'), apiGet('/account/me'), apiGet('/account/me')]);

    expect(results).toHaveLength(3);
    // Three concurrent 401s share a single refresh call, which carries the CSRF header.
    const refreshes = requests.filter((r) => r.url === '/auth/refresh');
    expect(refreshes).toHaveLength(1);
    expect(refreshes[0]!.headers['x-bookeasy-client']).toBe('web');
    expect(calls).toBe(6);
  });

  it('clears the session and surfaces the error when refresh fails', async () => {
    installFakeApi((r) =>
      r.url === '/auth/refresh' ? reply.fail(401, 'SESSION_EXPIRED') : reply.fail(401, 'UNAUTHENTICATED'),
    );
    let latest: unknown = 'unset';
    session.subscribe((u) => (latest = u));
    session.apply(authenticated('tok-A') as never);

    await expect(apiGet('/account/me')).rejects.toMatchObject({ code: 'UNAUTHENTICATED', status: 401 });
    expect(latest).toBeNull();
    expect(session.hasToken()).toBe(false);
  });

  it('refreshSession reports whether a session could be restored', async () => {
    installFakeApi((r) => (r.url === '/auth/refresh' ? reply.ok(authenticated('tok-C')) : undefined));
    await expect(refreshSession()).resolves.toBe(true);
    expect(session.hasToken()).toBe(true);
  });
});
