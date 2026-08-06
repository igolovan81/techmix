import type {
  AccountResponse,
  AmountRequest,
  ApiErrorBody,
  OpenAccountRequest,
  OpenAccountResponse,
  StatementLine,
  TransferRequest,
  TransferResponse,
} from './types';

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });

  if (!response.ok) {
    const body = (await response.json()) as ApiErrorBody;
    throw new ApiError(body.error, body.message);
  }

  return (await response.json()) as T;
}

export function openAccount(body: OpenAccountRequest): Promise<OpenAccountResponse> {
  return request<OpenAccountResponse>('/accounts', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getAccount(accountId: string): Promise<AccountResponse> {
  return request<AccountResponse>(`/accounts/${accountId}`);
}

export function deposit(accountId: string, body: AmountRequest): Promise<AccountResponse> {
  return request<AccountResponse>(`/accounts/${accountId}/deposits`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function withdraw(accountId: string, body: AmountRequest): Promise<AccountResponse> {
  return request<AccountResponse>(`/accounts/${accountId}/withdrawals`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function transfer(body: TransferRequest): Promise<TransferResponse> {
  return request<TransferResponse>('/transfers', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getStatement(accountId: string): Promise<StatementLine[]> {
  return request<StatementLine[]>(`/accounts/${accountId}/statement`);
}
