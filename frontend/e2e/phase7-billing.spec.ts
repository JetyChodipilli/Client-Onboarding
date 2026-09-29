import { expect, test, type Page, type Route } from "@playwright/test";
import type { InvoiceView } from "../features/billing/types";
const success = (data: unknown) => ({ success: true, data, meta: {}, requestId: "billing-test" });
const fail = (message: string) => ({ success: false, error: { code: "BILLING_STATE_CONFLICT", message, fieldErrors: [] }, requestId: "billing-test" });
const cors = { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true", "access-control-allow-methods": "GET,POST,OPTIONS", "access-control-allow-headers": "Content-Type,X-XSRF-TOKEN,Idempotency-Key" };
const respond = (r: Route, body: unknown, status = 200) => r.fulfill({ status, json: body, headers: cors });
const initial: InvoiceView = { invoice: { id: "invoice", projectId: "project", stepId: "step", invoiceNumber: "INV-LAUNCH-2026", currency: "INR", policy: "DEPOSIT", subtotalMinor: 10000, taxMinor: 0, totalMinor: 10000, thresholdMinor: 5000, capturedMinor: 0, refundedMinor: 0, reservedMinor: 0, status: "SENT", dueDate: "2026-12-31", createdAt: "2026-09-29T12:00:00Z", version: 1 }, items: [{ description: "Launch planning", quantity: 1, unitAmountMinor: 10000, taxBasisPoints: 0, totalMinor: 10000 }], displayStatus: "SENT", paidMinor: 0, balanceMinor: 10000, thresholdRemainingMinor: 5000, requirementSatisfied: false };
const portal = "/portal/projects/project/payments/step";
async function mock(page: Page, options: { internal?: boolean; draft?: boolean; confirm?: boolean; unknown?: boolean; denied?: boolean } = {}) {
  let view = structuredClone(initial); let exists = !options.draft; let pending = options.unknown;
  await page.route("https://checkout.razorpay.com/v1/checkout.js", (r) => r.fulfill({ contentType: "text/javascript", body: `window.Razorpay = class { constructor(o) { this.options = o; } on() {} open() { this.options.handler({ razorpay_payment_id: 'pay_fixture', razorpay_order_id: 'order_fixture', razorpay_signature: '${"a".repeat(64)}' }); } };` }));
  await page.route("**/api/v1/**", (r) => {
    const path = new URL(r.request().url()).pathname; const method = r.request().method();
    if (method === "OPTIONS") return r.fulfill({ status: 204, headers: cors });
    if (path.endsWith("/auth/csrf")) return respond(r, success({ headerName: "X-XSRF-TOKEN", token: "csrf" }));
    if (path.endsWith("/auth/me")) return respond(r, success({ displayName: "Ada", role: options.internal ? "Finance" : "CLIENT_MEMBER", email: "ada@example.test", permissions: options.denied ? [] : options.internal ? ["INVOICE_READ", "INVOICE_CREATE", "INVOICE_SEND", "PAYMENT_OVERRIDE", "PAYMENT_REFUND", "PAYMENT_RECONCILE"] : ["CLIENT_PORTAL_READ", "CLIENT_PORTAL_STEP_UPDATE"] }));
    if (path.endsWith("/projects/project")) return respond(r, success({ projectStatus: "ONBOARDING", onboardingStatus: "IN_PROGRESS", currentStatus: "ACTION_REQUIRED", progress: view.requirementSatisfied ? 100 : 0, availableHelp: "Email your project manager for help.", steps: [{ id: "step", status: view.requirementSatisfied ? "COMPLETED" : "AVAILABLE" }] }));
    if (path.endsWith("/invoices") && method === "POST") { exists = true; view = { ...view, invoice: { ...view.invoice, ...r.request().postDataJSON(), status: "DRAFT", version: 0 }, displayStatus: "DRAFT" }; return respond(r, success(view), 201); }
    if (path.endsWith("/invoices")) return respond(r, success(exists ? [view] : []));
    if (path.endsWith("/payments/step") || path.endsWith("/invoices/by-step/step") || path.endsWith("/invoices/invoice")) return respond(r, success(exists ? view : null));
    if (path.endsWith("/viewed")) { view.invoice.status = "VIEWED"; view.displayStatus = "VIEWED"; return respond(r, success(view)); }
    if (path.endsWith("/send")) { view.invoice.status = "SENT"; view.displayStatus = "SENT"; return respond(r, success(view)); }
    if (path.endsWith("/invoices/invoice/payments")) return respond(r, success({ providerAvailable: true, pendingCheckout: pending ? { id: "session", invoiceId: "invoice", status: "UNKNOWN", amountMinor: 5000, receipt: "receipt", version: 1 } : undefined, transactions: { items: view.paidMinor ? [{ id: "transaction", provider: "RAZORPAY", providerPaymentId: "pay_fixture", status: "CAPTURED", amountMinor: 5000, refundedMinor: 0, createdAt: "2026-09-29T12:00:00Z" }] : [], page: 0, size: 20, totalElements: view.paidMinor ? 1 : 0 } }));
    if (path.endsWith("/checkout")) return respond(r, success({ id: "session", invoiceId: "invoice", status: "READY", keyId: "rzp_test_fixture", orderId: "order_fixture", amountMinor: r.request().postDataJSON().amountMinor, currency: "INR", receipt: "receipt", version: 1 }));
    if (path.endsWith("/confirm")) { if (options.confirm) view = { ...view, paidMinor: 5000, balanceMinor: 5000, thresholdRemainingMinor: 0, requirementSatisfied: true, displayStatus: "PARTIALLY_PAID", invoice: { ...view.invoice, status: "PARTIALLY_PAID", capturedMinor: 5000 } }; return respond(r, success({ message: "Checked" })); }
    if (path.endsWith("/reconcile")) { pending = false; return respond(r, success({})); }
    return respond(r, fail("Requested resource was not found."), 404);
  });
}
test("client checkout uses confirmed balances and exposes project context", async ({ page }, info) => {
  const errors: string[] = []; page.on("pageerror", (e) => errors.push(e.message)); page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  await mock(page, { confirm: true }); await page.goto(portal); await expect(page.getByRole("heading", { name: "Invoice", exact: true })).toBeVisible();
  await page.keyboard.press("Tab"); await expect(page.getByRole("link", { name: "Skip to portal" })).toBeFocused();
  await page.getByRole("button", { name: "Pay with Razorpay" }).click(); await expect(page.getByText("Payment requirement met", { exact: true })).toBeVisible();
  await expect(page.getByText("PARTIALLY PAID", { exact: true })).toBeVisible(); await expect(page.getByRole("heading", { name: "Waiting for our team" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);await page.screenshot({ path: info.outputPath("billing-client-confirmed.png"), fullPage: true });expect(errors).toEqual([]);
});
test("a browser success callback alone never marks an invoice paid", async ({ page }) => {
  await mock(page);await page.goto(portal);await page.getByRole("button", { name: "Pay with Razorpay" }).click();await expect(page.getByText("Payment evidence checked. The balance above shows the confirmed status.")).toBeVisible();await expect(page.getByText("Payment requirement met", { exact: true })).toHaveCount(0);await expect(page.getByText("No payment transactions recorded yet.")).toBeVisible();
});
test("an unknown provider response blocks duplicate checkout with a clear recovery path", async ({ page }, info) => {
  await mock(page, { unknown: true });await page.goto(portal);await expect(page.getByText(/Your payment request is awaiting confirmation from Razorpay/)).toBeVisible();await expect(page.getByRole("button", { name: "Resume Razorpay checkout" })).toBeDisabled();await expect(page.getByRole("button", { name: "Refresh payment status" })).toBeEnabled();await page.screenshot({ path: info.outputPath("billing-pending.png"), fullPage: true });
});
test("internal draft creation validates inputs and publishes the immutable invoice", async ({ page }, info) => {
  await mock(page, { internal: true, draft: true });await page.goto("/app/invoices/steps/step");await expect(page.getByRole("heading", { name: "Create invoice", exact: true })).toBeVisible();
  await page.getByLabel("Description", { exact: true }).fill("Launch planning");await page.getByLabel("Unit price (INR)").fill("100");await page.getByLabel("Due date", { exact: true }).fill("2026-12-31");await page.getByRole("button", { name: "Create draft invoice" }).click();await page.getByRole("button", { name: "Publish to client portal" }).click();await expect(page.getByText("Invoice published in the client portal.")).toBeVisible();await expect(page.getByRole("button", { name: "Publish to client portal" })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);await page.screenshot({ path: info.outputPath("billing-internal.png"), fullPage: true });
});
test("billing permission, empty and API error states are explicit", async ({ page }) => {
  await mock(page, { internal: true, denied: true });await page.goto("/app/invoices");await expect(page.getByText("You do not have permission to read invoices.")).toBeVisible();
  await page.unroute("**/api/v1/**");await mock(page, { internal: true, draft: true });await page.reload();await expect(page.getByRole("heading", { name: "No invoices found" })).toBeVisible();
  await page.route("**/api/v1/invoices?**", (r) => respond(r, fail("Billing is temporarily unavailable."), 503));await page.reload();await expect(page.getByText("Billing is temporarily unavailable.")).toBeVisible();await expect(page.getByRole("button", { name: "Retry invoices" })).toBeEnabled();
});
