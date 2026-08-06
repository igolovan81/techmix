import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'banking-demo.known-accounts';

export function loadAccountIds(): string[] {
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((value): value is string => typeof value === 'string') : [];
  } catch {
    return [];
  }
}

function saveAccountIds(accountIds: string[]): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(accountIds));
}

export function useAccountRegistry() {
  const [accountIds, setAccountIds] = useState<string[]>(() => loadAccountIds());

  useEffect(() => {
    saveAccountIds(accountIds);
  }, [accountIds]);

  const addAccount = useCallback((accountId: string) => {
    setAccountIds((current) => (current.includes(accountId) ? current : [...current, accountId]));
  }, []);

  return { accountIds, addAccount };
}
