// Shared steps. Every test brings its own Organization, so one suite run never depends
// on what another left behind and the tests can be read in any order.
import { expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const PASSWORD = 'a-secure-e2e-password';

/** A name nothing else in the database will collide with. */
export function unique(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/** Registers a new Organization through the form and signs its administrator in. */
export async function registerAndSignIn(page) {
  const email = `${unique('owner')}@example.test`;
  await page.goto('/register');
  await page.getByLabel(/organization name|tên organization/i).fill(unique('Acme'));
  await page.getByLabel(/^your name|tên của bạn/i).fill('Owner');
  await page.getByLabel(/^email/i).fill(email);
  await page.getByLabel(/^password|mật khẩu/i).fill(PASSWORD);
  await page.getByRole('button', { name: /create organization|tạo organization/i }).click();

  await expect(page).toHaveURL(/\/login/);
  await signIn(page, email);
  return { email, password: PASSWORD };
}

export async function signIn(page, email, password = PASSWORD) {
  await page.goto('/login');
  await page.getByLabel(/^email/i).fill(email);
  await page.getByLabel(/^password|mật khẩu/i).fill(password);
  await page.getByRole('button', { name: /^sign in|đăng nhập$/i }).click();
  await expect(page).toHaveURL(/\/$/);
}

/** Creates a Project and answers its id, read from the card the page renders. */
export async function createProject(page, name = unique('Checkout')) {
  await page.goto('/projects');
  await page.getByPlaceholder(/project name|tên project/i).fill(name);
  await page.getByRole('button', { name: /create project|tạo project/i }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
  const card = page.locator('article[id^="project-"]', { hasText: name });
  const id = (await card.first().getAttribute('id')).replace('project-', '');
  return { id, name };
}

/**
 * Fails on any accessibility violation the listed WCAG levels define. The tags are the
 * ones the reference workspace was held to; everything they cover is a rule a browser
 * or a screen reader can check without knowing what the page means.
 */
export async function expectNoAccessibilityViolations(page, context) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();

  const described = results.violations.map((violation) => ({
    id: violation.id,
    impact: violation.impact,
    help: violation.help,
    nodes: violation.nodes.map((node) => node.target.join(' ')),
  }));
  expect(described, `accessibility violations on ${context}`).toEqual([]);
}
