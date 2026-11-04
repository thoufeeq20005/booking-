# Deployment Notes

The full deployment guide is completed in Phase 11. These decisions already shape the configuration.

## Topology

```
Browser ──HTTPS──▶ Vercel (SPA)  ── rewrite /api/* ──▶ Render (Spring Boot) ──▶ Managed MySQL
```

The SPA and API share one origin (the Vercel domain). The browser never talks to Render directly, so:
- the refresh cookie is first-party and `SameSite=Strict`, with no third-party-cookie problems;
- no CORS is involved for the app itself (`CORS_ALLOWED_ORIGINS` still restricts anything else).

**Set the rewrite target** in `frontend/vercel.json` to your Render service URL (default placeholder:
`https://bookeasy-api.onrender.com`). Vercel can't read environment variables in `vercel.json`.

## Required backend environment (prod)

`SPRING_PROFILES_ACTIVE=prod`, `DB_*`, `JWT_SECRET` (≥ 64 bytes), `DATA_ENCRYPTION_KEY` (32 bytes base64),
`FRONTEND_URL` (the Vercel domain, used in email links), `CORS_ALLOWED_ORIGINS`, plus provider settings
(see `docs/integrations/`). The prod profile forces the Secure cookie flag, requires DB TLS, disables Swagger, and
turns off log-only notification delivery.

## Client IP behind proxies

Rate limits key on the client IP, so it must be correct. With Vercel → Render, `X-Forwarded-For` arrives as
`<client>, <vercel-egress>`, so `CLIENT_IP_XFF_INDEX=2` (second from the right). **Verify after the first deploy:**
temporarily set `LOG_LEVEL=DEBUG`, make one request, and check the `Client IP inputs:` log line. If the header
looks different, adjust the index. If it's wrong, every user can end up sharing one rate-limit bucket.
