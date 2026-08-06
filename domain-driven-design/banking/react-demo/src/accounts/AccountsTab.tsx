import { useAccountRegistry } from './AccountRegistry';
import { OpenAccountForm } from './OpenAccountForm';
import { AccountCard } from './AccountCard';

export function AccountsTab() {
  const { accountIds, addAccount } = useAccountRegistry();

  return (
    <div className="accounts-tab">
      <OpenAccountForm onOpened={addAccount} />

      <h2>Known accounts</h2>
      {accountIds.length === 0 && <p>No accounts opened yet in this browser.</p>}
      <div className="account-list">
        {accountIds.map((accountId) => (
          <AccountCard key={accountId} accountId={accountId} />
        ))}
      </div>
    </div>
  );
}
