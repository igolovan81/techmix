import { useState } from 'react';
import { useAccountRegistry } from '../accounts/AccountRegistry';
import { useStatement } from '../api/queries';

export function StatementView() {
  const { accountIds } = useAccountRegistry();
  const [accountId, setAccountId] = useState('');
  const statementQuery = useStatement(accountId || undefined);

  return (
    <div className="statement-view">
      <h2>Account statement</h2>
      <datalist id="known-accounts-statement">
        {accountIds.map((id) => (
          <option key={id} value={id} />
        ))}
      </datalist>
      <label>
        Account
        <input
          list="known-accounts-statement"
          value={accountId}
          onChange={(event) => setAccountId(event.target.value)}
          placeholder="Paste or pick an account id"
        />
      </label>

      {statementQuery.isLoading && <p>Loading statement...</p>}
      {statementQuery.isError && <p className="form-error">{statementQuery.error?.message}</p>}

      {statementQuery.data && (
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Amount</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            {statementQuery.data.map((line) => (
              <tr key={line.id}>
                <td>{new Date(line.occurredAt).toLocaleString()}</td>
                <td>{line.type}</td>
                <td>
                  {line.amount} {line.currencyCode}
                </td>
                <td>{line.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
