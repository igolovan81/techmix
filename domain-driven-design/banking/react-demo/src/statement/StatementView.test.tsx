import { describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import * as api from '../api/client';
import { StatementView } from './StatementView';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, getStatement: vi.fn() };
});

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('StatementView', () => {
  it('shows the statement lines for the entered account id', async () => {
    vi.mocked(api.getStatement).mockResolvedValue([
      {
        id: 'line-1',
        accountId: 'abc-123',
        type: 'CREDIT',
        amount: 100,
        currencyCode: 'USD',
        description: 'Account opened',
        occurredAt: '2026-08-05T10:00:00Z',
      },
    ]);
    const user = userEvent.setup();

    renderWithClient(<StatementView />);
    await user.type(screen.getByLabelText('Account'), 'abc-123');

    expect(await screen.findByText('Account opened')).toBeInTheDocument();
    expect(screen.getByText('CREDIT')).toBeInTheDocument();
    expect(api.getStatement).toHaveBeenCalledWith('abc-123');
  });

  it('shows an error message when the statement cannot be loaded', async () => {
    vi.mocked(api.getStatement).mockRejectedValue(new api.ApiError('ACCOUNT_NOT_FOUND', 'Account not found: abc-999'));
    const user = userEvent.setup();

    renderWithClient(<StatementView />);
    await user.type(screen.getByLabelText('Account'), 'abc-999');

    expect(await screen.findByText('Account not found: abc-999')).toBeInTheDocument();
  });
});
