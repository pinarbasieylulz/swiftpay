export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresInMs: number;
}

export interface AccountResponse {
  id: string;
  iban: string;
  balance: number;
  currency: string;
}

export interface LimitResponse {
  perTransferLimit: number;
  dailyLimit: number;
  dailyRemaining: number;
  monthlyLimit: number;
  monthlyRemaining: number;
}

export interface TransferRequest {
  sourceAccountId: string;
  destinationIban: string;
  amount: string;
  description?: string;
  idempotencyKey: string;
}

export interface TransferResponse {
  id: string;
  sourceAccountId: string;
  destinationIban: string;
  amount: number;
  currency: string;
  description?: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  failureReason?: string;
  createdAt: string;
  completedAt?: string;
}

export interface MonthlyStatisticResponse {
  month: string; // "YYYY-MM"
  totalTransferCount: number;
  totalTransferredAmount: number;
}

export interface ApiErrorResponse {
  errorCode: string;
  message: string;
  traceId: string;
  timestamp: string;
}
