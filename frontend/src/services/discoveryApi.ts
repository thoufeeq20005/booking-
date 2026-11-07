import { apiGet } from './apiClient';
import type { DayHours, LocalizedText } from './organizationApi';

export interface SearchHit {
  organizationId: number;
  slug: string;
  name: string;
  ratingAvg: number;
  reviewCount: number;
  logoUrl?: string;
  coverUrl?: string;
  categoryId: number;
  subcategoryId: number;
  subcategoryName: LocalizedText;
  branchId: number;
  branchName: string;
  locality?: string;
  city: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  fromPrice?: number | string;
}

export interface PublicProfile {
  id: number;
  slug: string;
  name: string;
  description?: string;
  logoUrl?: string;
  coverUrl?: string;
  categoryName: LocalizedText;
  subcategoryName: LocalizedText;
  categoryId: number;
  bookingWorkflow: string;
  ratingAvg: number;
  reviewCount: number;
  contactPhone?: string;
  contactEmail?: string;
  website?: string;
  confirmationMode: 'INSTANT' | 'APPROVAL';
  branches: {
    id: number; name: string; addressLine1: string; addressLine2?: string; locality?: string; city: string; state: string;
    pincode: string; latitude: number; longitude: number; phone?: string; timezone: string; hours: DayHours[];
  }[];
  services: {
    id: number; name: string; description?: string; durationMinutes: number; price: number | string;
    consultationType: 'IN_PERSON' | 'ONLINE'; requiresStaff: boolean; branchPrices: Record<string, number | string>;
    imageUrl?: string;
  }[];
  staff: { id: number; displayName: string; title?: string; specialisation?: string; branchIds: number[]; serviceIds: number[] }[];
}

export interface Suggestion {
  type: 'BUSINESS' | 'SUBCATEGORY';
  id: number;
  slug?: string;
  label?: string;
  labels?: LocalizedText;
}

export type SortKey = 'DISTANCE' | 'RATING' | 'PRICE';

export const discoveryApi = {
  search: (params: { lat: number; lng: number; radiusKm: number; categoryId?: number; subcategoryId?: number; q?: string;
    sort: SortKey; page?: number }) =>
    apiGet<{ items: SearchHit[]; page: number; size: number; hasMore: boolean }>('/public/search', { params }),
  suggest: (q: string) => apiGet<Suggestion[]>('/public/suggest', { params: { q } }),
  profile: (slug: string) => apiGet<PublicProfile>(`/public/organizations/${encodeURIComponent(slug)}`),
};

/** Major cities for people who don't share their location (no geocoding key needed). */
export const CITIES = [
  { key: 'chennai', lat: 13.0827, lng: 80.2707 },
  { key: 'bengaluru', lat: 12.9716, lng: 77.5946 },
  { key: 'hyderabad', lat: 17.385, lng: 78.4867 },
  { key: 'mumbai', lat: 19.076, lng: 72.8777 },
  { key: 'delhi', lat: 28.6139, lng: 77.209 },
  { key: 'kolkata', lat: 22.5726, lng: 88.3639 },
  { key: 'pune', lat: 18.5204, lng: 73.8567 },
  { key: 'kochi', lat: 9.9312, lng: 76.2673 },
  { key: 'coimbatore', lat: 11.0168, lng: 76.9558 },
  { key: 'madurai', lat: 9.9252, lng: 78.1198 },
  { key: 'tiruchirappalli', lat: 10.7905, lng: 78.7047 },
  { key: 'salem', lat: 11.6643, lng: 78.146 },
  { key: 'thoothukudi', lat: 8.7642, lng: 78.1348 },
  { key: 'thiruvananthapuram', lat: 8.5241, lng: 76.9366 },
  { key: 'visakhapatnam', lat: 17.6868, lng: 83.2185 },
  { key: 'mysuru', lat: 12.2958, lng: 76.6394 },
] as const;

export const mapsLink = (lat: number, lng: number) => `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;
