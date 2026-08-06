import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

describe('App', () => {
  it('shows the Accounts tab by default', () => {
    render(<App />);
    expect(screen.getByText('Accounts tab coming soon.')).toBeInTheDocument();
  });

  it('switches to the Transfer tab on click', async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole('button', { name: 'Transfer' }));
    expect(screen.getByText('Transfer tab coming soon.')).toBeInTheDocument();
  });

  it('switches to the Statement tab on click', async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole('button', { name: 'Statement' }));
    expect(screen.getByText('Statement tab coming soon.')).toBeInTheDocument();
  });
});
