import { apiGet, apiPost, apiPut } from './apiClient';

export interface CheckoutOrder {
  keyId: string;
  orderId: string;
  amountPaise: number;
  currency: string;
  reference: string;
  description: string;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  expiresAt: string;
  /** Development demo gateway (no Razorpay keys): pay with paymentApi.demoPay instead of Checkout. */
  demo?: boolean;
}

/** Razorpay Checkout method names. */
export type PaymentMethod = 'upi' | 'card' | 'netbanking' | 'wallet';

export interface Receipt {
  receiptNumber: string;
  paidAt: string;
  reference: string;
  businessName: string;
  customerName: string;
  serviceName: string;
  appointmentAt: string;
  servicePrice: number | string;
  platformFee: number | string;
  total: number | string;
  paymentId: string;
  method?: string;
  refunded?: number | string;
  refundStatus?: string;
}

export interface Settlement {
  id: number;
  organizationId: number;
  organizationName: string;
  amountPaise: number;
  status: 'PENDING_MANUAL' | 'TRANSFERRED' | 'PAID';
  razorpayTransferId?: string;
  payoutReference?: string;
  createdAt: string;
  paidAt?: string;
}

export const paymentApi = {
  order: (appointmentId: number) => apiPost<CheckoutOrder>(`/customer/appointments/${appointmentId}/payment-order`),
  verify: (body: { orderId: string; paymentId: string; signature: string }) => apiPost<void>('/customer/payments/verify', body),
  demoPay: (appointmentId: number, method: PaymentMethod) =>
    apiPost<void>(`/customer/appointments/${appointmentId}/demo-payment`, { method }),
  receipt: (appointmentId: number) => apiGet<Receipt>(`/customer/appointments/${appointmentId}/receipt`),
  earnings: (orgId: number) =>
    apiGet<{ pendingPayoutPaise: number; settledPaise: number; settlements: Settlement[] }>(`/owner/organizations/${orgId}/earnings`),
  settlements: (status?: string) => apiGet<Settlement[]>('/admin/settlements', { params: { status } }),
  runSettlement: () => apiPost<number>('/admin/settlements/run'),
  markPaid: (id: number, reference: string) => apiPost<Settlement>(`/admin/settlements/${id}/paid`, { reference }),
  setPayoutAccount: (orgId: number, accountId: string) => apiPut<void>(`/admin/organizations/${orgId}/payout-account`, { accountId }),
};

interface RazorpayResponse {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayInstance {
  open: () => void;
  on: (event: string, cb: () => void) => void;
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance;
  }
}

let loading: Promise<void> | null = null;

/** Loads Razorpay Checkout once. */
function loadCheckout(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  loading ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://checkout.razorpay.com/v1/checkout.js';
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => {
      loading = null;
      reject(new Error('Razorpay Checkout could not be loaded'));
    };
    document.body.appendChild(s);
  });
  return loading;
}

/**
 * Opens Razorpay Checkout for the order. Resolves with the signed response, which the server then verifies;
 * resolves null if the customer closes the window.
 */
/** Opens Razorpay Checkout; `method` pre-selects the option the customer chose on the payment page. */
export async function payWithRazorpay(order: CheckoutOrder, method?: PaymentMethod): Promise<RazorpayResponse | null> {
  await loadCheckout();
  return new Promise((resolve) => {
    const rzp = new window.Razorpay!({
      key: order.keyId,
      amount: order.amountPaise,
      currency: order.currency,
      order_id: order.orderId,
      name: 'BookEasy',
      description: order.description,
      prefill: { name: order.customerName, email: order.customerEmail, contact: order.customerPhone, method },
      notes: { reference: order.reference },
      theme: { color: '#2563eb' },
      handler: (res: RazorpayResponse) => resolve(res),
      modal: { ondismiss: () => resolve(null) },
    });
    rzp.open();
  });
}
