import { useState } from 'react';
import type { FormEvent } from 'react';
import { useOpenAccount } from '../api/queries';

interface OpenAccountFormProps {
  onOpened: (accountId: string) => void;
}

export function OpenAccountForm({ onOpened }: OpenAccountFormProps) {
  const [ownerName, setOwnerName] = useState('');
  const [initialBalance, setInitialBalance] = useState('0');
  const [currency, setCurrency] = useState('USD');
  const mutation = useOpenAccount();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate(
      { ownerName, initialBalance: Number(initialBalance), currency },
      {
        onSuccess: (response) => {
          onOpened(response.accountId);
          setOwnerName('');
          setInitialBalance('0');
        },
      },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="open-account-form">
      <h2>Open account</h2>
      <label>
        Owner name
        <input value={ownerName} onChange={(event) => setOwnerName(event.target.value)} required />
      </label>
      <label>
        Initial balance
        <input
          type="number"
          min="0"
          step="0.01"
          value={initialBalance}
          onChange={(event) => setInitialBalance(event.target.value)}
          required
        />
      </label>
      <label>
        Currency
        <input value={currency} onChange={(event) => setCurrency(event.target.value.toUpperCase())} required />
      </label>
      <button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? 'Opening...' : 'Open account'}
      </button>
      {mutation.error && <p className="form-error">{mutation.error.message}</p>}
    </form>
  );
}
