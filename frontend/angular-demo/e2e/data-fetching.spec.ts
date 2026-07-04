import { test, expect } from '@playwright/test';

test('fetches and renders items from the fake API', async ({ page }) => {
  await page.goto('/data-fetching');

  await expect(page.getByTestId('loading-spinner')).toBeVisible();
  await expect(page.getByTestId('items-list')).toBeVisible();
  await expect(page.getByTestId('items-list')).toContainText('Signals');
});
