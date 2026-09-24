import { expect, test, type Page, type Route } from "@playwright/test";
import type { AssetView } from "../features/assets/types";
const success = (data: unknown) => ({ success: true, data, meta: {}, requestId: "assets-test" });
const failure = (message: string) => ({ success: false, error: { code: "ASSET_STATE_CONFLICT", message, fieldErrors: [] }, requestId: "assets-test" });
const cors = { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true", "access-control-allow-methods": "GET,POST,PUT,OPTIONS", "access-control-allow-headers": "Content-Type,X-XSRF-TOKEN" };
const respond = (r: Route, value: unknown, status = 200) => r.fulfill({ status, json: value, headers: cors });
const requirement = { id: "requirement", name: "Brand document", instructions: "Provide the latest brand guide.", allowedMimes: ["text/plain"], maxBytes: 1000, version: 0 };
const initial: AssetView = { projectId: "project", stepId: "step", stepName: "Share your brand file", projectStatus: "ONBOARDING", onboardingStatus: "IN_PROGRESS", stepStatus: "AVAILABLE", allowSkip: false, allowReopen: false, requirement, version: 0 };
const file = { id: "file", versionNumber: 1, filename: "brand.txt", mime: "text/plain", byteSize: 14, status: "SUBMITTED", scanStatus: "CLEAN", scanMessage: "File validation and malware scanning passed.", createdAt: "2026-09-24T12:00:00Z", downloadable: true };
const path = "/portal/projects/project/assets/step";
async function mock(page: Page, internal = false, initialState = initial) {
  let view = structuredClone(initialState);
  await page.route("**/api/v1/**", (r) => {
    const p = new URL(r.request().url()).pathname; const method = r.request().method();
    if (method === "OPTIONS") return r.fulfill({ status: 204, headers: cors });
    if (p.endsWith("/auth/csrf")) return respond(r, success({ headerName: "X-XSRF-TOKEN", token: "csrf" }));
    if (p.endsWith("/auth/me")) return respond(r, success({ id: "user", displayName: "Ada", role: internal ? "Reviewer" : "CLIENT_MEMBER", email: "ada@example.test", permissions: internal ? ["ASSET_READ", "ASSET_MANAGE", "ASSET_REVIEW"] : ["CLIENT_PORTAL_READ", "CLIENT_PORTAL_STEP_UPDATE"] }));
    if (p.endsWith("/asset-requirements")) return respond(r, success(method === "GET" ? [requirement] : { ...requirement, ...r.request().postDataJSON() }));
    if (p.endsWith("/projects/project")) return respond(r, success({ progress: 0, availableHelp: "Email your project manager for help.", steps: [] }));
    if (p.endsWith("/versions")) return respond(r, success(view.current ? [{ file: view.current, reviews: [] }] : []));
    if (p.endsWith("/assets/step") || p.endsWith("/asset-responses/step")) return respond(r, success(view));
    if (p.endsWith("/upload-url")) { view = { ...view, version: 1, stepStatus: "IN_PROGRESS", current: { ...file, status: "REQUESTED", scanStatus: "PENDING", downloadable: false } }; return respond(r, success({ asset: view, upload: { url: "http://localhost:9000/test-upload", method: "PUT", headers: { "content-type": "text/plain" }, expiresAt: "2026-09-24T12:10:00Z" } })); }
    if (p.endsWith("/submit")) { view = { ...view, version: 3, stepStatus: "SUBMITTED", current: file }; return respond(r, success(view)); }
    if (p.endsWith("/review")) { const body = r.request().postDataJSON(); view = { ...view, version: view.version + 1, stepStatus: body.decision === "APPROVED" ? "COMPLETED" : body.decision, current: { ...file, status: body.decision, reviewNote: body.note } }; return respond(r, success(view)); }
    return respond(r, failure("Requested resource was not found."), 404);
  });
  await page.route("http://localhost:9000/test-upload", (r) => r.fulfill({ status: 200, body: "", headers: { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-methods": "PUT,OPTIONS", "access-control-allow-headers": "content-type" } }));
}
test("client uploads with progress, submits and retains file history", async ({ page }, info) => {
  const errors: string[] = []; page.on("pageerror", (e) => errors.push(e.message)); page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  await mock(page); await page.goto(path);
  await expect(page.getByRole("heading", { name: "Brand document", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Upload and submit file" })).toBeDisabled();
  await page.getByLabel("Choose a file").setInputFiles({ name: "brand.txt", mimeType: "text/plain", buffer: Buffer.from("Our brand file") });
  await page.getByRole("button", { name: "Upload and submit file" }).click();
  await expect(page.getByText("File submitted. Your project team will review it.")).toBeVisible();
  await expect(page.getByLabel("Asset status")).toHaveText("SUBMITTED");
  await expect(page.getByRole("button", { name: "Download current file" })).toBeVisible();
  await expect(page.getByLabel("Choose a file")).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: info.outputPath("asset-submitted.png"), fullPage: true }); expect(errors).toEqual([]);
});
test("file validation and server conflicts preserve a recoverable upload screen", async ({ page }) => {
  await mock(page); await page.goto(path);
  await page.getByLabel("Choose a file").setInputFiles({ name: "large.txt", mimeType: "text/plain", buffer: Buffer.alloc(1001, 65) });
  await expect(page.getByText(/Choose a non-empty file no larger/)).toBeVisible();
  await page.getByLabel("Choose a file").setInputFiles({ name: "brand.txt", mimeType: "text/plain", buffer: Buffer.from("Safe file") });
  await page.route("**/assets/step/upload-url", (r) => respond(r, failure("This asset changed. Reload before trying again."), 409));
  await page.getByRole("button", { name: "Upload and submit file" }).click();
  await expect(page.getByText("This asset changed. Reload before trying again.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Reload file status" })).toBeEnabled();
});
test("quarantined files have no download or review bypass", async ({ page }, info) => {
  await mock(page, false, { ...initial, version: 3, stepStatus: "IN_PROGRESS", current: { ...file, status: "QUARANTINED", scanStatus: "INFECTED", scanMessage: "This file was quarantined by malware scanning. Upload a different, safe file.", downloadable: false } });
  await page.goto(path); await expect(page.getByLabel("Asset status")).toHaveText("QUARANTINED");
  await expect(page.getByRole("button", { name: /Download/ })).toHaveCount(0); await expect(page.getByRole("button", { name: "Approve file" })).toHaveCount(0);
  await expect(page.getByLabel("Choose a file")).toBeEnabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: info.outputPath("asset-quarantined.png"), fullPage: true });
});
test("reviewer must provide feedback before requesting a replacement", async ({ page }, info) => {
  await mock(page, true, { ...initial, version: 3, stepStatus: "SUBMITTED", current: file });
  await page.goto("/app/assets/responses/step"); await expect(page.getByRole("button", { name: "Request revision" })).toBeDisabled();
  await page.getByLabel("Feedback (required for revision)").fill("Please provide the final brand guide.");
  await page.getByRole("button", { name: "Request revision" }).click();
  await expect(page.getByText("Revision requested. The client can upload a new version.")).toBeVisible();
  await expect(page.getByLabel("Asset status")).toHaveText("NEEDS REVISION");
  await page.screenshot({ path: info.outputPath("asset-review.png"), fullPage: true });
});
test("requirement catalog handles creation, loading, empty and denied states", async ({ page }, info) => {
  await mock(page, true);
  await page.route("**/asset-requirements?*", async (r) => { await new Promise((resolve) => setTimeout(resolve, 400)); return respond(r, success([])); });
  await page.goto("/app/assets"); await expect(page.getByText("Loading requirements…")).toBeVisible(); await expect(page.getByRole("heading", { name: "No requirements found" })).toBeVisible();
  await page.getByLabel("Requirement name", { exact: true }).fill("Brand document"); await page.getByLabel("Instructions", { exact: true }).fill("Provide the final brand guide.");
  await page.getByRole("button", { name: "Create requirement", exact: true }).click();
  await expect(page.getByText("Requirement created. Attach it to a file upload step in Workflows.")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: info.outputPath("asset-requirements.png"), fullPage: true });
  await page.route("**/asset-responses/denied", (r) => respond(r, failure("You do not have permission to read these files."), 403));
  await page.goto("/app/assets/responses/denied"); await expect(page.getByText("You do not have permission to read these files.")).toBeVisible();
});
