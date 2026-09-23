// The path a release actually takes, through the pages rather than the API: a Project,
// a source, a draft release, and the switcher that remembers which Project was open.
import { test, expect } from '@playwright/test';
import { createProject, expectNoAccessibilityViolations, registerAndSignIn, unique } from './support.js';

test('a project is created, a source connected, and a release drafted', async ({ page }) => {
  await registerAndSignIn(page);

  // The overview offers exactly one next step, and it is the first unfinished one.
  await expect(page.locator('#next-step')).toContainText(/create a project|tạo một project/i);

  const project = await createProject(page);
  await page.goto('/');
  await expect(page.locator('#next-step')).toContainText(/connect a source|kết nối một nguồn/i);

  await page.goto('/projects');
  await page.getByLabel(/repository owner|chủ repository/i).fill('acme');
  await page.getByLabel(/repository name|tên repository/i).fill(unique('app'));
  await page.getByRole('button', { name: /generate webhook credentials|sinh thông tin webhook/i }).click();
  await expect(page.getByRole('heading', { name: /save these webhook credentials|lưu thông tin webhook/i }))
    .toBeVisible();
  await expectNoAccessibilityViolations(page, 'source connected');

  // With a source but no changes, the overview waits rather than inventing work.
  await page.goto('/');
  await expect(page.locator('#next-step')).toContainText(/waiting for the first change|chờ thay đổi đầu tiên/i);

  const version = '1.0.0';
  await page.goto(`/releases?project=${project.id}`);
  await page.getByLabel(/^version|phiên bản/i).fill(version);
  await page.getByRole('button', { name: /create release|tạo release/i }).click();
  // The version appears twice on the release page: as its heading and above the note
  // preview. The heading is the one that says the release was created.
  await expect(page.locator('h1.page-heading')).toHaveText(version);
  await expectNoAccessibilityViolations(page, 'release page');
});

test('the header remembers which project was open', async ({ page }) => {
  await registerAndSignIn(page);
  const first = await createProject(page, unique('First'));
  const second = await createProject(page, unique('Second'));

  // Switching from the header, which is a plain GET form.
  await page.goto(`/changes?project=${second.id}`);
  await expect(page.locator('#project-switcher')).toHaveValue(second.id);

  // A URL that names no Project opens on the one last looked at, server-side: the
  // selected option is right in the very first response, with no script involved.
  await page.goto('/changes');
  await expect(page.locator('#project-switcher')).toHaveValue(second.id);

  await page.goto(`/releases?project=${first.id}`);
  await page.goto('/releases');
  await expect(page.locator('#project-switcher')).toHaveValue(first.id);
});
