# Razorpay (Phase 8)

BookEasy charges customers online through Razorpay Checkout when **all three** of `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`
and `RAZORPAY_WEBHOOK_SECRET` are set. Without them the platform runs in pay-at-venue mode: bookings skip
`PENDING_PAYMENT` and go straight to confirmed / awaiting approval.

## Setup

1. Razorpay Dashboard → Settings → API Keys: generate **test** keys first (`rzp_test_…`). Put them in Render's environment.
2. Settings → Webhooks → Add: URL `https://<your-api>/api/v1/public/webhooks/razorpay`, a strong secret
   (`RAZORPAY_WEBHOOK_SECRET`), events `payment.captured`, `payment.failed`, `order.paid`, `refund.processed`, `refund.failed`.
3. Frontend: `VITE_RAZORPAY_KEY_ID` is not required (the key id comes from the order response), but keep it set for reference.
4. Go live: switch to `rzp_live_` keys and a live webhook only after testing the full flow below.

## Flow

1. Customer books → appointment `PENDING_PAYMENT`, slot locked for `PAYMENT_HOLD_MINUTES` (default 15).
2. `POST /customer/appointments/{id}/payment-order` creates (or reuses) a Razorpay order for the total in paise
   (`payment_capture=1`, receipt = booking reference).
3. Checkout returns `order_id|payment_id` + signature → `POST /customer/payments/verify` checks
   HMAC-SHA256 with the key secret. Only then is the booking confirmed (or sent for approval).
4. The webhook confirms independently (signature = HMAC-SHA256 of the raw body with the webhook secret); events are
   de-duplicated by `X-Razorpay-Event-Id`, so retries are harmless. Whichever arrives first confirms; the other is a no-op.
5. Unpaid bookings expire after the hold (job every minute) and free their slot. A payment that lands after expiry
   or cancellation is refunded in full automatically.

## Refunds

Queued in the same transaction as the cancellation (`refunds` table, one per booking), sent right after commit, and
retried every 5 minutes up to 5 times. Customer cancellations refund the policy share of the service fee
(`refund.*` platform settings); business cancellations and declines refund everything including the platform fee.

## Commission & settlements

On capture a `commission_records` row stores gross, BookEasy's fee and the business's payout (all paise).
Refunds reduce the payout first, then the fee. Nightly at 02:00 IST completed, paid, unsettled bookings are grouped
per business into a settlement:

- **Route enabled** (`RAZORPAY_ROUTE_ENABLED=true`, Route activated on your account) and the business has a linked
  account (`acc_…`, set by a Super Admin on the business review page) → one transfer per payment, status `TRANSFERRED`.
- Otherwise `PENDING_MANUAL`: finance pays the business by bank transfer and records the UTR under Admin → Settlements.

## Testing locally

Use Razorpay test cards/UPI in Checkout. For webhooks in development, expose the API with a tunnel (e.g. ngrok) and
point a test-mode webhook at it. Automated tests use a fake client and test secrets (`PaymentIT`).

## Demo mode (development only)

Without Razorpay keys, `PAYMENTS_DEMO_MODE=true` (the dev default) still sends bookings through the payment page:
the customer picks UPI, card, net banking or wallet and a simulated gateway marks the booking paid (payment ids start
with `demo_`, refunds of demo payments are settled locally). No card or UPI details are ever collected. Demo mode is
ignored as soon as Razorpay keys are set, is forced off in the `prod` profile, and is off in tests unless a test opts in.
Set `PAYMENTS_DEMO_MODE=false` locally to go back to "pay at the venue" (bookings confirm instantly).

With keys (test keys `rzp_test_…` are free and need no KYC), the same page opens Razorpay Checkout with the chosen
method pre-selected.
