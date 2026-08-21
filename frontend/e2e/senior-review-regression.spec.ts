import { expect, test, type Page } from "@playwright/test";

function envelope(data: unknown) { return { success: true, data, meta: {}, requestId: "senior-review-e2e" }; }

const internalUser = {
  id: "00000000-0000-0000-0000-000000000111",
  email: "admin@example.com",
  displayName: "Admin User",
  organizationId: "00000000-0000-0000-0000-000000000222",
  organizationName: "Northstar Studio",
  organizationSlug: "northstar",
  permissions: ["ORG_READ"],
};

async function mockInternalAuth(page: Page) {
  await page.route("**/api/v1/auth/refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope({ accessToken: "mock.jwt", accessTokenExpiresAt: "2099-01-01T00:00:00Z", user: internalUser, mfaRequired: false, mfaSetupRequired: false, challengeToken: null })) }));
}

async function mockClientAuth(page: Page) {
  const user = { ...internalUser, permissions: ["CLIENT_PORTAL"] };
  await page.route("**/api/v1/client-auth/refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope({ accessToken: "mock.client.jwt", accessTokenExpiresAt: "2099-01-01T00:00:00Z", user, mfaRequired: false, mfaSetupRequired: false, challengeToken: null })) }));
}

test("integration readiness never needs secret material in its browser response", async ({ page }) => {
  await mockInternalAuth(page);
  await page.route("**/api/v1/integrations", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(envelope({ integrations: [
    { key: "payments", name: "Payments", category: "Financial", status: "DEVELOPMENT", provider: "SIGNED_SANDBOX", enabled: true, productionReady: false, message: "A signed sandbox adapter is active for local/integration testing only." },
    { key: "malware-scan", name: "Malware scanning", category: "Security", status: "READY", provider: null, enabled: true, productionReady: true, message: "Uploaded assets must pass malware scanning before normal availability." },
  ] })) }));
  await page.goto("/app/integrations");
  await expect(page.getByRole("heading", { name: "Integrations" })).toBeVisible();
  await expect(page.getByText("SIGNED_SANDBOX")).toBeVisible();
  await expect(page.getByText(/Secret values are intentionally never returned/i)).toBeVisible();
});

test("client profile remains usable on mobile and exposes security recovery", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockClientAuth(page);
  await page.goto("/portal/profile");
  await expect(page.getByRole("heading", { name: "Your secure workspace identity" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Reset password/ })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 2)).toBe(true);
});
