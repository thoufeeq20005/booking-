import { apiGet, apiPost } from './apiClient';
import type { DaySlots } from './teamApi';

export type BookingStatus = 'PENDING_PAYMENT' | 'PENDING_APPROVAL' | 'CONFIRMED' | 'CANCELLED' | 'DECLINED' | 'COMPLETED'
  | 'NO_SHOW' | 'EXPIRED';

export interface BookingPass {
  reference: string;
  status: BookingStatus;
  startsAt: string;
  endsAt: string;
  timezone: string;
  organizationName: string;
  organizationSlug: string;
  branchName: string;
  branchAddress?: string;
  serviceName: string;
  staffName?: string;
  guestFirstName?: string;
  paid: boolean;
  checkedInAt?: string;
}

export interface Booking {
  id: number;
  reference: string;
  status: BookingStatus;
  startsAt: string;
  endsAt: string;
  timezone: string;
  organizationId: number;
  organizationName: string;
  organizationSlug: string;
  branchId: number;
  branchName: string;
  branchAddress?: string;
  serviceId: number;
  serviceName: string;
  staffId?: number;
  staffName?: string;
  customerName?: string;
  customerPhone?: string;
  patientName?: string;
  servicePrice: number | string;
  platformFee: number | string;
  totalAmount: number | string;
  customerNote?: string;
  cancelReason?: string;
  cancelledBy?: 'CUSTOMER' | 'BUSINESS' | 'SYSTEM';
  refundAmount?: number | string;
  checkedInAt?: string;
  rescheduleCount: number;
  qrPayload?: string;
  paid: boolean;
  reviewed: boolean;
  createdAt: string;
  /** Movie tickets: the show, its screen and the seats (seats are released if the booking ends). */
  showtimeId?: number;
  screenName?: string;
  seats?: string[];
  /** Table bookings: number of guests. */
  partySize?: number;
  /** Table bookings: the offer on the final bill. Bill payments: the offer applied. */
  offerTitle?: string;
  offerPercent?: number;
  /** Bill payments: the bill, the discount and the cover charge credited; totalAmount is what was paid. */
  billAmount?: number | string;
  discountAmount?: number | string;
  coverCredit?: number | string;
}

/** Tickets and tables are cancelled and booked again rather than moved. */
export const isVenueBooking = (b: Pick<Booking, 'showtimeId' | 'partySize' | 'billAmount'>) =>
  b.showtimeId != null || b.partySize != null || b.billAmount != null;

export interface CancelPreview {
  totalPaid: number | string;
  refundAmount: number | string;
  refundPercent: number;
  allowed: boolean;
}

export type BusinessAction = 'confirm' | 'decline' | 'cancel' | 'check-in' | 'complete' | 'no-show';

export const ACTIVE: BookingStatus[] = ['PENDING_PAYMENT', 'PENDING_APPROVAL', 'CONFIRMED'];

export const bookingApi = {
  slots: (branchId: number, serviceId: number, date: string, staffId?: number) =>
    apiGet<DaySlots>(`/public/branches/${branchId}/services/${serviceId}/slots`, { params: { date, staffId } }),
  book: (body: { branchId: number; serviceId: number; staffId?: number; startsAt: string; note?: string; familyMemberId?: number }) =>
    apiPost<Booking>('/customer/appointments', body),
  mine: () => apiGet<Booking[]>('/customer/appointments'),
  get: (id: number) => apiGet<Booking>(`/customer/appointments/${id}`),
  /** Public, limited details behind a scanned booking QR. */
  pass: (code: string) => apiGet<BookingPass>(`/public/passes/${encodeURIComponent(code)}`),
  cancelPreview: (id: number) => apiGet<CancelPreview>(`/customer/appointments/${id}/cancel-preview`),
  cancel: (id: number, reason?: string) => apiPost<Booking>(`/customer/appointments/${id}/cancel`, { reason }),
  reschedule: (id: number, startsAt: string, staffId?: number) =>
    apiPost<Booking>(`/customer/appointments/${id}/reschedule`, { startsAt, staffId }),
  ownerDay: (orgId: number, date: string) => apiGet<Booking[]>(`/owner/organizations/${orgId}/appointments`, { params: { date } }),
  ownerAct: (orgId: number, id: number, action: BusinessAction, reason?: string) =>
    apiPost<Booking>(`/owner/organizations/${orgId}/appointments/${id}/${action}`, { reason }),
  checkIn: (orgId: number, code: string) => apiPost<Booking>(`/owner/organizations/${orgId}/check-in`, { code }),
  staffDay: (date: string) => apiGet<Booking[]>('/staff/appointments', { params: { date } }),
  staffAct: (id: number, action: BusinessAction, reason?: string) =>
    apiPost<Booking>(`/staff/appointments/${id}/${action}`, { reason }),
};

/** yyyy-mm-dd of a date in India time. */
export function isoDateIST(d: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(d);
}

export function nextDays(count: number): string[] {
  return Array.from({ length: count }, (_, i) => isoDateIST(new Date(Date.now() + i * 86400000)));
}
