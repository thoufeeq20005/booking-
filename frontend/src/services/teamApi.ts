import { apiDelete, apiGet, apiPost, apiPut, apiUpload } from './apiClient';

export type ConsultationType = 'IN_PERSON' | 'ONLINE';
export type Permission = 'CAN_CONFIRM' | 'CAN_CANCEL' | 'CAN_EDIT_HOURS';
export const PERMISSIONS: Permission[] = ['CAN_CONFIRM', 'CAN_CANCEL', 'CAN_EDIT_HOURS'];

export interface BranchOffer {
  branchId: number;
  priceOverride?: number | string | null;
}

export interface ServiceView {
  id: number;
  name: string;
  description?: string;
  durationMinutes: number;
  price: number | string;
  consultationType: ConsultationType;
  requiresStaff: boolean;
  bufferMinutes: number;
  slotIntervalMinutes: number;
  maxAdvanceDays: number;
  minNoticeMinutes: number;
  active: boolean;
  branches: BranchOffer[];
  imageUrl?: string;
}

export type ServiceInput = Omit<ServiceView, 'id' | 'imageUrl'>;

export interface Window {
  branchId: number;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export interface Leave {
  id: number;
  startsAt: string;
  endsAt: string;
  reason?: string;
}

export interface StaffView {
  id: number;
  userId?: number;
  displayName: string;
  title?: string;
  specialisation?: string;
  permissions: Permission[];
  status: 'INVITED' | 'ACTIVE' | 'INACTIVE';
  branchIds: number[];
  serviceIds: number[];
  email?: string;
  invitation?: { email: string; expiresAt: string; open: boolean };
  availability: Window[];
  upcomingLeave: Leave[];
}

export interface StaffInput {
  displayName: string;
  title?: string;
  specialisation?: string;
  permissions: Permission[];
  branchIds: number[];
  serviceIds: number[];
}

export interface DaySlots {
  date: string;
  timezone: string;
  price: number | string;
  durationMinutes: number;
  outcome: 'OPEN' | 'HOLIDAY' | 'CLOSED' | 'OUT_OF_RANGE' | 'NO_STAFF';
  staff: { id: number; displayName: string; title?: string }[];
  slots: { time: string; startsAt: string; endsAt: string; staffIds: number[] }[];
}

export interface Workplace {
  organizationId: number;
  organizationName: string;
  profile: StaffView;
  branches: { id: number; name: string }[];
  services: { id: number; name: string }[];
}

const base = (orgId: number) => `/owner/organizations/${orgId}`;

export const teamApi = {
  services: (orgId: number) => apiGet<ServiceView[]>(`${base(orgId)}/services`),
  createService: (orgId: number, body: ServiceInput) => apiPost<ServiceView>(`${base(orgId)}/services`, body),
  updateService: (orgId: number, id: number, body: ServiceInput) => apiPut<ServiceView>(`${base(orgId)}/services/${id}`, body),
  deleteService: (orgId: number, id: number) => apiDelete(`${base(orgId)}/services/${id}`),
  uploadServiceImage: (orgId: number, id: number, file: File) =>
    apiUpload<ServiceView>(`${base(orgId)}/services/${id}/image`, file),
  removeServiceImage: (orgId: number, id: number) => apiDelete<ServiceView>(`${base(orgId)}/services/${id}/image`),
  staff: (orgId: number) => apiGet<StaffView[]>(`${base(orgId)}/staff`),
  invite: (orgId: number, email: string, profile: StaffInput) =>
    apiPost<StaffView>(`${base(orgId)}/staff/invitations`, { email, profile }),
  addSelf: (orgId: number, profile: StaffInput) => apiPost<StaffView>(`${base(orgId)}/staff/self`, profile),
  updateStaff: (orgId: number, id: number, profile: StaffInput) => apiPut<StaffView>(`${base(orgId)}/staff/${id}`, profile),
  setStatus: (orgId: number, id: number, status: 'ACTIVE' | 'INACTIVE') =>
    apiPost<StaffView>(`${base(orgId)}/staff/${id}/status`, { status }),
  resend: (orgId: number, id: number) => apiPost<StaffView>(`${base(orgId)}/staff/${id}/invitation/resend`),
  revoke: (orgId: number, id: number) => apiDelete(`${base(orgId)}/staff/${id}/invitation`),
  setAvailability: (orgId: number, id: number, windows: Window[]) =>
    apiPut<StaffView>(`${base(orgId)}/staff/${id}/availability`, { windows }),
  addLeave: (orgId: number, id: number, body: { startsAt: string; endsAt: string; reason?: string }) =>
    apiPost<StaffView>(`${base(orgId)}/staff/${id}/leaves`, body),
  deleteLeave: (orgId: number, id: number, leaveId: number) => apiDelete<StaffView>(`${base(orgId)}/staff/${id}/leaves/${leaveId}`),
  previewSlots: (orgId: number, params: { branchId: number; serviceId: number; date: string; staffId?: number }) =>
    apiGet<DaySlots>(`${base(orgId)}/slots`, { params }),
};

export const invitationApi = {
  preview: (token: string) =>
    apiPost<{ organizationName: string; displayName: string; email: string; expiresAt: string }>(
      '/public/staff-invitations/preview', { token }),
  accept: (token: string) => apiPost<Workplace>('/staff-invitations/accept', { token }),
  workplaces: () => apiGet<Workplace[]>('/staff/workplaces'),
};
