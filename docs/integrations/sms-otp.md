# SMS / OTP – MSG91

## Why MSG91

Chosen as the default because it is an Indian provider with DLT (TRAI) template support built in, an OTP-friendly
template ("Flow") API, INR billing and wide adoption. The integration sits behind the `SmsSender` interface, so
swapping providers (e.g. Twilio Verify, Gupshup, Kaleyra) means adding one adapter class.
**Pricing changes often. Confirm current per-SMS rates and DLT charges in the MSG91 dashboard before choosing a plan.**

## One-time setup (business side)

1. **DLT registration** (mandatory for commercial SMS in India): register your business as a *Principal Entity*
   on a DLT portal (Jio, Vodafone-Idea, Airtel, BSNL…), register a *sender ID (header)*, and register an OTP
   *content template*, e.g.
   `{#var#} is your BookEasy verification code. It expires in 5 minutes. Do not share it with anyone.`
2. In MSG91: add the DLT entity ID and header, then create a **Flow/SMS template** that maps to the DLT template and
   uses one variable for the code (for example `otp`).
3. Create an API **auth key** (restrict by IP if your plan allows).

## Configure BookEasy

```
SMS_PROVIDER=msg91
SMS_API_KEY=<MSG91 auth key>
SMS_OTP_TEMPLATE_ID=<MSG91 template/flow id>
SMS_OTP_VARIABLE=otp          # the variable name used in that template
SMS_BASE_URL=https://control.msg91.com
```

The adapter sends `POST {SMS_BASE_URL}/api/v5/flow` with header `authkey` and body
`{"template_id": ..., "short_url": "0", "recipients": [{"mobiles": "91XXXXXXXXXX", "<variable>": "<code>"}]}`.
Check this against MSG91's current API reference when you set up the account.
The request shape is unit-tested (`Msg91SmsSenderTest`), but it has not been run against a live MSG91 account yet.

## Behaviour

- No provider + dev profile: codes are written to the backend log (`[DEV SMS] …`) so you can develop offline.
- No provider in prod: sending fails with `503 PROVIDER_UNAVAILABLE`, and the code is not stored, so the user isn't
  blocked by the cooldown.
- Limits: 6-digit code, 5-minute expiry, 5 attempts, 60 s resend cooldown, 5 sends per hour per number.
