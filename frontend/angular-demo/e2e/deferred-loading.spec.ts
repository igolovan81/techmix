import { test, expect } from '@playwright/test';

test('defer block loads the heavy widget once scrolled into view', async ({ page }) => {
  await page.goto('/deferred-loading');

  await expect(page.getByTestId('defer-placeholder')).toBeVisible();

  await page.getByTestId('defer-placeholder').scrollIntoViewIfNeeded();
  await page.locator('mat-sidenav-content').evaluate((el) => el.scrollTo(0, el.scrollHeight));

  await expect(page.getByTestId('heavy-widget')).toBeVisible();
});
