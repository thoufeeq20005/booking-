import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import i18n from '@/i18n';

/** Mirrors the backend ApiResponse envelope. */
export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  error?: ApiErrorBody;
  timestamp: string;
}

export interface ApiFieldError {
  field: string;
  code: string;
  message: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors: ApiFieldError[];
}

/** Normalised error thrown by every API call. `code` maps to the `errors.*` translation keys. */
export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number | null,
    public readonly fieldErrors: ApiFieldError[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// Same-origin by default: the Vite dev server and Vercel both proxy /api to the backend, which keeps the
// refresh cookie first-party (SameSite=Strict) and avoids CORS entirely.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

/** Required by the backend on cookie-authenticated endpoints (refresh, logout) as a CSRF defence. */
export const CLIENT_HEADER = { 'X-BookEasy-Client': 'web' };

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

// ---------------------------------------------------------------------------
// Session state. The access token lives only in memory; the refresh token is an HttpOnly cookie.
// ---------------------------------------------------------------------------

export interface UserView {
  id: number;
  fullName: string;
  email?: string;
  phone?: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  roles: Role[];
  preferredLanguage: string;
  twoFactorEnabled: boolean;
  hasPassword: boolean;
  status: string;
  createdAt: string;
}

export type Role = 'CUSTOMER' | 'BUSINESS_OWNER' | 'STAFF' | 'SUPER_ADMIN';

export interface Session {
  /** EMAIL_CODE_REQUIRED: the password was right and a code was emailed; TWO_FACTOR_REQUIRED: authenticator app. */
  status: 'AUTHENTICATED' | 'TWO_FACTOR_REQUIRED' | 'EMAIL_CODE_REQUIRED';
  accessToken?: string;
  accessTokenExpiresAt?: string;
  challengeToken?: string;
  user?: UserView;
  /** EMAIL_CODE_REQUIRED only: code lifetime, resend wait and (in development without email) the code itself. */
  emailCode?: { expiresInSeconds: number; resendAfterSeconds: number; demoCode?: string };
}

type SessionListener = (user: UserView | null) => void;

let accessToken: string | null = null;
const listeners = new Set<SessionListener>();

export const session = {
  /** Stores an AUTHENTICATED session and notifies the app. */
  apply(s: Session) {
    if (s.status !== 'AUTHENTICATED' || !s.accessToken || !s.user) return;
    accessToken = s.accessToken;
    listeners.forEach((l) => l(s.user!));
  },
  clear() {
    accessToken = null;
    listeners.forEach((l) => l(null));
  },
  subscribe(listener: SessionListener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  hasToken: () => accessToken !== null,
};

let inflightRefresh: Promise<boolean> | null = null;

/** Runs fn while holding a lock shared by every BookEasy tab, so tabs never rotate the cookie concurrently. */
async function withCrossTabLock<T>(fn: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  return locks ? locks.request('bookeasy-session-refresh', fn) : fn();
}

/** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
export function refreshSession(): Promise<boolean> {
  inflightRefresh ??= withCrossTabLock(async () => {
    try {
      const res = await apiClient.post<ApiEnvelope<Session>>('/auth/refresh', null, { headers: CLIENT_HEADER });
      if (res.data.data) session.apply(res.data.data);
      return true;
    } catch {
      session.clear();
      return false;
    }
  }).finally(() => {
    inflightRefresh = null;
  });
  return inflightRefresh;
}

const isAuthEndpoint = (url = '') => url.startsWith('/auth/') || url.startsWith('/public/');

apiClient.interceptors.request.use((config) => {
  config.headers.set('Accept-Language', i18n.language);
  if (accessToken && !isAuthEndpoint(config.url)) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return config;
});

apiClient.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  if (error.response?.status === 401 && config && !config._retried && !isAuthEndpoint(config.url)) {
    config._retried = true;
    if (await refreshSession()) return apiClient.request(config);
  }
  throw error;
});

export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err;
  if (err instanceof AxiosError) {
    const body = err.response?.data as ApiEnvelope<unknown> | undefined;
    if (body?.error) {
      return new ApiError(body.error.code, body.error.message, err.response?.status ?? null, body.error.fieldErrors);
    }
    if (!err.response) return new ApiError('NETWORK_ERROR', 'Cannot reach the server', null);
    return new ApiError('INTERNAL_ERROR', err.message, err.response.status);
  }
  return new ApiError('INTERNAL_ERROR', String(err), null);
}

async function call<T>(config: AxiosRequestConfig, acceptErrorStatus = false): Promise<T> {
  try {
    const res = await apiClient.request<ApiEnvelope<T>>({
      ...config,
      validateStatus: acceptErrorStatus ? () => true : undefined,
    });
    if (res.status === 204) return undefined as T;
    if (res.data?.success) return res.data.data as T;
    throw toApiError(new AxiosError('Request failed', undefined, res.config, res.request, res));
  } catch (err) {
    throw toApiError(err);
  }
}

/** GET that unwraps the envelope. With acceptErrorStatus, non-2xx envelopes (e.g. 503 health) are returned. */
export function apiGet<T>(url: string, opts: { acceptErrorStatus?: boolean; params?: object } = {}): Promise<T> {
  return call<T>({ method: 'GET', url, params: opts.params }, opts.acceptErrorStatus);
}

export function apiPost<T>(url: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  return call<T>({ method: 'POST', url, data: body, headers });
}

export function apiPatch<T>(url: string, body?: unknown): Promise<T> {
  return call<T>({ method: 'PATCH', url, data: body });
}

export function apiPut<T>(url: string, body?: unknown): Promise<T> {
  return call<T>({ method: 'PUT', url, data: body });
}

export function apiDelete<T = void>(url: string): Promise<T> {
  return call<T>({ method: 'DELETE', url });
}

/** Multipart upload of a single file in the `file` part. */
export function apiUpload<T>(url: string, file: File): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  return call<T>({ method: 'POST', url, data: form, timeout: 60000 });
}

/**
 * Downloads a protected file with the session's access token. Private documents are never addressable
 * by a plain link, so the UI shows them from an object URL made from this blob.
 */
export async function apiBlob(url: string): Promise<Blob> {
  try {
    const res = await apiClient.get<Blob>(url, { responseType: 'blob', timeout: 60000 });
    return res.data;
  } catch (err) {
    if (err instanceof AxiosError && err.response?.data instanceof Blob) {
      try {
        const text = await err.response.data.text();
        err.response.data = JSON.parse(text);
      } catch {
        // not JSON: fall through with the generic error
      }
    }
    throw toApiError(err);
  }
}
