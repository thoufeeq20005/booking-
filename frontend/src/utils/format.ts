// Indian formatting helpers. Amounts arrive from the API as decimal strings or numbers in rupees;
// they are only formatted here, never used for arithmetic (the backend owns all money maths).

export const APP_TIME_ZONE = 'Asia/Kolkata';

const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export function formatINR(amount: number | string): string {
  const value = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(value)) return '—';
  return inr.format(value);
}

export function formatIndianNumber(value: number): string {
  return new Intl.NumberFormat('en-IN').format(value);
}

export function formatDate(iso: string | Date, locale = 'en-IN'): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: APP_TIME_ZONE }).format(new Date(iso));
}

export function formatDateTime(iso: string | Date, locale = 'en-IN'): string {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: APP_TIME_ZONE,
  }).format(new Date(iso));
}

/** "09:00:00" → "9:00 am" (wall-clock time, no zone conversion). */
export function formatWallTime(time: string, locale = 'en-IN'): string {
  const [h = '0', m = '0'] = time.split(':');
  const d = new Date(Date.UTC(2000, 0, 1, Number(h), Number(m)));
  return new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' }).format(d);
}
