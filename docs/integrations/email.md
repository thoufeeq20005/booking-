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
| Email verification | 6-digit code, entered on the website | 5 min |
| Email sign-in code | 6-digit code, entered on the website | 5 min |
| Password reset | `{FRONTEND_URL}/reset-password?token=…` | 30 min |

Texts live in `backend/src/main/resources/i18n/messages_{en,ta,hi,ml,te,kn}.properties` and are rendered in the
user's preferred language. Booking, payment and approval emails arrive with the notification module (Phase 9).

## Behaviour without a provider

- Dev: emails are logged (`[DEV EMAIL] …`), including the link, so flows can be completed locally.
- Prod: sends fail and are logged as errors. Registration still succeeds (`verificationEmailSent: false`) and
  the user can resend later. A message is never reported as sent when it wasn't.

## Quick start with Gmail (development and small pilots)

Gmail works for testing the sign-in codes with real inboxes. Its daily sending limit is low (about 500 a day for a
personal account), so switch to a transactional provider before launch.

1. Turn on 2-Step Verification for the Gmail account: <https://myaccount.google.com/security>.
2. Create an app password named `BookEasy`: <https://myaccount.google.com/apppasswords>. Copy the 16 characters
   without spaces; Google shows it only once.
3. In `.env` (never commit it):
   ```properties
   EMAIL_PROVIDER=smtp
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=yourname@gmail.com
   SMTP_PASSWORD=abcdefghijklmnop
   SMTP_STARTTLS=true
   EMAIL_FROM_ADDRESS=yourname@gmail.com
   EMAIL_FROM_NAME=BookEasy
   ```
4. Restart the backend. Codes now arrive by email instead of appearing on screen.

## Sign-in codes after the password

With `AUTH_PASSWORD_LOGIN_EMAIL_CODE=true` (the default), a correct password is not enough: BookEasy emails a 6-digit
code (stored hashed, valid 5 minutes, 5 tries, a new one at most every 60 seconds and 5 per hour) and signs in only once
it is entered. Accounts with an authenticator app get that step instead. Signing in with only an emailed code, or with
Google, already proves the inbox and skips it. Set the variable to `false` to turn the step off.

## Brevo API (for hosts that block SMTP, e.g. Render's free plan)

Render's free instances can't open outbound SMTP connections (ports 25, 465 and 587), so Gmail SMTP fails there.
Brevo's HTTPS API works instead. Its free plan allows 300 emails a day.

1. Sign up at brevo.com.
2. Go to **Senders, domains & dedicated IPs → Senders** and add the address you send from (your Gmail works). Click the
   link in the verification email Brevo sends.
3. Go to **SMTP & API → API keys** and create a key.
4. In Render, set:

| Variable | Value |
|---|---|
| `EMAIL_PROVIDER` | `brevo` |
| `EMAIL_API_KEY` | the Brevo API key |
| `EMAIL_FROM_ADDRESS` | the verified sender address |

The `SMTP_*` variables are not used with `brevo`.

Booking and reminder emails are sent on a background thread, so a slow or unreachable mail provider never holds up a
page. Sign-in codes are sent while the user waits, with a 10-second timeout.

