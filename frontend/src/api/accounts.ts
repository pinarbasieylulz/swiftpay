import { api } from './client';
import type { AccountResponse, LimitResponse } from '../types/api';

export const getMyAccounts = () => api.get<AccountResponse[]>('/accounts/me');
export const getMyLimits = () => api.get<LimitResponse>('/accounts/me/limits');
