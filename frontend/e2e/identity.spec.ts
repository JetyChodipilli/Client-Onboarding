import { expect, test, type Page } from "@playwright/test";

const user = {
  id: "00000000-0000-0000-0000-000000000101",
  email: "admin@example.com",
  displayName: "Asha Rao",
  organizationId: "00000000-0000-0000-0000-000000000201",
  organizationName: "Northstar Studio",
  organizationSlug: "northstar",
  permissions: ["ORG_READ", "ORG_UPDATE", "USER_READ", "USER_MANAGE", "ROLE_READ", "ROLE_MANAGE", "AUDIT_READ"],
};
const auth = { accessToken: "mock.jwt.token", accessTokenExpiresAt: "2099-01-01T00:00:00Z", user, mfaRequired: false, mfaSetupRequired: false, challengeToken: null };
function envelope(data: unknown) { return { success: true, data, meta: {}, requestId: "e2e-request" }; }
function failure(code: string, message: string) { return { success: false, error: { code, message, fieldErrors: [] }, requestId: "e2e-request" }; }

async function mockAnonymous(page: Page) {
  await page.route("**/api/v1/auth/refresh", route => route.fulfill({ status: 401, contentType: "application/json", body: JSON.stringify(failure("UNAUTHORIZED", "Authentication is required.")) }));
}
async function mockAuthenticated(page: Page, permissions = user.permissions) {
  const current = { ...user, permissions };
  await page.route("**/api/v1/auth/refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope({ ...auth, user: current })) }));
}

test("login happy path is clear, responsive, and console-clean", async ({ page }) => {
  const errors: string[] = []; page.on("console", m => { if (m.type() === "error") errors.push(m.text()); });
  await mockAnonymous(page);
  await page.route("**/api/v1/auth/login", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope(auth)) }));
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await page.getByLabel("Workspace").fill("northstar");
  await page.getByLabel("Work email").fill("admin@example.com");
  await page.getByLabel("Password").fill("Velvet-River-84-Comet");
  await page.getByRole("button", { name: /Sign in securely/ }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByText("Northstar Studio").first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  expect(errors).toEqual([]);
});

test("login validation/API failure explains recovery without leaking details", async ({ page }) => {
  await mockAnonymous(page);
  await page.route("**/api/v1/auth/login", route => route.fulfill({ status: 401, contentType: "application/json", body: JSON.stringify(failure("INVALID_CREDENTIALS", "Email, workspace, or password is incorrect.")) }));
  await page.goto("/login");
  await page.getByLabel("Workspace").fill("northstar"); await page.getByLabel("Work email").fill("admin@example.com"); await page.getByLabel("Password").fill("wrong-password");
  await page.getByRole("button", { name: /Sign in securely/ }).click();
  await expect(page.getByRole("alert")).toContainText("Email, workspace, or password is incorrect");
});

test("permission-aware navigation and direct-route denial are explicit", async ({ page }) => {
  await mockAuthenticated(page, ["ORG_READ"]);
  await page.goto("/app");
  await expect(page.getByRole("link", { name: "People" })).toHaveCount(0);
  await page.goto("/app/users");
  await expect(page.getByRole("heading", { name: "Permission required" })).toBeVisible();
});

test("people screen covers populated, empty, and API error states", async ({ page }) => {
  await mockAuthenticated(page);
  let mode: "populated" | "empty" | "error" = "populated";
  await page.route("**/api/v1/roles", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope([{ id: "r1", code: "ACCOUNT_MANAGER", name: "Account Manager", description: null, systemRole: false, permissions: ["USER_READ"], version: 0 }])) }));
  await page.route("**/api/v1/organization-users?**", route => {
    if (mode === "error") return route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify(failure("SERVICE_UNAVAILABLE", "People are temporarily unavailable.")) });
    const data = mode === "empty" ? [] : [{ membershipId: "m1", userId: "u1", email: "maya@example.com", displayName: "Maya Singh", status: "ACTIVE", roles: [{ id: "r1", code: "ACCOUNT_MANAGER", name: "Account Manager" }], version: 0 }];
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope(data)) });
  });
  await page.goto("/app/users"); await expect(page.getByText("Maya Singh")).toBeVisible();
  mode = "empty"; await page.getByRole("button", { name: "Refresh people" }).click(); await expect(page.getByText("No members yet")).toBeVisible();
  mode = "error"; await page.getByRole("button", { name: "Refresh people" }).click(); await expect(page.getByRole("alert")).toContainText("temporarily unavailable");
});

test("MFA challenge keeps the security token in component memory and prompts for one-time code", async ({ page }) => {
  await mockAnonymous(page);
  await page.route("**/api/v1/auth/login", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope({ accessToken: null, accessTokenExpiresAt: null, user: null, mfaRequired: true, mfaSetupRequired: false, challengeToken: "opaque-challenge" })) }));
  await page.goto("/login"); await page.getByLabel("Workspace").fill("northstar"); await page.getByLabel("Work email").fill("admin@example.com"); await page.getByLabel("Password").fill("Velvet-River-84-Comet"); await page.getByRole("button", { name: /Sign in securely/ }).click();
  await expect(page.getByLabel("Verification code")).toBeVisible();
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
});
