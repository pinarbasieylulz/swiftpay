import { api } from './client';
import type { TransferRequest, TransferResponse, MonthlyStatisticResponse } from '../types/api';

export const createTransfer = (req: TransferRequest) =>
  api.post<TransferResponse>('/transfers', req);

export const getTransferById = (id: string) =>
  api.get<TransferResponse>(`/transfers/${id}`);

export const listTransfers = () => api.get<TransferResponse[]>('/transfers');

export const listTransfersByDateRange = (startDate: string, endDate: string) =>
  api.get<TransferResponse[]>(
    `/transfers?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`,
  );

export const getMonthlyStatistics = () =>
  api.get<MonthlyStatisticResponse[]>('/statistics/monthly');
