# Email – SMTP

BookEasy sends email over standard SMTP, so any reputable transactional provider works without code changes.
Recommended options (confirm current pricing and limits when you sign up):

| Provider | Why |
|---|---|
| **Brevo** (ex-Sendinblue) | Generous free tier for low volumes, simple SMTP relay, good for launch |
| **Amazon SES** (Mumbai `ap-south-1`) | Lowest cost at scale; needs sandbox exit approval |
| Zoho ZeptoMail | India-based support, transactional-only |

## Setup

1. Verify your sending domain with the provider and add the **SPF, DKIM and DMARC** DNS records they give you
   (required for inbox placement, especially Gmail/Yahoo).
2. Create SMTP credentials.
3. Configure:
   ```
   EMAIL_PROVIDER=smtp
   SMTP_HOST=smtp-relay.brevo.com      # or email-smtp.ap-south-1.amazonaws.com, …
   SMTP_PORT=587
   SMTP_USERNAME=...
   SMTP_PASSWORD=...
   SMTP_STARTTLS=true
   EMAIL_FROM_ADDRESS=no-reply@yourdomain.in
   EMAIL_FROM_NAME=BookEasy
   ```

## Messages (Phase 2)

| Event | Link target | Expiry |
|---|---|---|
| Email verification | `{FRONTEND_URL}/verify-email?token=…` | 24 h |
| Password reset | `{FRONTEND_URL}/reset-password?token=…` | 30 min |

Texts live in `backend/src/main/resources/i18n/messages_{en,ta,hi,ml,te,kn}.properties` and are rendered in the
user's preferred language. Booking, payment and approval emails arrive with the notification module (Phase 9).

## Behaviour without a provider

- Dev: emails are logged (`[DEV EMAIL] …`), including the link, so flows can be completed locally.
- Prod: sends fail and are logged as errors. Registration still succeeds (`verificationEmailSent: false`) and
  the user can resend later. A message is never reported as sent when it wasn't.
