import { useState } from 'react';
import type { FormEvent } from 'react';
import { useAccount, useDeposit, useWithdraw } from '../api/queries';

interface AccountCardProps {
  accountId: string;
}

export function AccountCard({ accountId }: AccountCardProps) {
  const accountQuery = useAccount(accountId);
  const depositMutation = useDeposit(accountId);
  const withdrawMutation = useWithdraw(accountId);
  const [depositAmount, setDepositAmount] = useState('0');
  const [withdrawAmount, setWithdrawAmount] = useState('0');

  if (accountQuery.isLoading) {
    return <p>Loading account {accountId}...</p>;
  }

  if (accountQuery.isError) {
    return (
      <div className="account-card account-card-error">
        <p>
          Could not load account {accountId}: {accountQuery.error?.message}
        </p>
      </div>
    );
  }

  const account = accountQuery.data;
  if (!account) {
    return null;
  }

  const currency = account.currency;

  function handleDeposit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    depositMutation.mutate({ amount: Number(depositAmount), currency }, { onSuccess: () => setDepositAmount('0') });
  }

  function handleWithdraw(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    withdrawMutation.mutate(
      { amount: Number(withdrawAmount), currency },
      { onSuccess: () => setWithdrawAmount('0') },
    );
  }

  return (
    <div className="account-card">
      <h3>{account.ownerName}</h3>
      <p className="account-id">{account.accountId}</p>
      <p className="balance">
        {account.balance} {account.currency}
      </p>

      <form onSubmit={handleDeposit} className="inline-form">
        <input
          type="number"
          min="0"
          step="0.01"
          value={depositAmount}
          onChange={(event) => setDepositAmount(event.target.value)}
          aria-label={`Deposit amount for ${account.accountId}`}
        />
        <button type="submit" disabled={depositMutation.isPending}>
          Deposit
        </button>
      </form>
      {depositMutation.error && <p className="form-error">{depositMutation.error.message}</p>}

      <form onSubmit={handleWithdraw} className="inline-form">
        <input
          type="number"
          min="0"
          step="0.01"
          value={withdrawAmount}
          onChange={(event) => setWithdrawAmount(event.target.value)}
          aria-label={`Withdraw amount for ${account.accountId}`}
        />
        <button type="submit" disabled={withdrawMutation.isPending}>
          Withdraw
        </button>
      </form>
      {withdrawMutation.error && <p className="form-error">{withdrawMutation.error.message}</p>}
    </div>
  );
}
