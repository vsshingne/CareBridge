import { test, expect, type Page } from "@playwright/test";

async function login(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/dashboard/);
}
test("public directory and mobile layout", async ({ page }) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /Your next step/ }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/landing-desktop.png",
    fullPage: true,
  });
  await page.goto("/doctors");
  await expect(page.locator(".doctor-card")).toHaveCount(3);
  await page
    .getByLabel("Specialization", { exact: true })
    .selectOption("Cardiology");
  await expect(page.locator(".doctor-card")).toHaveCount(1);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Dr. Ananya Rao" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: "test-results/directory-mobile.png",
    fullPage: true,
  });
});
test("patient books, restores session, reschedules and cancels through real API", async ({
  page,
}) => {
  const password = process.env.DEMO_PASSWORD;
  expect(password, "Run seed-demo.mjs with DEMO_PASSWORD first").toBeTruthy();
  await login(page, "patient@carebridge.example", password!);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Hello, Jamie." }),
  ).toBeVisible();
  await page.goto("/doctors");
  await page.getByRole("link", { name: "View availability" }).first().click();
  const tomorrow = new Date(Date.now() + 2 * 86400000).toLocaleDateString(
    "en-CA",
    { timeZone: "Asia/Kolkata" },
  );
  await page.getByLabel(/Appointment date/).fill(tomorrow);
  await expect(page.locator(".slot").first()).toBeVisible();
  await page.locator(".slot").first().click();
  const reason = `Browser verification ${Date.now()}`;
  await page.getByLabel("Reason for visit (optional)").fill(reason);
  await page.getByRole("button", { name: "Request appointment" }).click();
  await expect(page).toHaveURL(/appointments$/);
  const card = page.locator(".appointment-card").filter({ hasText: reason });
  await expect(card).toBeVisible();
  await card.getByRole("link", { name: "Reschedule" }).click();
  await page.getByLabel(/Appointment date/).fill(tomorrow);
  await expect(page.locator(".slot").first()).toBeVisible();
  await page.locator(".slot").first().click();
  await page.getByRole("button", { name: "Confirm new time" }).click();
  await expect(page).toHaveURL(/appointments$/);
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .locator(".appointment-card")
    .filter({ hasText: reason })
    .getByRole("button", { name: "Cancel appointment" })
    .click();
  await expect(
    page
      .locator(".appointment-card")
      .filter({ hasText: reason })
      .getByText("cancelled", { exact: true }),
  ).toBeVisible();
  await page.goto("/profile");
  await page.getByLabel("Emergency contact (optional)").fill("Demo contact");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByText("Profile saved.")).toBeVisible();
});
test("doctor edits availability and admin reviews verification", async ({
  page,
}) => {
  expect(process.env.DEMO_PASSWORD).toBeTruthy();
  await login(page, "ananya@carebridge.example", process.env.DEMO_PASSWORD!);
  await page.goto("/schedule");
  await page.getByRole("button", { name: "Edit", exact: true }).first().click();
  await page.getByRole("button", { name: "Save availability" }).click();
  await expect(page.getByText("Availability saved.")).toBeVisible();
  await page.getByRole("button", { name: "Sign out" }).click();
  await login(page, process.env.ADMIN_EMAIL!, process.env.ADMIN_PASSWORD!);
  await page.goto("/admin");
  await page.getByRole("button", { name: "Doctors", exact: true }).click();
  const row = page.getByRole("row").filter({ hasText: "Dr. Ananya Rao" });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "Remove verification" }).click();
  await expect(row.getByText("Pending review")).toBeVisible();
  await row.getByRole("button", { name: "Verify doctor" }).click();
  await expect(row.getByText("Verified", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Audit logs" }).click();
  await expect(page.getByRole("table")).toBeVisible();
});
