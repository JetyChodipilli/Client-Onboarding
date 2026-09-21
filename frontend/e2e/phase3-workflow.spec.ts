import { expect, test, type Page, type Route } from "@playwright/test";

const success = (data: unknown) => ({ success: true, data, meta: {}, requestId: "11111111-1111-4111-8111-111111111111" });
const failure = (code: string, message: string) => ({ success: false, error: { code, message, fieldErrors: [] }, requestId: "22222222-2222-4222-8222-222222222222" });
const user = { id: "user", organizationId: "org", email: "admin@example.test", displayName: "Avery Admin", role: "Administrator", permissions: ["CLIENT_READ", "PROJECT_READ", "PROJECT_CREATE", "SERVICE_MANAGE", "WORKFLOW_READ", "WORKFLOW_MANAGE", "ONBOARDING_START", "ONBOARDING_REVIEW"], sessionId: "session" };
const template = { id: "template-1", name: "Meta Ads Onboarding", description: "A validated launch workflow", status: "ACTIVE", serviceId: "service-1", version: 0 };
const draft = { id: "version-draft", templateId: template.id, versionNumber: 2, status: "DRAFT", version: 1 };
const published = { id: "version-published", templateId: template.id, versionNumber: 1, status: "PUBLISHED", version: 2 };
const firstStep = { id: "step-1", stepKey: "BRIEF", name: "Collect brief", stepType: "FORM", displayOrder: 0, required: true, blocking: true, clientVisible: true, requiresReview: true, dependencyMode: "NONE", allowSkip: false, allowReopen: true, configuration: {}, dependencyStepIds: [], version: 0 };
const secondStep = { id: "step-2", stepKey: "ACCESS", name: "Verify platform access", stepType: "PLATFORM_ACCESS", displayOrder: 1, required: true, blocking: true, clientVisible: true, requiresReview: true, dependencyMode: "ALL", allowSkip: false, allowReopen: true, configuration: {}, dependencyStepIds: ["step-1"], version: 0 };
const corsHeaders = { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true", "access-control-allow-methods": "GET,POST,PUT,PATCH,OPTIONS", "access-control-allow-headers": "Content-Type,X-XSRF-TOKEN,Idempotency-Key" };
const respond = (route: Route, json: unknown, status = 200) => route.fulfill({ status, json, headers: corsHeaders });

async function mockApi(page: Page, options: { emptyTemplates?: boolean; publishedOnly?: boolean; withOnboarding?: boolean } = {}) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const endpoint = url.pathname;
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers: corsHeaders });
    if (endpoint === "/api/v1/auth/me") return respond(route, success(user));
    if (endpoint === "/api/v1/auth/csrf") return respond(route, success({ headerName: "X-XSRF-TOKEN", token: "csrf" }));
    if (endpoint === "/api/v1/services") return respond(route, success([{ id: "service-1", code: "META_ADS", name: "Meta Ads", status: "ACTIVE", version: 0 }]));
    if (endpoint === "/api/v1/workflow-templates") return respond(route, success(options.emptyTemplates ? [] : [template]));
    if (endpoint === `/api/v1/workflow-templates/${template.id}`) return respond(route, success(template));
    if (endpoint === `/api/v1/workflow-templates/${template.id}/versions`) return respond(route, success(options.publishedOnly ? [published] : [draft, published]));
    if (endpoint === `/api/v1/workflow-template-versions/${draft.id}`) return respond(route, success({ version: draft, steps: [firstStep, secondStep] }));
    if (endpoint === `/api/v1/workflow-template-versions/${published.id}`) return respond(route, success({ version: published, steps: [firstStep, secondStep] }));
    if (endpoint === "/api/v1/projects/project-1") return respond(route, success({ id: "project-1", name: "Acme launch", clientName: "Acme", serviceId: "service-1", serviceName: "Meta Ads", serviceCode: "META_ADS", status: options.withOnboarding ? "ONBOARDING" : "DRAFT", version: options.withOnboarding ? 1 : 0 }));
    if (endpoint === "/api/v1/projects/project-1/onboarding") {
      if (!options.withOnboarding) return respond(route, failure("RESOURCE_NOT_FOUND", "Requested resource was not found."), 404);
      return respond(route, success({ onboarding: { id: "onboarding-1", projectId: "project-1", sourceTemplateId: template.id, sourceTemplateVersionId: published.id, snapshotVersionNumber: 1, status: "DRAFT", ready: false, version: 0 }, progress: 50, steps: [{ ...firstStep, onboardingId: "onboarding-1", sourceStepId: firstStep.id, applicable: true, status: "COMPLETED", dependencyStepInstanceIds: [], version: 1 }, { ...secondStep, onboardingId: "onboarding-1", sourceStepId: secondStep.id, applicable: true, status: "AVAILABLE", dependencyStepInstanceIds: ["step-1"], version: 1 }] }));
    }
    return respond(route, failure("RESOURCE_NOT_FOUND", "Requested resource was not found."), 404);
  });
}

test.describe("Phase 3 workflow UI", () => {
  test("shows a useful empty template state and creation path", async ({ page }) => {
    await mockApi(page, { emptyTemplates: true });
    await page.goto("/app/workflows");
    await expect(page.getByRole("heading", { name: "Workflow templates" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "No workflow templates" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Create template" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false);
  });

  test("edits a draft with keyboard alternatives and dependency proof", async ({ page }) => {
    await mockApi(page);
    await page.goto(`/app/workflows/${template.id}`);
    await expect(page.getByRole("heading", { name: template.name })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Ordered steps" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Move Collect brief down" })).toBeVisible();
    await expect(page.getByText("Blocking steps")).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false);
  });

  test("renders published versions as read-only", async ({ page }) => {
    await mockApi(page, { publishedOnly: true });
    await page.goto(`/app/workflows/${template.id}`);
    await expect(page.getByText(/This version is immutable/)).toBeVisible();
    await expect(page.getByRole("button", { name: "Save draft" })).toHaveCount(0);
  });

  test("separates runtime progress readiness and step action", async ({ page }) => {
    await mockApi(page, { withOnboarding: true });
    await page.goto("/app/projects/project-1");
    await expect(page.getByRole("heading", { name: "Acme launch" })).toBeVisible();
    await expect(page.getByText("50%")).toBeVisible();
    await expect(page.getByText("1 blocker")).toBeVisible();
    await expect(page.getByRole("progressbar", { name: "Onboarding progress" })).toHaveAttribute("aria-valuenow", "50");
    await expect(page.getByText("Your action")).toBeVisible();
    await expect(page.getByText("Waiting for our team")).toBeVisible();
    await expect(page.getByText("Available help")).toBeVisible();
    await expect(page.getByRole("button", { name: "Start" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false);
  });
});
