import { beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { loadAccountIds, useAccountRegistry } from './AccountRegistry';

describe('AccountRegistry', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('starts empty when nothing is stored', () => {
    const { result } = renderHook(() => useAccountRegistry());
    expect(result.current.accountIds).toEqual([]);
  });

  it('adds an account id and persists it to localStorage', () => {
    const { result } = renderHook(() => useAccountRegistry());

    act(() => {
      result.current.addAccount('abc-123');
    });

    expect(result.current.accountIds).toEqual(['abc-123']);
    expect(loadAccountIds()).toEqual(['abc-123']);
  });

  it('does not add the same account id twice', () => {
    const { result } = renderHook(() => useAccountRegistry());

    act(() => {
      result.current.addAccount('abc-123');
      result.current.addAccount('abc-123');
    });

    expect(result.current.accountIds).toEqual(['abc-123']);
  });
});
