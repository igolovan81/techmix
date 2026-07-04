import { test, expect } from '@playwright/test';

const NAV_ITEMS = [
  { path: 'signals', label: 'Signals' },
  { path: 'component-communication', label: 'Component Communication' },
  { path: 'forms', label: 'Forms' },
  { path: 'data-fetching', label: 'Data Fetching' },
  { path: 'deferred-loading', label: 'Deferred Loading' },
  { path: 'routing', label: 'Routing' },
  { path: 'pipes', label: 'Pipes' },
  { path: 'directives', label: 'Directives' },
  { path: 'animations', label: 'Animations' },
];

test('sidenav links navigate to every feature route', async ({ page }) => {
  await page.goto('/');

  for (const item of NAV_ITEMS) {
    await page.getByRole('link', { name: item.label }).click();
    await expect(page).toHaveURL(new RegExp(`/${item.path}$`));
  }
});
