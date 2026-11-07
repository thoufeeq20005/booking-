# Creating the First Super Admin

There is no default admin account or password. The first Super Admin is created once, through a one-time,
token-protected endpoint that switches itself off as soon as a Super Admin exists.

## Steps

1. Generate a bootstrap token (at least 32 characters):
   ```bash
   openssl rand -base64 32
   ```
2. Set on the backend (Render dashboard → Environment, or local `.env`):
   ```
   BOOTSTRAP_ADMIN_EMAIL=you@yourcompany.in
   BOOTSTRAP_ADMIN_TOKEN=<the generated token>
   ```
   and redeploy/restart.
3. Call the endpoint once (over HTTPS in production):
   ```bash
   curl -X POST https://<your-site>/api/v1/public/bootstrap/super-admin \
     -H 'Content-Type: application/json' \
     -d '{"token":"<token>","email":"you@yourcompany.in","fullName":"Your Name","password":"<strong password>"}'
   ```
   The email must match `BOOTSTRAP_ADMIN_EMAIL` exactly. The password must meet the normal policy.
4. **Remove** `BOOTSTRAP_ADMIN_TOKEN` and `BOOTSTRAP_ADMIN_EMAIL` from the environment and redeploy.
5. Sign in at `/signin` and **turn on two-step verification** under *My account*.

## Behaviour and safeguards

| Situation | Response |
|---|---|
| Token/email not configured (or token < 32 chars) | `404 BOOTSTRAP_UNAVAILABLE` |
| Wrong token or email | `403` (logged as a rejected attempt) |
| A Super Admin already exists | `409 BOOTSTRAP_ALREADY_DONE` |
| More than 5 attempts per hour per IP | `429` |

The token comparison is constant-time. Success writes a `SUPER_ADMIN_BOOTSTRAPPED` audit log entry. More admins
will be added from the admin dashboard (Phase 4), not through this endpoint.
