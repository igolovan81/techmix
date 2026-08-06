import { useState } from 'react';

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
        {activeTab === 'accounts' && <p>Accounts tab coming soon.</p>}
        {activeTab === 'transfer' && <p>Transfer tab coming soon.</p>}
        {activeTab === 'statement' && <p>Statement tab coming soon.</p>}
      </main>
    </div>
  );
}
