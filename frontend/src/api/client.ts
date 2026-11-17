import type { ApiErrorResponse } from '../types/api';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'https://localhost:8443/api';

export class ApiError extends Error {
  errorCode: string;
  traceId: string;
  status: number;

  constructor(status: number, body: ApiErrorResponse) {
    super(body.message);
    this.errorCode = body.errorCode;
    this.traceId = body.traceId;
    this.status = status;
  }
}

export class NetworkError extends Error {}

let authToken: string | null = localStorage.getItem('swiftpay_token');
let onUnauthorized: (() => void) | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
  if (token) {
    localStorage.setItem('swiftpay_token', token);
  } else {
    localStorage.removeItem('swiftpay_token');
  }
}

export function getAuthToken(): string | null {
  return authToken;
}

export function registerUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new NetworkError('Sunucuya ulaşılamadı. İnternet bağlantınızı kontrol edin.');
  }

  if (response.status === 401 && onUnauthorized) {
    onUnauthorized();
  }

  if (!response.ok) {
    let body: ApiErrorResponse;
    try {
      body = await response.json();
    } catch {
      throw new ApiError(response.status, {
        errorCode: 'UNKNOWN_ERROR',
        message: `Beklenmeyen hata (HTTP ${response.status})`,
        traceId: '',
        timestamp: new Date().toISOString(),
      });
    }
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
};
