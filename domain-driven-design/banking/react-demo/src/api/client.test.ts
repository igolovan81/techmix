import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, deposit, getAccount, openAccount } from './client';

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as unknown as Response;
}

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('openAccount posts to /accounts and returns the parsed body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ accountId: 'abc-123' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await openAccount({ ownerName: 'Ada', initialBalance: 100, currency: 'USD' });

    expect(result).toEqual({ accountId: 'abc-123' });
    expect(fetchMock).toHaveBeenCalledWith(
      '/accounts',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ ownerName: 'Ada', initialBalance: 100, currency: 'USD' }),
      }),
    );
  });

  it('getAccount throws an ApiError with the backend error code and message on failure', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ error: 'ACCOUNT_NOT_FOUND', message: 'Account not found: abc-123' }, 404));
    vi.stubGlobal('fetch', fetchMock);

    await expect(getAccount('abc-123')).rejects.toBeInstanceOf(ApiError);
    await expect(getAccount('abc-123')).rejects.toMatchObject({
      code: 'ACCOUNT_NOT_FOUND',
      message: 'Account not found: abc-123',
    });
  });

  it('deposit posts amount and currency to /accounts/{id}/deposits', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ accountId: 'abc-123', ownerName: 'Ada', balance: 150, currency: 'USD' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await deposit('abc-123', { amount: 50, currency: 'USD' });

    expect(result.balance).toBe(150);
    expect(fetchMock).toHaveBeenCalledWith(
      '/accounts/abc-123/deposits',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ amount: 50, currency: 'USD' }) }),
    );
  });
});
