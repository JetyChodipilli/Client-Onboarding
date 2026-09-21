import { createHmac } from "node:crypto";
import { expect, test } from "@playwright/test";

test.skip(!process.env.LIVE_BACKEND, "Set LIVE_BACKEND=1 when the Phase 1 backend is running.");

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

test("live backend: privileged bootstrap login enrolls MFA and opens the workspace", async ({ page }, testInfo) => {
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
  expect(browserErrors).toEqual([]);
});
