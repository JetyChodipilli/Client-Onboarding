import { expect, test, type Page, type Route } from "@playwright/test";

const success = (data: unknown) => ({ success: true, data, meta: {}, requestId: "11111111-1111-4111-8111-111111111111" });
const failure = (code: string, message: string) => ({ success: false, error: { code, message, fieldErrors: [] }, requestId: "22222222-2222-4222-8222-222222222222" });
const user = { id: "user", organizationId: "org", email: "admin@example.test", displayName: "Avery Admin", role: "Administrator", permissions: ["ORGANIZATION_READ", "USER_MANAGE", "ROLE_MANAGE", "AUDIT_READ"], sessionId: "session" };
const browserErrors = new WeakMap<Page, string[]>();
const corsHeaders = {
  "access-control-allow-origin": "http://localhost:3000",
  "access-control-allow-credentials": "true",
  "access-control-allow-methods": "GET,POST,PATCH,OPTIONS",
  "access-control-allow-headers": "Content-Type,X-XSRF-TOKEN",
};
const respond = (route: Route, json: unknown, status = 200) => route.fulfill({ status, json, headers: corsHeaders });

async function mockApi(page: Page, handler?: (route: Route, endpoint: string) => Promise<boolean>) {
  await page.route("**/api/v1/**", async (route) => {
    const endpoint = new URL(route.request().url()).pathname;
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers: corsHeaders });
    if (handler && await handler(route, endpoint)) return;
    if (endpoint === "/api/v1/auth/csrf") return respond(route, success({ headerName: "X-XSRF-TOKEN", parameterName: "_csrf", token: "csrf" }));
    if (endpoint === "/api/v1/auth/me") return respond(route, success(user));
    if (endpoint === "/api/v1/auth/login") return respond(route, success({ state: "AUTHENTICATED", recoveryCodes: [], user }));
    return respond(route, failure("RESOURCE_NOT_FOUND", "Requested resource was not found."), 404);
  });
}

test.describe("Phase 1 identity UI", () => {
  test.beforeEach(async ({ page }) => {
    const errors: string[] = [];
    browserErrors.set(page, errors);
    page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
    page.on("pageerror", (error) => errors.push(error.message));
  });

  test.afterEach(async ({ page }) => {
    expect(browserErrors.get(page)).toEqual([]);
  });

  test("sign-in happy path reaches the permission-aware workspace", async ({ page }) => {
    await mockApi(page);
    await page.goto("/login");
    await page.getByLabel("Work email").fill("admin@example.test");
    await page.getByLabel("Organization slug").fill("agency");
    await page.getByLabel("Password", { exact: true }).fill("CorrectHorse7Battery");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/\/app$/);
    await expect(page.getByRole("heading", { name: "Workspace security baseline" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false);
  });

  test("validation failure is inline, linked, and keyboard discoverable", async ({ page }) => {
    await mockApi(page);
    await page.goto("/login");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByText("Check the highlighted fields.")).toBeFocused();
    await expect(page.getByText("Enter a valid email address.")).toBeVisible();
  });

  test("API error retains the form and gives a recovery message", async ({ page }) => {
    await mockApi(page, async (route, endpoint) => {
      if (endpoint === "/api/v1/auth/login") { await respond(route, failure("INVALID_CREDENTIALS", "The organization or credentials are invalid."), 401); return true; }
      return false;
    });
    await page.goto("/login");
    await page.getByLabel("Work email").fill("admin@example.test");
    await page.getByLabel("Organization slug").fill("agency");
    await page.getByLabel("Password", { exact: true }).fill("WrongPassword7");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "credentials are invalid" })).toBeVisible();
    await expect(page.getByLabel("Work email")).toHaveValue("admin@example.test");
  });

  test("authorization failure and empty member state remain explicit", async ({ page }) => {
    await mockApi(page, async (route, endpoint) => {
      if (endpoint === "/api/v1/organization-members") { await respond(route, failure("PERMISSION_DENIED", "You do not have permission to perform this action."), 403); return true; }
      if (endpoint === "/api/v1/roles") { await respond(route, success([])); return true; }
      return false;
    });
    await page.goto("/app/settings/members");
    await expect(page.getByRole("alert").filter({ hasText: "do not have permission" })).toBeVisible();

    await page.unrouteAll();
    await mockApi(page, async (route, endpoint) => {
      if (endpoint === "/api/v1/organization-members" || endpoint === "/api/v1/roles") { await respond(route, success([])); return true; }
      return false;
    });
    await page.reload();
    await expect(page.getByRole("heading", { name: "No members yet" })).toBeVisible();
  });

  test("loading state is announced and mobile layout has no overflow", async ({ page }) => {
    await mockApi(page, async (route, endpoint) => {
      if (endpoint === "/api/v1/auth/me") { await new Promise((resolve) => setTimeout(resolve, 350)); await respond(route, success(user)); return true; }
      return false;
    });
    await page.goto("/app");
    await expect(page.getByText("Loading your secure workspace…")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Workspace security baseline" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false);
  });
});
