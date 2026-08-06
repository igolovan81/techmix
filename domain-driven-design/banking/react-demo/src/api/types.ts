export interface OpenAccountRequest {
  ownerName: string;
  initialBalance: number;
  currency: string;
}

export interface OpenAccountResponse {
  accountId: string;
}

export interface AccountResponse {
  accountId: string;
  ownerName: string;
  balance: number;
  currency: string;
}

export interface AmountRequest {
  amount: number;
  currency: string;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
}

export interface TransferResponse {
  transferId: string;
}

export type StatementLineType = 'DEBIT' | 'CREDIT';

export interface StatementLine {
  id: string;
  accountId: string;
  type: StatementLineType;
  amount: number;
  currencyCode: string;
  description: string;
  occurredAt: string;
}

export interface ApiErrorBody {
  error: string;
  message: string;
}
