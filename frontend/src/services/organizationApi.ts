import { useQuery } from '@tanstack/react-query';
import { apiBlob, apiDelete, apiGet, apiPatch, apiPost, apiPut, apiUpload } from './apiClient';

/** Translatable text keyed by language code; `en` is always present. */
export type LocalizedText = Record<string, string>;

export type BookingWorkflow = 'HEALTHCARE' | 'SALON' | 'EDUCATION' | 'GENERIC' | 'CINEMA' | 'DINING';
export type OrganizationStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
export type DocumentStatus = 'SUBMITTED' | 'ACCEPTED' | 'REJECTED';
export type ConfirmationMode = 'INSTANT' | 'APPROVAL';
export type MissingStep = 'CONTACT' | 'BRANCH' | 'WORKING_HOURS' | 'DOCUMENTS';
export type VerificationAction = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'REINSTATED';

export interface Subcategory {
  id: number;
  categoryId: number;
  slug: string;
  name: LocalizedText;
  active: boolean;
  sortOrder: number;
}

export interface DocumentRequirement {
  id: number;
  categoryId: number;
  code: string;
  label: LocalizedText;
  description?: LocalizedText;
  mandatory: boolean;
  allowedTypes: string[];
  maxSizeBytes: number;
  active: boolean;
  sortOrder: number;
}

export interface Category {
  id: number;
  slug: string;
  name: LocalizedText;
  description?: LocalizedText;
  icon: string;
  bookingWorkflow: BookingWorkflow;
  active: boolean;
  sortOrder: number;
  subcategories: Subcategory[];
  documentRequirements?: DocumentRequirement[];
}

export interface OrganizationSummary {
  id: number;
  name: string;
  slug: string;
  status: OrganizationStatus;
  statusReason?: string;
  categoryId?: number;
  categoryName?: LocalizedText;
  subcategoryId: number;
  subcategoryName?: LocalizedText;
  logoUrl?: string;
  coverUrl?: string;
  city?: string;
  ownerName?: string;
  ownerEmail?: string;
  submittedAt?: string;
  approvedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DayHours {
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
  breakStart?: string;
  breakEnd?: string;
}

export interface Branch {
  id: number;
  name: string;
  addressLine1: string;
  addressLine2?: string;
  locality?: string;
  city: string;
  state: string;
  pincode: string;
  latitude: number;
  longitude: number;
  phone?: string;
  timezone: string;
  active: boolean;
  hours: DayHours[];
}

export interface Holiday {
  id: number;
  date: string;
  reason?: string;
}

export interface UploadedDocument {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  reviewerNote?: string;
  uploadedAt: string;
  reviewedAt?: string;
}

export interface DocumentSlot {
  requirement: DocumentRequirement;
  document?: UploadedDocument;
}

export interface HistoryEntry {
  action: VerificationAction;
  fromStatus: OrganizationStatus;
  toStatus: OrganizationStatus;
  reason?: string;
  createdAt: string;
}

export interface Owner {
  userId: number;
  fullName: string;
  email?: string;
  phone?: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  role: 'PRIMARY' | 'CO_OWNER';
}

export interface OrganizationDetail {
  summary: OrganizationSummary;
  description?: string;
  contactEmail?: string;
  contactPhone?: string;
  website?: string;
  gstin?: string;
  confirmationMode: ConfirmationMode;
  editable: { identity: boolean; operations: boolean };
  missingSteps: MissingStep[];
  branches: Branch[];
  documents: DocumentSlot[];
  history: HistoryEntry[];
  owners?: Owner[];
}

export interface OrganizationInput {
  subcategoryId: number;
  name: string;
  description?: string;
  contactEmail?: string;
  contactPhone?: string;
  website?: string;
  gstin?: string;
  confirmationMode: ConfirmationMode;
}

export type BranchInput = Omit<Branch, 'id' | 'hours' | 'timezone'> & { timezone?: string };

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

const owner = (id: number) => `/owner/organizations/${id}`;

export const categoryApi = {
  publicTree: () => apiGet<Category[]>('/public/categories'),
};

export const ownerOrganizationApi = {
  create: (body: OrganizationInput) => apiPost<OrganizationDetail>('/business/organizations', body),
  list: () => apiGet<OrganizationSummary[]>('/owner/organizations'),
  get: (id: number) => apiGet<OrganizationDetail>(owner(id)),
  update: (id: number, body: OrganizationInput) => apiPut<OrganizationDetail>(owner(id), body),
  uploadLogo: (id: number, file: File) => apiUpload<OrganizationDetail>(`${owner(id)}/logo`, file),
  removeLogo: (id: number) => apiDelete<OrganizationDetail>(`${owner(id)}/logo`),
  uploadCover: (id: number, file: File) => apiUpload<OrganizationDetail>(`${owner(id)}/cover`, file),
  removeCover: (id: number) => apiDelete<OrganizationDetail>(`${owner(id)}/cover`),
  submit: (id: number) => apiPost<OrganizationDetail>(`${owner(id)}/submit`),
  createBranch: (id: number, body: BranchInput) => apiPost<Branch>(`${owner(id)}/branches`, body),
  updateBranch: (id: number, branchId: number, body: BranchInput) =>
    apiPut<Branch>(`${owner(id)}/branches/${branchId}`, body),
  deleteBranch: (id: number, branchId: number) => apiDelete(`${owner(id)}/branches/${branchId}`),
  setHours: (id: number, branchId: number, days: DayHours[]) =>
    apiPut<Branch>(`${owner(id)}/branches/${branchId}/hours`, { days }),
  holidays: (id: number, branchId: number) => apiGet<Holiday[]>(`${owner(id)}/branches/${branchId}/holidays`),
  addHoliday: (id: number, branchId: number, body: { date: string; reason?: string }) =>
    apiPost<Holiday>(`${owner(id)}/branches/${branchId}/holidays`, body),
  deleteHoliday: (id: number, branchId: number, holidayId: number) =>
    apiDelete(`${owner(id)}/branches/${branchId}/holidays/${holidayId}`),
  uploadDocument: (id: number, requirementId: number, file: File) =>
    apiUpload<DocumentSlot>(`${owner(id)}/documents/${requirementId}`, file),
  deleteDocument: (id: number, requirementId: number) => apiDelete(`${owner(id)}/documents/${requirementId}`),
  documentFile: (id: number, requirementId: number) => apiBlob(`${owner(id)}/documents/${requirementId}/file`),
};

const admin = (id: number) => `/admin/organizations/${id}`;

export const adminOrganizationApi = {
  search: (params: { q?: string; status?: OrganizationStatus; page?: number; size?: number }) =>
    apiGet<Page<OrganizationSummary>>('/admin/organizations', { params }),
  get: (id: number) => apiGet<OrganizationDetail>(admin(id)),
  documentFile: (id: number, documentId: number) => apiBlob(`${admin(id)}/documents/${documentId}/file`),
  reviewDocument: (id: number, documentId: number, body: { status: DocumentStatus; note?: string }) =>
    apiPatch<OrganizationDetail>(`${admin(id)}/documents/${documentId}`, body),
  approve: (id: number, reason?: string) => apiPost<OrganizationDetail>(`${admin(id)}/approve`, { reason }),
  reject: (id: number, reason: string) => apiPost<OrganizationDetail>(`${admin(id)}/reject`, { reason }),
  suspend: (id: number, reason: string) => apiPost<OrganizationDetail>(`${admin(id)}/suspend`, { reason }),
  reinstate: (id: number, reason?: string) => apiPost<OrganizationDetail>(`${admin(id)}/reinstate`, { reason }),
};

export interface CategoryInput {
  slug: string;
  name: LocalizedText;
  description?: LocalizedText;
  icon: string;
  bookingWorkflow: BookingWorkflow;
  active: boolean;
  sortOrder: number;
}

export interface SubcategoryInput {
  slug: string;
  name: LocalizedText;
  active: boolean;
  sortOrder: number;
}

export interface RequirementInput {
  code: string;
  label: LocalizedText;
  description?: LocalizedText;
  mandatory: boolean;
  allowedTypes: string[];
  maxSizeBytes: number;
  active: boolean;
  sortOrder: number;
}

const cat = (id: number) => `/admin/categories/${id}`;

export const adminCategoryApi = {
  list: () => apiGet<Category[]>('/admin/categories'),
  create: (body: CategoryInput) => apiPost<Category[]>('/admin/categories', body),
  update: (id: number, body: CategoryInput) => apiPut<Category[]>(cat(id), body),
  remove: (id: number) => apiDelete<Category[]>(cat(id)),
  createSubcategory: (id: number, body: SubcategoryInput) => apiPost<Category[]>(`${cat(id)}/subcategories`, body),
  updateSubcategory: (id: number, subId: number, body: SubcategoryInput) =>
    apiPut<Category[]>(`${cat(id)}/subcategories/${subId}`, body),
  removeSubcategory: (id: number, subId: number) => apiDelete<Category[]>(`${cat(id)}/subcategories/${subId}`),
  createRequirement: (id: number, body: RequirementInput) =>
    apiPost<Category[]>(`${cat(id)}/document-requirements`, body),
  updateRequirement: (id: number, reqId: number, body: RequirementInput) =>
    apiPut<Category[]>(`${cat(id)}/document-requirements/${reqId}`, body),
  removeRequirement: (id: number, reqId: number) =>
    apiDelete<Category[]>(`${cat(id)}/document-requirements/${reqId}`),
};

export function usePublicCategories() {
  return useQuery({ queryKey: ['public-categories'], queryFn: categoryApi.publicTree, staleTime: 10 * 60 * 1000 });
}

export const orgKeys = {
  mine: ['owner-organizations'] as const,
  detail: (id: number) => ['owner-organization', id] as const,
  adminList: (params: object) => ['admin-organizations', params] as const,
  adminDetail: (id: number) => ['admin-organization', id] as const,
  adminCategories: ['admin-categories'] as const,
};
