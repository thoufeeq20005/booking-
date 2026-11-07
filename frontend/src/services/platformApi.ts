import { useQuery } from '@tanstack/react-query';
import { apiGet } from './apiClient';

export interface PublicConfig {
  currency: 'INR';
  commissionPercent: number;
  supportedLanguages: string[];
  defaultLanguage: string;
  timezone: string;
  support: {
    email?: string;
    phone?: string;
    workingDays: string[];
    opensAt: string;
    closesAt: string;
    timezone: string;
    available: boolean;
  };
}

export interface HealthStatus {
  status: 'UP' | 'DEGRADED';
  database: 'UP' | 'DOWN';
  checkedAt: string;
}

export const platformApi = {
  getPublicConfig: () => apiGet<PublicConfig>('/public/config'),
  getHealth: () => apiGet<HealthStatus>('/health', { acceptErrorStatus: true }),
};

export function usePublicConfig() {
  return useQuery({ queryKey: ['public-config'], queryFn: platformApi.getPublicConfig, staleTime: 5 * 60_000 });
}

export function useHealth() {
  return useQuery({ queryKey: ['health'], queryFn: platformApi.getHealth, refetchInterval: 30_000 });
}
