import { expect, test, type Page, type Route } from "@playwright/test";

const success = (data: unknown) => ({ success: true, data, meta: {}, requestId: "44444444-4444-4444-8444-444444444444" });
const failure = (code: string, message: string) => ({ success: false, error: { code, message, fieldErrors: [] }, requestId: "44444444-4444-4444-8444-555555555555" });
const cors = { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true", "access-control-allow-methods": "GET,POST,OPTIONS", "access-control-allow-headers": "Content-Type,X-XSRF-TOKEN" };
const respond = (route: Route, json: unknown, status = 200) => route.fulfill({ status, json, headers: cors });
const user = { id: "client-user", organizationId: "org", organizationName: "Northstar Studio", organizationSlug: "northstar", email: "ada@client.test", displayName: "Ada Client", role: "CLIENT_ADMIN", permissions: ["CLIENT_PORTAL_READ", "CLIENT_PORTAL_STEP_UPDATE"], sessionId: "session" };
const invitation = { status: "VALID", organizationName: "Northstar Studio", organizationSlug: "northstar", clientName: "Acme", projectName: "Acme Launch", contactName: "Ada Client", email: "ada@client.test", expiresAt: "2026-10-01T12:00:00Z" };
const dashboard = { projectId: "project-1", projectName: "Acme Launch", projectStatus: "ONBOARDING", clientName: "Acme", onboardingId: "onboarding-1", onboardingStatus: "IN_PROGRESS", currentStatus: "ACTION_REQUIRED", progress: 25, waitingFor: "YOU", availableHelp: "Email owner@northstar.test for help.", helpEmail: "owner@northstar.test", nextAction: { id: "step-1", name: "Read the welcome guide", description: "Review the launch plan before continuing.", type: "WELCOME", status: "AVAILABLE", required: true, blocking: true, deadline: "2026-10-02T12:00:00Z", waitingFor: "YOUR_ACTION", actionable: true, version: 0 }, steps: [{ id: "step-1", name: "Read the welcome guide", description: "Review the launch plan before continuing.", type: "WELCOME", status: "AVAILABLE", required: true, blocking: true, deadline: "2026-10-02T12:00:00Z", waitingFor: "YOUR_ACTION", actionable: true, version: 0 }, { id: "step-2", name: "Platform access", description: "Waiting for account preparation.", type: "PLATFORM_ACCESS", status: "LOCKED", required: true, blocking: true, waitingFor: "OUR_TEAM", blockingReason: "Your team is completing a prerequisite.", actionable: false, version: 0 }] };

async function mock(page: Page, state: "valid" | "expired" = "valid") {
  await page.route("**/api/v1/**", async (route) => {
    const endpoint = new URL(route.request().url()).pathname;
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors });
    if (endpoint === "/api/v1/auth/csrf") return respond(route, success({ headerName: "X-XSRF-TOKEN", token: "csrf" }));
    if (endpoint === "/api/v1/auth/me") return respond(route, success(user));
    if (endpoint === "/api/v1/client-invitations/inspect") return respond(route, success({ ...invitation, status: state === "expired" ? "EXPIRED" : "VALID" }));
    if (endpoint === "/api/v1/client-invitations/accept") return respond(route, success({ message: "Invitation accepted. Sign in to continue.", organizationSlug: "northstar", projectName: "Acme Launch" }));
    if (endpoint === "/api/v1/client-auth/login") return respond(route, success(user));
    if (endpoint === "/api/v1/client-portal/projects") return respond(route, success([{ id: "project-1", name: "Acme Launch", projectStatus: "ONBOARDING", clientName: "Acme", onboardingStatus: "IN_PROGRESS", progress: 25, pendingRequirements: 2 }]));
    if (endpoint === "/api/v1/client-portal/projects/project-1") return respond(route, success(dashboard));
    if (endpoint.includes("/steps/step-1/transition")) return respond(route, success({ ...dashboard, progress: 50, nextAction: { ...dashboard.nextAction, status: "IN_PROGRESS", version: 1 }, steps: [{ ...dashboard.steps[0], status: "IN_PROGRESS", version: 1 }, dashboard.steps[1]] }));
    return respond(route, failure("RESOURCE_NOT_FOUND", "Requested resource was not found."), 404);
  });
}

test.describe("Phase 4 client invitation and portal", () => {
  test("accepts a verified invitation and routes to client sign in", async ({ page }) => {
    await mock(page); await page.goto("/client/accept-invitation?token=secure-token");
    await expect(page.getByRole("heading", { name: "Join Acme Launch" })).toBeVisible();
    await page.getByLabel("Create or confirm your password").fill("ClientPortal7Password");
    await page.getByLabel("Confirm password").fill("ClientPortal7Password");
    await page.getByRole("button", { name: "Activate secure portal" }).click();
    await expect(page.getByRole("heading", { name: "Your portal is ready" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Continue to client sign in" })).toHaveAttribute("href", "/client/login?organization=northstar");
  });

  test("explains an expired invitation without exposing an activation form", async ({ page }) => {
    await mock(page, "expired"); await page.goto("/client/accept-invitation?token=expired-token");
    await expect(page.getByRole("heading", { name: "This invitation is unavailable" })).toBeVisible();
    await expect(page.getByText(/Ask your project team to resend it/)).toBeVisible();
    await expect(page.getByRole("button", { name: "Activate secure portal" })).toHaveCount(0);
  });

  test("signs a client into the separate portal scope", async ({ page }) => {
    await mock(page); await page.goto("/client/login?organization=northstar");
    await page.getByLabel("Work email").fill("ada@client.test");
    await page.getByLabel("Password", { exact: true }).fill("ClientPortal7Password");
    await page.getByRole("button", { name: "Open client portal" }).click();
    await expect(page).toHaveURL(/\/portal$/);
  });

  test("shows status progress next action blocker deadline and help", async ({ page }, testInfo) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await mock(page); await page.goto("/portal/projects/project-1");
    await expect(page.getByRole("heading", { name: "Acme Launch" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Your action", exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Waiting for our team", exact: true })).toBeVisible();
    await expect(page.getByRole("progressbar", { name: "Onboarding progress" })).toHaveAttribute("aria-valuenow", "25");
    await expect(page.getByText("Required deadline")).toBeVisible();
    await expect(page.getByText("Available help")).toBeVisible();
    await expect(page.getByText("Internal risk review")).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false);
    await page.screenshot({ path: testInfo.outputPath("client-dashboard.png"), fullPage: true });
    expect(errors).toEqual([]);
  });

  test("starts an actionable informational step", async ({ page }) => {
    await mock(page); await page.goto("/portal/projects/project-1");
    await page.getByRole("button", { name: "Start this step" }).click();
    await expect(page.getByRole("progressbar", { name: "Onboarding progress" })).toHaveAttribute("aria-valuenow", "50");
    await expect(page.getByRole("button", { name: "Mark complete" })).toBeVisible();
  });

  test("submits review-required work for team review", async ({ page }) => {
    await mock(page);
    const step = { ...dashboard.nextAction, status: "IN_PROGRESS", requiresReview: true, version: 1 };
    await page.route("**/api/v1/client-portal/projects/project-1", (route) => respond(route, success({ ...dashboard, nextAction: step, steps: [step] })));
    await page.route("**/steps/step-1/transition", (route) => {
      expect(route.request().postDataJSON().targetStatus).toBe("SUBMITTED");
      return respond(route, success({ ...dashboard, nextAction: null, waitingFor: "OUR_TEAM", steps: [{ ...step, status: "SUBMITTED", actionable: false, waitingFor: "OUR_TEAM" }] }));
    });
    await page.goto("/portal/projects/project-1");
    await page.getByRole("button", { name: "Submit for review" }).click();
    await expect(page.getByText("SUBMITTED", { exact: true })).toBeVisible();
    await expect(page.getByText("Internal review or prerequisite")).toBeVisible();
  });

  test("shows empty projects and recovers from a denied project", async ({ page }) => {
    await mock(page);
    await page.route("**/api/v1/client-portal/projects?*", (route) => respond(route, success([])));
    await page.goto("/portal");
    await expect(page.getByRole("heading", { name: "No projects are assigned" })).toBeVisible();
    await page.route("**/api/v1/client-portal/projects/private", (route) => respond(route, failure("RESOURCE_NOT_FOUND", "Requested resource was not found."), 404));
    await page.goto("/portal/projects/private");
    await expect(page.getByText("Requested resource was not found.")).toBeVisible();
    await expect(page.getByRole("link", { name: "Projects", exact: true })).toBeVisible();
  });

  test("keeps the client session screen on logout failure and allows retry", async ({ page }) => {
    await mock(page);
    let attempts = 0;
    await page.route("**/api/v1/auth/logout", (route) => {
      if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors });
      attempts += 1;
      return attempts === 1
        ? respond(route, failure("DEPENDENCY_UNAVAILABLE", "Please try again."), 503)
        : respond(route, success({ message: "Signed out" }));
    });
    await page.goto("/portal");
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page.getByRole("alert")).toContainText("Your session may still be active");
    await expect(page).toHaveURL(/\/portal$/);
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page).toHaveURL(/\/client\/login$/);
    expect(attempts).toBe(2);
  });

  test("keeps a clear loading state and shows an API failure", async ({ page }) => {
    await mock(page);
    let release!: () => void;
    const waiting = new Promise<void>((resolve) => { release = resolve; });
    await page.route("**/api/v1/client-portal/projects/project-1", async (route) => {
      await waiting;
      return respond(route, failure("DEPENDENCY_UNAVAILABLE", "The service is temporarily unavailable."), 503);
    });
    await page.goto("/portal/projects/project-1");
    await expect(page.locator('[aria-busy="true"]')).toBeVisible();
    release();
    await expect(page.getByText("The service is temporarily unavailable.")).toBeVisible();
  });
});
