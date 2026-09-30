// Every page a signed-in person reaches, opened and checked against Axe. The suite runs
// at 360 px in the light theme and at 1440 px in the dark one, so a rule that only
// breaks in one of them still fails the build.
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, signIn, unique } from './support.js';

test.describe('workspace pages', () => {
  test('open, stay usable, and break no accessibility rule', async ({ page }) => {
    await registerAndSignIn(page);
    const project = await createProject(page);

    const pages = [
      ['overview', '/'],
      ['change inbox', `/changes?project=${project.id}`],
      ['releases', `/releases?project=${project.id}`],
      ['projects', '/projects'],
      ['sensitive paths', `/projects/${project.id}/sensitive-paths`],
      ['members', '/members'],
      ['audiences', '/audiences'],
      ['new audience', '/audiences/new'],
      ['categories', '/categories'],
      ['automation', '/automation'],
    ];

    for (const [name, path] of pages) {
      await page.goto(path);
      await expect(page.locator('main#page-content')).toBeVisible();
      // A toast answers something just done; a page opened plainly has nothing to say.
      await expect(page.locator('.toast-item'), `toasts on ${name}`).toHaveCount(0);
      await expectNoAccessibilityViolations(page, name);
    }
  });

  test('the public pages break no accessibility rule either', async ({ page }) => {
    for (const [name, path] of [['home', '/'], ['sign in', '/login'], ['register', '/register']]) {
      await page.goto(path);
      await expectNoAccessibilityViolations(page, name);
    }
  });

  test('an unknown address answers with a page, not a stack trace', async ({ page }) => {
    // Signed in first: to a stranger every unknown address is the sign-in page, which
    // says nothing about what does or does not exist.
    await registerAndSignIn(page);
    const response = await page.goto('/nope');

    expect(response.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: /back to the workspace|về không gian làm việc/i })).toBeVisible();
    await expect(page.locator('body')).not.toContainText('Exception');
    await expectNoAccessibilityViolations(page, 'not found');
  });

  test('a form sent after its session ended asks to sign in again', async ({ page, context }) => {
    await registerAndSignIn(page);
    await page.goto('/projects');
    await page.getByPlaceholder(/project name|tên project/i).fill(unique('Checkout'));

    await context.clearCookies();
    await page.getByRole('button', { name: /create project|tạo project/i }).click();

    await expect(page).toHaveURL(/\/login\?expired$/);
    await expect(page.getByRole('status')).toContainText(/session ended|phiên làm việc đã hết/i);
    await expect(page.locator('body')).not.toContainText(/another Organization|Organization khác/);
    await expectNoAccessibilityViolations(page, 'sign in after an expired session');
  });

  test('a form older than the sign-in beside it is called out of date', async ({ page, context }) => {
    const owner = await registerAndSignIn(page);
    await page.goto('/projects');
    await page.getByPlaceholder(/project name|tên project/i).fill(unique('Checkout'));

    // Signing in again in another tab starts a new session and a new form token.
    const other = await context.newPage();
    await signIn(other, owner.email, owner.password);
    await other.close();
    const response = page.waitForResponse((answer) => answer.request().method() === 'POST');
    await page.getByRole('button', { name: /create project|tạo project/i }).click();

    expect((await response).status()).toBe(403);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(/out of date|đã cũ/i);
    await expect(page.locator('body')).not.toContainText(/another Organization|Organization khác/);
    await expectNoAccessibilityViolations(page, 'out-of-date form');
  });

  test('the sidebar brand fits its row in every shipped language', async ({ page, context, baseURL }) => {
    await registerAndSignIn(page);
    for (const language of ['en', 'vi']) {
      await context.addCookies([{ name: 'releaseflow_lang', value: language, url: baseURL }]);
      await page.goto('/');
      const menu = page.locator('label[for="app-drawer"].icon-button');
      if (await menu.isVisible()) {
        await menu.click();
      }
      const brand = page.locator('#app-sidebar > a').first();
      await expect(brand).toBeVisible();

      // A tagline that wraps must grow the row downwards, never spill over its top edge.
      const fit = await brand.evaluate((row) => {
        const box = row.getBoundingClientRect();
        const text = row.querySelector('span').getBoundingClientRect();
        return { rowTop: box.top, rowBottom: box.bottom, textTop: text.top, textBottom: text.bottom };
      });
      expect(fit.textTop, `${language} tagline top`).toBeGreaterThanOrEqual(fit.rowTop);
      expect(fit.textBottom, `${language} tagline bottom`).toBeLessThanOrEqual(fit.rowBottom);
      expect(fit.rowBottom - fit.rowTop, `${language} brand row height`).toBeLessThanOrEqual(64);
    }
  });
});
