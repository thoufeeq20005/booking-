import { apiDelete, apiGet, apiPost } from './apiClient';

export type PromotionKind = 'DISCOUNT' | 'FREE' | 'PACKAGE' | 'EVENT';

export interface Promotion {
  id: number;
  title: string;
  details?: string;
  kind: PromotionKind;
  percentOff?: number;
  validFrom?: string;
  validUntil?: string;
  current: boolean;
}

export interface PromotionInput {
  title: string;
  details?: string;
  kind: PromotionKind;
  percentOff?: number;
  validFrom?: string;
  validUntil?: string;
}

/** A real offer near the user (type FREE_SERVICE: a free class or assessment). */
export interface RecommendedOffer {
  id: string;
  type: PromotionKind | 'FREE_SERVICE';
  businessName: string;
  slug: string;
  category: string;
  title: string;
  details?: string;
  percentOff?: number;
  distanceKm: number;
  rating: number | string;
  reviewCount: number;
  coverUrl?: string;
}

export interface Recommendations {
  /** AI: chosen and explained by Claude; RULES: ranked by savings, rating and distance (no API key, or AI unavailable). */
  source: 'AI' | 'RULES';
  intro?: string;
  items: { offer: RecommendedOffer; headline: string; reason?: string }[];
}

export const recommendApi = {
  nearby: (p: { lat: number; lng: number; q?: string; radiusKm?: number }) => apiGet<Recommendations>('/public/recommendations', { params: p }),
  forBusiness: (slug: string) => apiGet<Promotion[]>(`/public/organizations/${encodeURIComponent(slug)}/promotions`),
  ownerList: (orgId: number) => apiGet<Promotion[]>(`/owner/organizations/${orgId}/promotions`),
  ownerAdd: (orgId: number, body: PromotionInput) => apiPost<Promotion[]>(`/owner/organizations/${orgId}/promotions`, body),
  ownerDelete: (orgId: number, id: number) => apiDelete<Promotion[]>(`/owner/organizations/${orgId}/promotions/${id}`),
};
