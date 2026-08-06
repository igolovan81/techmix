import { useState } from 'react';
import { AccountsTab } from './accounts/AccountsTab';
import { TransferForm } from './transfer/TransferForm';
import { StatementView } from './statement/StatementView';

type Tab = 'accounts' | 'transfer' | 'statement';

const TABS: { id: Tab; label: string }[] = [
  { id: 'accounts', label: 'Accounts' },
  { id: 'transfer', label: 'Transfer' },
  { id: 'statement', label: 'Statement' },
];

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('accounts');

  return (
    <div className="app">
      <h1>Banking Ledger Demo</h1>
      <nav className="tabs">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={activeTab === tab.id ? 'tab tab-active' : 'tab'}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </nav>
      <main className="tab-panel">
        {activeTab === 'accounts' && <AccountsTab />}
        {activeTab === 'transfer' && <TransferForm />}
        {activeTab === 'statement' && <StatementView />}
      </main>
    </div>
  );
}
