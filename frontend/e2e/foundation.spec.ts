import { expect, test } from "@playwright/test";

test.describe("platform foundation", () => {
  test("renders without console errors or horizontal overflow", async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => consoleErrors.push(error.message));

    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "Make every client start feel clear." }),
    ).toBeVisible();
    await expect(page.getByText("Phase 4 implemented")).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflow).toBe(false);
    expect(consoleErrors).toEqual([]);
  });

  test("supports keyboard navigation and theme switching", async ({ page }) => {
    await page.goto("/");
    await page.keyboard.press("Tab");
    await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#main-content")).toBeVisible();

    const themeButton = page.getByRole("button", { name: /Use (dark|light) theme/ });
    await themeButton.click();
    await expect(page.locator("html")).toHaveClass(/dark|light/);
  });

  test("remains usable with reduced motion", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Strong boundaries before complex workflows." }))
      .toBeVisible();
  });
});
