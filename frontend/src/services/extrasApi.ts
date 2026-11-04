import { apiDelete, apiGet, apiPost, apiPut } from './apiClient';

export type Relation = 'SPOUSE' | 'CHILD' | 'PARENT' | 'SIBLING' | 'OTHER';
export const RELATIONS: Relation[] = ['SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER'];

export interface FamilyMember { id: number; fullName: string; relation: Relation; dateOfBirth?: string }
export interface Favourite { organizationId: number; slug: string; name: string; ratingAvg: number; reviewCount: number; city?: string }
export interface SavedLocation { id: number; label: string; latitude: number; longitude: number }
export type SupportStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
export interface SupportRequest {
  id: number; userId: number; userName: string; userEmail?: string; appointmentId?: number; reference?: string;
  subject: string; message: string; status: SupportStatus; adminReply?: string; createdAt: string; updatedAt: string;
}
export interface RetentionRun {
  id: number; startedAt: string; finishedAt: string; appointmentsAnonymised: number; notificationsDeleted: number;
  outboxDeleted: number; webhookEventsDeleted: number; rateLimitsDeleted: number;
}

export const extrasApi = {
  family: () => apiGet<FamilyMember[]>('/customer/family'),
  addFamily: (b: { fullName: string; relation: Relation; dateOfBirth?: string }) => apiPost<FamilyMember>('/customer/family', b),
  removeFamily: (id: number) => apiDelete(`/customer/family/${id}`),
  favourites: () => apiGet<Favourite[]>('/customer/favourites'),
  addFavourite: (orgId: number) => apiPut<void>(`/customer/favourites/${orgId}`),
  removeFavourite: (orgId: number) => apiDelete(`/customer/favourites/${orgId}`),
  locations: () => apiGet<SavedLocation[]>('/customer/saved-locations'),
  saveLocation: (b: { label: string; latitude: number; longitude: number }) => apiPost<SavedLocation[]>('/customer/saved-locations', b),
  removeLocation: (id: number) => apiDelete(`/customer/saved-locations/${id}`),
  openSupport: (b: { subject: string; message: string; appointmentId?: number }) => apiPost<SupportRequest>('/support-requests', b),
  mySupport: () => apiGet<SupportRequest[]>('/support-requests'),
  adminSupport: (status?: SupportStatus) => apiGet<SupportRequest[]>('/admin/support-requests', { params: { status } }),
  answer: (id: number, status: SupportStatus, reply?: string) => apiPost<SupportRequest>(`/admin/support-requests/${id}`, { status, reply }),
  runRetention: () => apiPost<RetentionRun>('/admin/retention/run'),
  retentionRuns: () => apiGet<RetentionRun[]>('/admin/retention/runs'),
};

/** Up to three businesses picked for comparison, remembered in this browser only. */
const KEY = 'bookeasy.compare';
export const compareList = {
  get(): string[] {
    try { return JSON.parse(window.localStorage.getItem(KEY) ?? '[]') as string[]; } catch { return []; }
  },
  toggle(slug: string): string[] {
    const cur = compareList.get();
    const next = cur.includes(slug) ? cur.filter((s) => s !== slug) : [...cur, slug].slice(-3);
    try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* not remembered */ }
    return next;
  },
};
