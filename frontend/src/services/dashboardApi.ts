import { apiGet, apiPatch, apiPost, type Role, type UserView } from './apiClient';
import type { MissingStep, OrganizationStatus, OrganizationSummary, Page } from './organizationApi';

export interface AuditEntry {
  id: number;
  createdAt: string;
  actorUserId?: number;
  actorName?: string;
  actorRole?: string;
  action: string;
  entityType: string;
  entityId?: string;
  details?: Record<string, unknown> | string | null;
  ipAddress?: string;
}

export interface AdminDashboard {
  users: { total: number; newLast7Days: number; suspended: number; byRole: Record<Role, number> };
  organizations: Record<OrganizationStatus, number>;
  reviewQueue: {
    pending: number;
    oldestSubmittedAt?: string;
    oldestOrganizationId?: number;
    oldestOrganizationName?: string;
    documentsAwaitingReview: number;
  };
  recentActivity: AuditEntry[];
}

export interface OwnerBusinessCard {
  summary: OrganizationSummary;
  missingSteps: MissingStep[];
  activeBranches: number;
  branchesWithoutHours: number;
  documents: { required: number; uploaded: number; accepted: number; rejected: number };
}

export interface OwnerDashboard {
  byStatus: Partial<Record<OrganizationStatus, number>>;
  businesses: OwnerBusinessCard[];
  upcomingHolidays: {
    organizationId: number;
    organizationName: string;
    branchId: number;
    branchName: string;
    date: string;
    reason?: string;
  }[];
}

export type UserStatus = 'ACTIVE' | 'SUSPENDED';

export const dashboardApi = {
  admin: () => apiGet<AdminDashboard>('/admin/dashboard'),
  owner: () => apiGet<OwnerDashboard>('/owner/dashboard'),
};

export const adminUserApi = {
  search: (params: { q?: string; status?: UserStatus; role?: Role; page?: number; size?: number }) =>
    apiGet<Page<UserView>>('/admin/users', { params }),
  setStatus: (id: number, status: UserStatus, reason?: string) =>
    apiPatch<UserView>(`/admin/users/${id}/status`, { status, reason }),
  resetTwoFactor: (id: number, reason: string) => apiPost<UserView>(`/admin/users/${id}/two-factor/reset`, { reason }),
};

export const auditApi = {
  search: (params: {
    action?: string;
    entityType?: string;
    entityId?: string;
    actorUserId?: number;
    page?: number;
    size?: number;
  }) => apiGet<Page<AuditEntry>>('/admin/audit-logs', { params }),
};

/** The dashboard a user lands on: the most powerful role wins. */
export function homeFor(roles: Role[]): string {
  if (roles.includes('SUPER_ADMIN')) return '/admin';
  if (roles.includes('BUSINESS_OWNER')) return '/owner';
  if (roles.includes('STAFF')) return '/staff';
  return '/customer';
}
