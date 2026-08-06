import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from './client';
import type { AmountRequest, OpenAccountRequest, TransferRequest } from './types';

export function useAccount(accountId: string | undefined) {
  return useQuery({
    queryKey: ['account', accountId],
    queryFn: () => api.getAccount(accountId as string),
    enabled: Boolean(accountId),
  });
}

export function useStatement(accountId: string | undefined) {
  return useQuery({
    queryKey: ['statement', accountId],
    queryFn: () => api.getStatement(accountId as string),
    enabled: Boolean(accountId),
  });
}

export function useOpenAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: OpenAccountRequest) => api.openAccount(body),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['account', response.accountId] });
    },
  });
}

export function useDeposit(accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AmountRequest) => api.deposit(accountId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account', accountId] });
      queryClient.invalidateQueries({ queryKey: ['statement', accountId] });
    },
  });
}

export function useWithdraw(accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AmountRequest) => api.withdraw(accountId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account', accountId] });
      queryClient.invalidateQueries({ queryKey: ['statement', accountId] });
    },
  });
}

export function useTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: TransferRequest) => api.transfer(body),
    onSuccess: (_response, variables) => {
      queryClient.invalidateQueries({ queryKey: ['account', variables.fromAccountId] });
      queryClient.invalidateQueries({ queryKey: ['account', variables.toAccountId] });
      queryClient.invalidateQueries({ queryKey: ['statement', variables.fromAccountId] });
      queryClient.invalidateQueries({ queryKey: ['statement', variables.toAccountId] });
    },
  });
}
