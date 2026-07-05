import { test, expect } from '@playwright/test';

test('declarative binding and imperative creation both render app-star-rating widgets', async ({ page }) => {
  await page.goto('/custom-elements');

  const declarative = page.getByTestId('declarative-rating');
  await expect(declarative.getByTestId('star').nth(0)).toHaveText('★');
  await expect(declarative.getByTestId('star').nth(1)).toHaveText('★');
  await expect(declarative.getByTestId('star').nth(2)).toHaveText('☆');

  await page.getByTestId('increment-rating').click();
  await expect(declarative.getByTestId('star').nth(2)).toHaveText('★');
  await expect(declarative.getByTestId('star').nth(3)).toHaveText('☆');

  await page.getByTestId('create-imperatively').click();
  const imperative = page.getByTestId('imperative-rating');
  await expect(imperative.getByTestId('star').nth(0)).toHaveText('★');
  await expect(imperative.getByTestId('star').nth(2)).toHaveText('★');
  await expect(imperative.getByTestId('star').nth(3)).toHaveText('☆');
});
