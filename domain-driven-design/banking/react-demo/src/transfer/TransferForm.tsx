import { useState } from 'react';
import type { FormEvent } from 'react';
import { useAccountRegistry } from '../accounts/AccountRegistry';
import { useTransfer } from '../api/queries';

export function TransferForm() {
  const { accountIds } = useAccountRegistry();
  const [fromAccountId, setFromAccountId] = useState('');
  const [toAccountId, setToAccountId] = useState('');
  const [amount, setAmount] = useState('0');
  const [currency, setCurrency] = useState('USD');
  const mutation = useTransfer();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate(
      { fromAccountId, toAccountId, amount: Number(amount), currency },
      { onSuccess: () => setAmount('0') },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="transfer-form">
      <h2>Transfer money</h2>
      <datalist id="known-accounts">
        {accountIds.map((accountId) => (
          <option key={accountId} value={accountId} />
        ))}
      </datalist>

      <label>
        From account
        <input
          list="known-accounts"
          value={fromAccountId}
          onChange={(event) => setFromAccountId(event.target.value)}
          required
        />
      </label>
      <label>
        To account
        <input
          list="known-accounts"
          value={toAccountId}
          onChange={(event) => setToAccountId(event.target.value)}
          required
        />
      </label>
      <label>
        Amount
        <input
          type="number"
          min="0"
          step="0.01"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          required
        />
      </label>
      <label>
        Currency
        <input value={currency} onChange={(event) => setCurrency(event.target.value.toUpperCase())} required />
      </label>
      <button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? 'Transferring...' : 'Transfer'}
      </button>
      {mutation.isSuccess && <p className="form-success">Transfer {mutation.data.transferId} completed.</p>}
      {mutation.error && <p className="form-error">{mutation.error.message}</p>}
    </form>
  );
}
