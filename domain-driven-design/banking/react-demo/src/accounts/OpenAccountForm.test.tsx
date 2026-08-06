import { describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import * as api from '../api/client';
import { OpenAccountForm } from './OpenAccountForm';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, openAccount: vi.fn() };
});

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('OpenAccountForm', () => {
  it('submits the form and calls onOpened with the new account id', async () => {
    vi.mocked(api.openAccount).mockResolvedValue({ accountId: 'new-id' });
    const onOpened = vi.fn();
    const user = userEvent.setup();

    renderWithClient(<OpenAccountForm onOpened={onOpened} />);

    await user.type(screen.getByLabelText('Owner name'), 'Ada Lovelace');
    await user.clear(screen.getByLabelText('Initial balance'));
    await user.type(screen.getByLabelText('Initial balance'), '100');
    await user.click(screen.getByRole('button', { name: 'Open account' }));

    await waitFor(() => expect(onOpened).toHaveBeenCalledWith('new-id'));
    expect(api.openAccount).toHaveBeenCalledWith({ ownerName: 'Ada Lovelace', initialBalance: 100, currency: 'USD' });
  });

  it('shows the backend error message when opening fails', async () => {
    vi.mocked(api.openAccount).mockRejectedValue(
      new api.ApiError('INVALID_AMOUNT', 'Initial balance must not be negative'),
    );
    const user = userEvent.setup();

    renderWithClient(<OpenAccountForm onOpened={vi.fn()} />);

    await user.type(screen.getByLabelText('Owner name'), 'Ada');
    await user.click(screen.getByRole('button', { name: 'Open account' }));

    expect(await screen.findByText('Initial balance must not be negative')).toBeInTheDocument();
  });
});
