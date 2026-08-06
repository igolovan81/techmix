import { describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import * as api from './client';
import { useAccount, useDeposit, useOpenAccount } from './queries';

vi.mock('./client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./client')>();
  return {
    ...actual,
    openAccount: vi.fn(),
    getAccount: vi.fn(),
    deposit: vi.fn(),
    withdraw: vi.fn(),
    transfer: vi.fn(),
    getStatement: vi.fn(),
  };
});

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('queries', () => {
  it('useAccount fetches the account by id', async () => {
    vi.mocked(api.getAccount).mockResolvedValue({
      accountId: 'abc-123',
      ownerName: 'Ada',
      balance: 100,
      currency: 'USD',
    });

    const { result } = renderHook(() => useAccount('abc-123'), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.balance).toBe(100);
    expect(api.getAccount).toHaveBeenCalledWith('abc-123');
  });

  it('useOpenAccount calls api.openAccount with the form values', async () => {
    vi.mocked(api.openAccount).mockResolvedValue({ accountId: 'new-id' });

    const { result } = renderHook(() => useOpenAccount(), { wrapper });
    result.current.mutate({ ownerName: 'Ada', initialBalance: 100, currency: 'USD' });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.openAccount).toHaveBeenCalledWith({ ownerName: 'Ada', initialBalance: 100, currency: 'USD' });
  });

  it('useDeposit calls api.deposit with the account id and amount', async () => {
    vi.mocked(api.deposit).mockResolvedValue({
      accountId: 'abc-123',
      ownerName: 'Ada',
      balance: 150,
      currency: 'USD',
    });

    const { result } = renderHook(() => useDeposit('abc-123'), { wrapper });
    result.current.mutate({ amount: 50, currency: 'USD' });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.deposit).toHaveBeenCalledWith('abc-123', { amount: 50, currency: 'USD' });
  });
});
