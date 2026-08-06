import { beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import * as api from '../api/client';
import { AccountsTab } from './AccountsTab';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, openAccount: vi.fn(), getAccount: vi.fn(), deposit: vi.fn(), withdraw: vi.fn() };
});

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('AccountsTab', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('adds a new account card after opening an account', async () => {
    vi.mocked(api.openAccount).mockResolvedValue({ accountId: 'new-id' });
    vi.mocked(api.getAccount).mockResolvedValue({
      accountId: 'new-id',
      ownerName: 'Ada Lovelace',
      balance: 100,
      currency: 'USD',
    });
    const user = userEvent.setup();

    renderWithClient(<AccountsTab />);

    expect(screen.getByText('No accounts opened yet in this browser.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Owner name'), 'Ada Lovelace');
    await user.clear(screen.getByLabelText('Initial balance'));
    await user.type(screen.getByLabelText('Initial balance'), '100');
    await user.click(screen.getByRole('button', { name: 'Open account' }));

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
  });
});
