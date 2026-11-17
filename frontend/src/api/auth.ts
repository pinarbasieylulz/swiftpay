import { api, setAuthToken } from './client';
import type { LoginRequest, LoginResponse } from '../types/api';

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const response = await api.post<LoginResponse>('/auth/login', credentials);
  setAuthToken(response.token);
  return response;
}

export function logout() {
  setAuthToken(null);
}
