// An administrator invites somebody, and the invitee joins through the link. The link is
// shown once, so the test reads it from the page it is shown on.
import { test, expect } from '@playwright/test';
import { expectNoAccessibilityViolations, registerAndSignIn, signIn, unique } from './support.js';

test('an administrator invites a member, who joins through the link', async ({ page, browser }) => {
  await registerAndSignIn(page);

  const invitee = `${unique('teammate')}@example.test`;
  await page.goto('/members');
  await expectNoAccessibilityViolations(page, 'members');
  await page.getByRole('textbox').first().fill(invitee);
  await page.getByRole('button', { name: /create invitation|tạo lời mời/i }).click();

  await expect(page.getByRole('heading', { name: /send this link|gửi liên kết này/i })).toBeVisible();
  const link = await page.getByRole('textbox').first().inputValue();
  expect(link).toContain('/accept-invite');
  await expectNoAccessibilityViolations(page, 'invitation issued');

  await page.getByRole('link', { name: /i have sent the link|tôi đã gửi liên kết/i }).click();

  // The invitee is somebody else, in their own browser. The token rides in the URL
  // fragment, which no browser sends, so the page's own script has to move it into the
  // form before the server ever sees it.
  const inviteeContext = await browser.newContext();
  const inviteePage = await inviteeContext.newPage();
  await inviteePage.goto(link);
  await expect(inviteePage.getByRole('heading', { name: /join /i })).toBeVisible();
  await expectNoAccessibilityViolations(inviteePage, 'accept invitation');

  const password = 'a-secure-e2e-password';
  await inviteePage.getByLabel(/^your name|tên của bạn/i).fill('Teammate');
  await inviteePage.getByLabel(/^password|mật khẩu/i).fill(password);
  await inviteePage.getByRole('button', { name: /join organization|tham gia organization/i }).click();

  await expect(inviteePage).toHaveURL(/\/login/);
  await signIn(inviteePage, invitee, password);
  await expect(inviteePage.locator('main#page-content')).toBeVisible();

  // A member sees the workspace but not the administrator-only navigation.
  await expect(inviteePage.getByRole('link', { name: /^members|thành viên$/i })).toHaveCount(0);

  // And is told a page is not theirs rather than shown it.
  const forbidden = await inviteePage.goto('/automation');
  expect(forbidden.status()).toBe(403);
  await expect(inviteePage.getByRole('heading', { level: 1 })).toBeVisible();
  await expectNoAccessibilityViolations(inviteePage, 'forbidden');

  await inviteeContext.close();
});

test('an unusable invitation link says so once, and says nothing else', async ({ page }) => {
  await page.goto('/accept-invite#token=not-a-real-token');

  await expect(page.getByRole('alert')).toContainText(/invalid or has expired|không hợp lệ hoặc đã hết hạn/i);
  await expectNoAccessibilityViolations(page, 'unusable invitation');
});
