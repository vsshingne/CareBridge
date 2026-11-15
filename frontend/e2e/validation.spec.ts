import { test, expect } from "@playwright/test";

test("registration validates before submitting and displays API errors", async ({
  page,
}) => {
  let submitted = 0;
  await page.route("**/api/v1/auth/register/patient", async (route) => {
    submitted++;
    await route.fulfill({
      status: 409,
      json: { message: "Email already registered" },
    });
  });
  await page.goto("/register/patient");
  await page.getByLabel("Full name", { exact: true }).fill("Test Patient");
  await page.getByLabel("Email address").fill("patient@example.test");
  await page.getByLabel("Password", { exact: true }).fill("short");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.locator(".field-error")).toBeVisible();
  expect(submitted).toBe(0);
  await page
    .getByLabel("Password", { exact: true })
    .fill("a-valid-password-123");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("alert")).toHaveText("Email already registered");
  expect(submitted).toBe(1);
});

test("anonymous protected route preserves return path", async ({ page }) => {
  await page.goto("/profile");
  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
});
