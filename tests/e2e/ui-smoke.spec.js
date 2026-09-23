// Every page a signed-in person reaches, opened and checked against Axe. The suite runs
// at 360 px in the light theme and at 1440 px in the dark one, so a rule that only
// breaks in one of them still fails the build.
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn } from './support.js';

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
});
