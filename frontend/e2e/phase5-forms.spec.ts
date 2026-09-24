import { expect, test, type Page, type Route } from "@playwright/test";
const success = (data: unknown) => ({ success: true, data, meta: {}, requestId: "forms-test" });
const failure = (message: string, fieldErrors: unknown[] = []) => ({ success: false, error: { code: "VALIDATION_FAILED", message, fieldErrors }, requestId: "forms-test" });
const cors = { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true", "access-control-allow-methods": "GET,POST,PUT,OPTIONS", "access-control-allow-headers": "Content-Type,X-XSRF-TOKEN" };
const respond = (route: Route, data: unknown, status = 200) => route.fulfill({ status, json: data, headers: cors });
const fields = [{ key: "business", label: "Business name", type: "TEXT", required: true, options: [] }, { key: "has_site", label: "Existing website?", type: "BOOLEAN", required: true, options: [] }, { key: "website", label: "Website URL", type: "URL", required: true, options: [], condition: { fieldKey: "has_site", operator: "EQUALS", value: "true" } }];
const template = { id: "form-1", name: "Project brief", description: "Tell us about your business", version: 0 };
const definition = { id: "version-1", formId: "form-1", versionNumber: 1, status: "PUBLISHED", fields, version: 2 };
const view = { formName: "Project brief", projectId: "project-1", stepName: "Complete your brief", projectStatus: "ONBOARDING", onboardingStatus: "IN_PROGRESS", stepStatus: "AVAILABLE", definition, response: { stepId: "step-1", formVersionId: "version-1", status: "DRAFT", answers: {}, version: 0, submissionNumber: 0 } };
const path = "/portal/projects/project-1/forms/step-1";

async function mock(page: Page, internal = false) {
  await page.route("**/api/v1/**", (route) => {
    const url = new URL(route.request().url()); const p = url.pathname;
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors });
    if (p.endsWith("/auth/csrf")) return respond(route, success({ headerName: "X-XSRF-TOKEN", token: "csrf" }));
    if (p.endsWith("/auth/me")) return respond(route, success({ id: "user", displayName: "Ada", organizationName: "Northstar", role: internal ? "Reviewer" : "CLIENT_MEMBER", email: "ada@example.test", permissions: internal ? ["FORM_MANAGE", "FORM_READ", "FORM_REVIEW"] : ["CLIENT_PORTAL_READ", "CLIENT_PORTAL_STEP_UPDATE"] }));
    if (p.endsWith("/forms") && route.request().method() === "GET") return respond(route, success([template]));
    if (p === "/api/v1/forms/form-1") return respond(route, success(template));
    if (p === "/api/v1/forms/form-1/versions") return respond(route, success([{ ...definition, status: "DRAFT" }]));
    if (p.endsWith("/submissions")) return respond(route, success([]));
    if (p.endsWith("/forms/step-1") || p.endsWith("/form-responses/step-1")) return respond(route, success(view));
    if (p.endsWith("/projects/project-1")) return respond(route, success({ progress: 0, availableHelp: "Email your project manager for help.", steps: [] }));
    return respond(route, failure("Requested resource was not found."), 404);
  });
}
test("client questionnaire saves a draft, shows conditions and submits", async ({ page }, testInfo) => {
  const errors: string[] = []; page.on("pageerror", (e) => errors.push(e.message));
  await mock(page);
  await page.route("**/forms/step-1/draft", (route) => respond(route, success({ ...view, stepStatus: "IN_PROGRESS", response: { ...view.response, ...route.request().postDataJSON(), version: 1 } })));
  await page.route("**/forms/step-1/submit", (route) => respond(route, success({ ...view, stepStatus: "SUBMITTED", response: { ...view.response, ...route.request().postDataJSON(), status: "SUBMITTED", version: 2, submissionNumber: 1 } })));
  await page.goto(path); await expect(page.getByRole("heading", { name: "Project brief", exact: true })).toBeVisible();
  await page.getByLabel(/Business name/).fill("Acme"); await page.getByLabel(/Existing website/).selectOption("true");
  await expect(page.getByLabel(/Website URL/)).toBeVisible(); await page.getByLabel(/Website URL/).fill("https://example.test");
  await page.getByRole("button", { name: "Save draft", exact: true }).click(); await expect(page.getByText("Draft saved. You can return to it later.")).toBeVisible();
  await page.getByRole("button", { name: "Submit answers", exact: true }).click(); await expect(page.getByText("Your answers have been submitted.")).toBeVisible();
  await expect(page.getByLabel(/Business name/)).toBeDisabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
  await page.screenshot({ path: testInfo.outputPath("questionnaire-submitted.png"), fullPage: true });
  expect(errors).toEqual([]);
});
test("validation and conflict errors preserve unsaved answers", async ({ page }) => {
  await mock(page); await page.route("**/forms/step-1/submit", (r) => respond(r, failure("Please correct this answer.", [{ field: "business", message: "A business name is required." }]), 400));
  await page.goto(path); await page.getByRole("button", { name: "Submit answers", exact: true }).click();
  await expect(page.getByRole("link", { name: /Business name: A business name is required/ })).toBeVisible();
  await expect(page.getByLabel(/Business name/)).toHaveAttribute("aria-invalid", "true");
  await page.getByLabel(/Business name/).fill("Do not lose this answer");
  await page.route("**/forms/step-1/draft", (r) => respond(r, failure("The form changed. Reload it before trying again."), 409));
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByText("The form changed. Reload it before trying again.")).toBeVisible();
  await expect(page.getByLabel(/Business name/)).toHaveValue("Do not lose this answer");
});
test("reviewer requests revision with required feedback", async ({ page }) => {
  await mock(page, true);
  const submitted = { ...view, stepStatus: "SUBMITTED", response: { ...view.response, status: "SUBMITTED", submissionNumber: 1, version: 1, answers: { business: "Acme", has_site: false } } };
  await page.route("**/form-responses/step-1", (r) => respond(r, success(submitted)));
  await page.route("**/form-responses/step-1/review", (r) => { expect(r.request().postDataJSON().note).toBe("Use the legal company name."); return respond(r, success({ ...submitted, stepStatus: "NEEDS_REVISION", response: { ...submitted.response, status: "NEEDS_REVISION", version: 2 } })); });
  await page.goto("/app/forms/responses/step-1"); await expect(page.getByRole("button", { name: "Request revision" })).toBeDisabled();
  await page.getByLabel("Feedback (required for revision)").fill("Use the legal company name.");
  await page.getByRole("button", { name: "Request revision" }).click(); await expect(page.getByText("Revision requested. The client can update their answers.")).toBeVisible();
});
test("builder saves versioned fields and previews conditional questions", async ({ page }, testInfo) => {
  await mock(page, true); let saved = definition;
  await page.route("**/form-versions/version-1/fields", (r) => { saved = { ...definition, ...r.request().postDataJSON(), status: "DRAFT", version: 3 }; return respond(r, success(saved)); });
  await page.route("**/form-versions/version-1/publish?*", (r) => respond(r, success({ ...saved, status: "PUBLISHED", version: 4 })));
  await page.goto("/app/forms/form-1"); await page.getByLabel("Question label", { exact: true }).first().fill("Legal business name");
  await page.getByRole("button", { name: "Save draft", exact: true }).click(); await expect(page.getByText("Draft saved.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Preview questionnaire" }).click();
  await expect(page.getByLabel(/Legal business name/)).toBeVisible();
  await page.getByRole("button", { name: "Publish version", exact: true }).click();
  await expect(page.getByText("Published. This version can now be selected in a workflow.")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
  await page.screenshot({ path: testInfo.outputPath("form-builder.png"), fullPage: true });
});
test("handles denied, unavailable, loading and empty form states", async ({ page }) => {
  await mock(page, true); await page.route("**/api/v1/forms?*", async (r) => { await new Promise((resolve) => setTimeout(resolve, 300)); return respond(r, success([])); });
  await page.goto("/app/forms"); await expect(page.getByText("Loading forms…")).toBeVisible(); await expect(page.getByRole("heading", { name: "No forms found" })).toBeVisible();
  await page.route("**/api/v1/form-responses/denied", (r) => respond(r, failure("You do not have permission to read this response."), 403));
  await page.goto("/app/forms/responses/denied"); await expect(page.getByText("You do not have permission to read this response.")).toBeVisible();
});
