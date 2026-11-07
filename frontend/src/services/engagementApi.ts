import { apiGet, apiPost, apiPut } from './apiClient';

export interface InboxItem {
  id: number;
  type: string;
  title: string;
  body: string;
  link?: string;
  readAt?: string;
  createdAt: string;
}

export interface Preferences {
  emailEnabled: boolean;
  smsEnabled: boolean;
  whatsappEnabled: boolean;
  remindersEnabled: boolean;
}

export interface Review {
  id: number;
  appointmentId: number;
  organizationId: number;
  organizationName: string;
  authorName: string;
  serviceName: string;
  rating: number;
  comment?: string;
  status: 'VISIBLE' | 'HIDDEN';
  ownerReply?: string;
  repliedAt?: string;
  reportCount: number;
  createdAt: string;
}

export const notificationApi = {
  inbox: () => apiGet<InboxItem[]>('/account/notifications'),
  unread: () => apiGet<number>('/account/notifications/unread-count'),
  read: (id: number) => apiPost<void>(`/account/notifications/${id}/read`),
  readAll: () => apiPost<void>('/account/notifications/read-all'),
  preferences: () => apiGet<Preferences>('/account/notification-preferences'),
  savePreferences: (p: Preferences) => apiPut<Preferences>('/account/notification-preferences', p),
};

export const reviewApi = {
  create: (appointmentId: number, rating: number, comment?: string) =>
    apiPost<Review>(`/customer/appointments/${appointmentId}/review`, { rating, comment }),
  forBusiness: (slug: string) => apiGet<Review[]>(`/public/organizations/${encodeURIComponent(slug)}/reviews`),
  forOwner: (orgId: number) => apiGet<Review[]>(`/owner/organizations/${orgId}/reviews`),
  reply: (orgId: number, reviewId: number, text: string) =>
    apiPost<Review>(`/owner/organizations/${orgId}/reviews/${reviewId}/reply`, { text }),
  report: (reviewId: number, text: string) => apiPost<void>(`/reviews/${reviewId}/report`, { text }),
  moderation: (reported: boolean) => apiGet<Review[]>('/admin/reviews', { params: { reported } }),
  moderate: (reviewId: number, action: 'hide' | 'restore', text?: string) =>
    apiPost<Review>(`/admin/reviews/${reviewId}/${action}`, { text }),
};
