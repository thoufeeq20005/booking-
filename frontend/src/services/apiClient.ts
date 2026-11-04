import axios, { AxiosError } from 'axios';
import i18n from '@/i18n';

/** Mirrors the backend ApiResponse envelope. */
export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  error?: ApiErrorBody;
  timestamp: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors: { field: string; code: string; message: string }[];
}

/** Normalised error thrown by every API call. `code` maps to the `errors.*` translation keys. */
export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number | null,
    public readonly fieldErrors: ApiErrorBody['fieldErrors'] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { Accept: 'application/json' },
});

apiClient.interceptors.request.use((config) => {
  config.headers.set('Accept-Language', i18n.language);
  return config;
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

/** GET that unwraps the envelope. Non-2xx responses still carrying an envelope (e.g. 503 health) are returned. */
export async function apiGet<T>(url: string, opts: { acceptErrorStatus?: boolean } = {}): Promise<T> {
  try {
    const res = await apiClient.get<ApiEnvelope<T>>(url, {
      validateStatus: opts.acceptErrorStatus ? () => true : undefined,
    });
    if (res.data?.success && res.data.data !== undefined) return res.data.data;
    throw toApiError(new AxiosError('Request failed', undefined, res.config, res.request, res));
  } catch (err) {
    throw toApiError(err);
  }
}
