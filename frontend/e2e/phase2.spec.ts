import { expect, test, type Page } from "@playwright/test";

const permissions = [
  "CLIENT_CREATE", "CLIENT_READ", "CLIENT_UPDATE",
  "SERVICE_READ", "SERVICE_MANAGE",
  "PROJECT_CREATE", "PROJECT_READ", "PROJECT_UPDATE",
  "USER_READ",
];
const user = {
  id: "00000000-0000-0000-0000-000000000101",
  email: "admin@example.com",
  displayName: "Asha Rao",
  organizationId: "00000000-0000-0000-0000-000000000201",
  organizationName: "Northstar Studio",
  organizationSlug: "northstar",
  permissions,
};
const auth = { accessToken: "mock.jwt.token", accessTokenExpiresAt: "2099-01-01T00:00:00Z", user, mfaRequired: false, mfaSetupRequired: false, challengeToken: null };
const client = { id: "c1", name: "Acme Technologies", status: "ACTIVE", archivedAt: null, createdAt: "2026-08-20T09:00:00Z", updatedAt: "2026-08-20T09:00:00Z", version: 0 };
const service = { id: "s1", code: "META_ADS", name: "Meta Ads Management", description: "Paid social delivery.", status: "ACTIVE", archivedAt: null, createdAt: "2026-08-20T09:00:00Z", updatedAt: "2026-08-20T09:00:00Z", version: 0 };
const project = { id: "p1", clientId: "c1", clientName: client.name, serviceId: "s1", serviceName: service.name, name: "Q4 Growth Campaign", description: "Launch campaign", status: "DRAFT", archivedAt: null, createdAt: "2026-08-20T09:00:00Z", updatedAt: "2026-08-20T09:00:00Z", version: 0 };
function envelope(data: unknown) { const count = Array.isArray(data) ? data.length : 0; return { success: true, data, meta: { page: 0, size: 25, totalElements: count, totalPages: count > 0 ? 1 : 0 }, requestId: "phase2-e2e" }; }
function failure(code: string, message: string) { return { success: false, error: { code, message, fieldErrors: [] }, requestId: "phase2-e2e" }; }
async function authRoute(page: Page, granted = permissions) {
  await page.route("**/api/v1/auth/refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope({ ...auth, user: { ...user, permissions: granted } })) }));
}

test("client list is tenant-workspace clear, responsive, and links to details", async ({ page }) => {
  const consoleErrors: string[] = []; page.on("console", msg => { if (msg.type() === "error") consoleErrors.push(msg.text()); });
  await authRoute(page);
  await page.route("**/api/v1/clients?**", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope([client])) }));
  await page.goto("/app/clients");
  await expect(page.getByRole("heading", { name: "Clients" })).toBeVisible();
  await expect(page.getByText("Acme Technologies")).toBeVisible();
  await expect(page.getByRole("link", { name: /View client/ })).toHaveAttribute("href", "/app/clients/c1");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  expect(consoleErrors).toEqual([]);
});

test("client screen has explicit empty and API error states", async ({ page }) => {
  await authRoute(page);
  let mode: "empty" | "error" = "empty";
  await page.route("**/api/v1/clients?**", route => mode === "empty"
    ? route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope([])) })
    : route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify(failure("SERVICE_UNAVAILABLE", "Clients are temporarily unavailable.")) }));
  await page.goto("/app/clients");
  await expect(page.getByText("No clients yet")).toBeVisible();
  mode = "error";
  await page.getByRole("button", { name: "Refresh clients" }).click();
  await expect(page.getByRole("alert")).toContainText("temporarily unavailable");
});

test("services show production-safe archive language and active records", async ({ page }) => {
  await authRoute(page);
  await page.route("**/api/v1/services?**", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope([service])) }));
  await page.goto("/app/services");
  await expect(page.getByRole("heading", { name: "Services" })).toBeVisible();
  await expect(page.getByText("META_ADS")).toBeVisible();
  await expect(page.getByText("Meta Ads Management")).toBeVisible();
});

test("project list exposes lifecycle without prematurely exposing activation", async ({ page }) => {
  await authRoute(page);
  await page.route("**/api/v1/projects?**", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope([project])) }));
  await page.goto("/app/projects");
  await expect(page.getByRole("heading", { name: "Projects" })).toBeVisible();
  await expect(page.getByText("Q4 Growth Campaign")).toBeVisible();
  await expect(page.getByText("DRAFT")).toBeVisible();
  await expect(page.getByRole("button", { name: /activate/i })).toHaveCount(0);
});

test("project detail separates team and activity and explains phase boundary", async ({ page }) => {
  await authRoute(page);
  await page.route("**/api/v1/projects/p1", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope(project)) }));
  await page.route("**/api/v1/projects/p1/members", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope([])) }));
  await page.route("**/api/v1/projects/p1/activity?**", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope([{ id: "a1", actorUserId: user.id, action: "PROJECT_CREATED", entityType: "PROJECT", entityId: "p1", summary: "Project created", metadata: {}, occurredAt: "2026-08-20T09:00:00Z" }])) }));
  await page.goto("/app/projects/p1");
  await expect(page.getByText("Readiness does not activate a project automatically.")).toBeVisible();
  await expect(page.getByText("Project team")).toBeVisible();
  await expect(page.getByText("Project created")).toBeVisible();
});

test("direct Phase 2 routes still deny missing backend-style permissions in the UI", async ({ page }) => {
  await authRoute(page, ["ORG_READ"]);
  await page.goto("/app/clients");
  await expect(page.getByRole("heading", { name: "Permission required" })).toBeVisible();
  await page.goto("/app/projects");
  await expect(page.getByRole("heading", { name: "Permission required" })).toBeVisible();
});
