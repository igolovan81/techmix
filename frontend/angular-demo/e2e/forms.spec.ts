import { test, expect } from '@playwright/test';

test('reactive form validates and submits', async ({ page }) => {
  await page.goto('/forms');
  const submit = page.getByTestId('submit-button');
  await expect(submit).toBeDisabled();

  await page.getByTestId('email-input').fill('not-an-email');
  await page.getByTestId('age-input').fill('16');
  await page.getByTestId('age-input').blur();
  await expect(page.getByTestId('age-error')).toBeVisible();

  await page.getByTestId('email-input').fill('demo@example.com');
  await page.getByTestId('age-input').fill('21');
  await expect(submit).toBeEnabled();

  await submit.click();
  await expect(page.getByTestId('submit-success')).toContainText('demo@example.com');
});
