import { createHmac } from "node:crypto";
import { expect, test } from "@playwright/test";
import { smtpInbox } from "./smtp-inbox";

test.skip(!process.env.LIVE_BACKEND, "Set LIVE_BACKEND=1 when the Phase 1 backend is running.");
test.describe.configure({ retries: 0 });

function decodeBase32(value: string) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const character of value) bits += alphabet.indexOf(character).toString(2).padStart(5, "0");
  const bytes: number[] = [];
  for (let index = 0; index + 8 <= bits.length; index += 8) bytes.push(Number.parseInt(bits.slice(index, index + 8), 2));
  return Buffer.from(bytes);
}

function currentTotp(secret: string) {
  const counter = Math.floor(Date.now() / 1000 / 30);
  const buffer = Buffer.alloc(8); buffer.writeBigUInt64BE(BigInt(counter));
  const hash = createHmac("sha1", decodeBase32(secret)).update(buffer).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  const binary = (hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return binary.toString().padStart(6, "0");
}

test("live backend: MFA workspace and invitation email to activated client portal", async ({ page, browser }, testInfo) => {
  test.setTimeout(120_000);
  test.skip(testInfo.project.name !== "desktop-chromium");
  const browserErrors: string[] = [];
  page.on("console", (message) => { if (message.type() === "error") browserErrors.push(message.text()); });
  page.on("pageerror", (error) => browserErrors.push(error.message));
  await page.goto("/login");
  await page.getByLabel("Work email").fill("e2e-admin@example.test");
  await page.getByLabel("Organization slug").fill("e2e-agency");
  await page.getByLabel("Password", { exact: true }).fill("CorrectHorse7Battery");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Secure your account" })).toBeVisible();
  const secret = (await page.locator("code").textContent())?.trim();
  expect(secret).toBeTruthy();
  await page.getByLabel("Verification or recovery code").fill(currentTotp(secret!));
  await page.getByRole("button", { name: "Verify and continue" }).click();
  await expect(page.getByRole("heading", { name: "Save your recovery codes" })).toBeVisible();
  await expect(page.locator('[aria-label="Recovery codes"] code')).toHaveCount(8);
  await page.getByRole("button", { name: "I saved these codes" }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole("heading", { name: "Workspace security baseline" })).toBeVisible();

  const api = page.context().request;
  const base = "http://localhost:8080/api/v1";
  async function mutate(path: string, data: unknown, method = "POST") {
    const csrf = await (await api.get(`${base}/auth/csrf`)).json();
    const response = await api.fetch(base + path, {
      method, data, headers: { [csrf.data.headerName]: csrf.data.token },
    });
    expect(response.ok(), `${method} ${path}: ${response.status()}`).toBe(true);
    return (await response.json()).data;
  }
  const inbox = await smtpInbox();
  const clientContext = await browser.newContext();
  try {
    const form = await mutate("/forms", { name: "Live project brief", description: "Launch requirements" });
    const formDraft = await mutate(`/form-versions/${form.definition.id}/fields`, { version: 0, fields: [
      { key: "business", label: "Business name", type: "TEXT", required: true, options: [] },
      { key: "has_site", label: "Existing website?", type: "BOOLEAN", required: true, options: [] },
      { key: "website", label: "Website URL", type: "URL", required: true, options: [], condition: { fieldKey: "has_site", operator: "EQUALS", value: "true" } },
    ] }, "PUT");
    await mutate(`/form-versions/${form.definition.id}/publish?version=${formDraft.version}`, {});
    const client = await mutate("/clients", { name: "Live Portal Client", status: "ACTIVE", version: 0 });
    const contact = await mutate(`/clients/${client.id}/contacts`, { name: "Client Reader", email: "reader@client.test", primary: true, version: 0 });
    const service = await mutate("/services", { code: "PORTAL-LIVE", name: "Portal launch", status: "ACTIVE", version: 0 });
    const project = await mutate("/projects", { clientId: client.id, serviceId: service.id, name: "Live portal launch", version: 0 });
    const template = await mutate("/workflow-templates", { name: "Live portal flow", serviceId: service.id });
    const versionId = template.draftVersion.id;
    const configured = await mutate(`/workflow-template-versions/${versionId}/steps`, {
      version: template.draftVersion.version,
      steps: [{ stepKey: "WELCOME", name: "Read your welcome guide", description: "Confirm that you have read your project welcome guide.", stepType: "WELCOME", displayOrder: 0, required: true, blocking: true, clientVisible: true, requiresReview: false, dependencyMode: "NONE", allowSkip: false, allowReopen: false, configuration: {}, dependencyStepIds: [] },
        { stepKey: "BRIEF", name: "Complete your questionnaire", stepType: "FORM", displayOrder: 1, required: true, blocking: true, clientVisible: true, requiresReview: true, dependencyMode: "NONE", allowSkip: false, allowReopen: false, configuration: { formVersionId: form.definition.id }, dependencyStepIds: [] }],
    }, "PUT");
    await mutate(`/workflow-template-versions/${versionId}/publish?version=${configured.version.version}`, {});
    const onboarding = await mutate(`/projects/${project.id}/onboarding`, { templateVersionId: versionId, projectVersion: project.version });
    const invited = await mutate(`/onboardings/${onboarding.onboarding.id}/client-invitations`, { contactId: contact.id, role: "MEMBER" });
    expect(invited.deliveryStatus).toBe("SENT");
    expect(inbox.messages).toHaveLength(1);
    const link = inbox.messages[0].match(/http[^\s]+\/client\/accept-invitation\?token=[^\s]+/)?.[0];
    expect(link).toBeTruthy();
    const activation = new URL(link!);
    const clientPage = await clientContext.newPage();
    clientPage.on("pageerror", (error) => browserErrors.push(error.message));
    await clientPage.goto(`http://localhost:3000${activation.pathname}${activation.search}`);
    await expect(clientPage.getByRole("heading", { name: "Join Live portal launch" })).toBeVisible();
    await clientPage.getByLabel("Create or confirm your password").fill("ClientPortal7Password");
    await clientPage.getByLabel("Confirm password").fill("ClientPortal7Password");
    await clientPage.getByRole("button", { name: "Activate secure portal" }).click();
    await clientPage.getByRole("link", { name: "Continue to client sign in" }).click();
    await clientPage.getByLabel("Work email").fill("reader@client.test");
    await clientPage.getByLabel("Password", { exact: true }).fill("ClientPortal7Password");
    await clientPage.getByRole("button", { name: "Open client portal" }).click();
    await clientPage.getByRole("link").filter({ has: clientPage.getByRole("heading", { name: "Live portal launch" }) }).click();
    await clientPage.getByRole("button", { name: "Start this step" }).click();
    await clientPage.getByRole("button", { name: "Mark complete" }).click();
    await expect(clientPage.getByRole("progressbar", { name: "Onboarding progress" })).toHaveAttribute("aria-valuenow", "50");
    await clientPage.getByRole("link", { name: "Open questionnaire" }).click();
    await expect(clientPage.getByRole("heading", { name: "Live project brief" })).toBeVisible();
    await clientPage.getByLabel(/Business name/).fill("First business");
    await clientPage.getByLabel(/Existing website/).selectOption("false");
    await expect(clientPage.getByLabel(/Website URL/)).toHaveCount(0);
    await clientPage.getByRole("button", { name: "Save draft", exact: true }).click();
    await expect(clientPage.getByText("Draft saved. You can return to it later.")).toBeVisible();
    await clientPage.reload();
    await expect(clientPage.getByLabel(/Business name/)).toHaveValue("First business");
    await clientPage.getByRole("button", { name: "Submit answers", exact: true }).click();
    await expect(clientPage.getByText("Your answers have been submitted.")).toBeVisible();
    const formStep = onboarding.steps.find((step: { stepType: string }) => step.stepType === "FORM");
    await page.goto(`/app/forms/responses/${formStep.id}`);
    await page.getByLabel("Feedback (required for revision)").fill("Please use the legal company name.");
    await page.getByRole("button", { name: "Request revision", exact: true }).click();
    await expect(page.getByText("Revision requested. The client can update their answers.")).toBeVisible();
    await clientPage.reload();
    await expect(clientPage.getByText("Please use the legal company name.", { exact: true })).toBeVisible();
    await clientPage.getByLabel(/Business name/).fill("Legal company name");
    await clientPage.getByRole("button", { name: "Resubmit answers", exact: true }).click();
    await expect(clientPage.getByText("Your answers have been submitted.")).toBeVisible();
    await page.reload();
    await page.getByRole("button", { name: "Approve answers", exact: true }).click();
    await expect(page.getByText("Approved. The workflow step is complete.")).toBeVisible();
    await clientPage.reload();
    await expect(clientPage.getByText("APPROVED", { exact: true })).toBeVisible();
    await clientPage.getByText(/^Submission 1 ·/).click();
    await expect(clientPage.getByText("First business", { exact: true })).toBeVisible();
    await clientPage.getByRole("link", { name: "Back to project" }).click();
    await expect(clientPage.getByRole("progressbar", { name: "Onboarding progress" })).toHaveAttribute("aria-valuenow", "100");
    expect((await clientContext.request.get(`${base}/clients`)).status()).toBe(403);
    await clientPage.getByRole("button", { name: "Sign out" }).click();
    await expect(clientPage).toHaveURL(/\/client\/login$/);
    expect((await clientContext.request.get(`${base}/client-portal/projects`)).status()).toBe(401);
    await clientPage.goto(`http://localhost:3000${activation.pathname}${activation.search}`);
    await expect(clientPage.getByRole("heading", { name: "This invitation is unavailable" })).toBeVisible();
  } finally {
    await clientContext.close();
    await inbox.close();
  }
  expect(browserErrors).toEqual([]);
});
