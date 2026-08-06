import { beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    openAccount: vi.fn(),
    getAccount: vi.fn(),
    deposit: vi.fn(),
    withdraw: vi.fn(),
    transfer: vi.fn(),
    getStatement: vi.fn(),
  };
});

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  );
}

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('shows the Accounts tab by default', () => {
    renderApp();
    expect(screen.getByRole('heading', { name: 'Open account' })).toBeInTheDocument();
  });

  it('switches to the Transfer tab on click', async () => {
    const user = userEvent.setup();
    renderApp();
    await user.click(screen.getByRole('button', { name: 'Transfer' }));
    expect(screen.getByRole('heading', { name: 'Transfer money' })).toBeInTheDocument();
  });

  it('switches to the Statement tab on click', async () => {
    const user = userEvent.setup();
    renderApp();
    await user.click(screen.getByRole('button', { name: 'Statement' }));
    expect(screen.getByRole('heading', { name: 'Account statement' })).toBeInTheDocument();
  });
});
