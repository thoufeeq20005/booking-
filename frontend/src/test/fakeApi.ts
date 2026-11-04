import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { apiClient } from '@/services/apiClient';

export interface FakeRequest {
  method: string;
  url: string;
  body: Record<string, unknown> | null;
  headers: Record<string, string>;
}

export interface FakeResponse {
  status: number;
  body?: unknown;
}

const ok = (data: unknown): FakeResponse => ({ status: 200, body: { success: true, data, timestamp: '' } });
const fail = (status: number, code: string, fieldErrors: unknown[] = []): FakeResponse => ({
  status,
  body: { success: false, error: { code, message: code, fieldErrors }, timestamp: '' },
});
export const reply = { ok, fail, noContent: (): FakeResponse => ({ status: 204 }) };

/**
 * Replaces the HTTP adapter so real interceptors (auth header, refresh-and-retry) run against scripted
 * responses. Returns the list of requests made, for assertions.
 */
export function installFakeApi(handler: (req: FakeRequest) => FakeResponse | undefined) {
  const requests: FakeRequest[] = [];
  const adapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
    const headers: Record<string, string> = {};
    Object.entries(config.headers.toJSON()).forEach(([k, v]) => (headers[k.toLowerCase()] = String(v)));
    const req: FakeRequest = {
      method: (config.method ?? 'get').toUpperCase(),
      url: config.url ?? '',
      body: typeof config.data === 'string' && config.data ? JSON.parse(config.data) : null,
      headers,
    };
    requests.push(req);
    const res = handler(req) ?? fail(404, 'NOT_FOUND');
    const response: AxiosResponse = {
      data: res.body ?? '',
      status: res.status,
      statusText: '',
      headers: {},
      config,
      request: {},
    };
    const validate = config.validateStatus ?? ((s: number) => s >= 200 && s < 300);
    if (validate(res.status)) return response;
    throw new AxiosError(`Request failed with ${res.status}`, 'ERR_BAD_RESPONSE', config, {}, response);
  };
  apiClient.defaults.adapter = adapter;
  return requests;
}

export const sampleUser = {
  id: 1,
  fullName: 'Asha Kumar',
  email: 'asha@example.test',
  emailVerified: false,
  phoneVerified: false,
  roles: ['CUSTOMER'],
  preferredLanguage: 'en',
  twoFactorEnabled: false,
  hasPassword: true,
  status: 'ACTIVE',
  createdAt: '2026-11-04T10:00:00Z',
};

export const authenticated = (token = 'access-1', user: object = sampleUser) => ({
  status: 'AUTHENTICATED',
  accessToken: token,
  accessTokenExpiresAt: '2026-11-04T10:15:00Z',
  user,
});
