import { describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import * as api from '../api/client';
import { AccountCard } from './AccountCard';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, getAccount: vi.fn(), deposit: vi.fn(), withdraw: vi.fn() };
});

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('AccountCard', () => {
  it('shows the account balance once loaded', async () => {
    vi.mocked(api.getAccount).mockResolvedValue({
      accountId: 'abc-123',
      ownerName: 'Ada Lovelace',
      balance: 100,
      currency: 'USD',
    });

    renderWithClient(<AccountCard accountId="abc-123" />);

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('100 USD')).toBeInTheDocument();
  });

  it('deposits an amount and calls the deposit endpoint with the account currency', async () => {
    vi.mocked(api.getAccount).mockResolvedValue({
      accountId: 'abc-123',
      ownerName: 'Ada Lovelace',
      balance: 100,
      currency: 'USD',
    });
    vi.mocked(api.deposit).mockResolvedValue({
      accountId: 'abc-123',
      ownerName: 'Ada Lovelace',
      balance: 150,
      currency: 'USD',
    });
    const user = userEvent.setup();

    renderWithClient(<AccountCard accountId="abc-123" />);
    await screen.findByText('Ada Lovelace');

    const depositInput = screen.getByLabelText('Deposit amount for abc-123');
    await user.clear(depositInput);
    await user.type(depositInput, '50');
    await user.click(screen.getByRole('button', { name: 'Deposit' }));

    await waitFor(() => expect(api.deposit).toHaveBeenCalledWith('abc-123', { amount: 50, currency: 'USD' }));
  });

  it('shows an error message when the account cannot be found', async () => {
    vi.mocked(api.getAccount).mockRejectedValue(new api.ApiError('ACCOUNT_NOT_FOUND', 'Account not found: abc-123'));

    renderWithClient(<AccountCard accountId="abc-123" />);

    expect(await screen.findByText(/Account not found: abc-123/)).toBeInTheDocument();
  });
});
