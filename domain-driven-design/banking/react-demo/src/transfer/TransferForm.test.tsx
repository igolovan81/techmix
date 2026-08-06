import { describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import * as api from '../api/client';
import { TransferForm } from './TransferForm';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, transfer: vi.fn() };
});

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('TransferForm', () => {
  it('submits a transfer and shows the returned transfer id', async () => {
    vi.mocked(api.transfer).mockResolvedValue({ transferId: 'transfer-1' });
    const user = userEvent.setup();

    renderWithClient(<TransferForm />);

    await user.type(screen.getByLabelText('From account'), 'account-a');
    await user.type(screen.getByLabelText('To account'), 'account-b');
    await user.clear(screen.getByLabelText('Amount'));
    await user.type(screen.getByLabelText('Amount'), '75');
    await user.click(screen.getByRole('button', { name: 'Transfer' }));

    expect(await screen.findByText('Transfer transfer-1 completed.')).toBeInTheDocument();
    expect(api.transfer).toHaveBeenCalledWith({
      fromAccountId: 'account-a',
      toAccountId: 'account-b',
      amount: 75,
      currency: 'USD',
    });
  });

  it('shows the backend error message when the transfer fails', async () => {
    vi.mocked(api.transfer).mockRejectedValue(
      new api.ApiError('INSUFFICIENT_FUNDS', 'Account account-a has insufficient funds'),
    );
    const user = userEvent.setup();

    renderWithClient(<TransferForm />);

    await user.type(screen.getByLabelText('From account'), 'account-a');
    await user.type(screen.getByLabelText('To account'), 'account-b');
    await user.click(screen.getByRole('button', { name: 'Transfer' }));

    expect(await screen.findByText('Account account-a has insufficient funds')).toBeInTheDocument();
  });
});
