import { render, screen } from "@testing-library/react";
import { beforeAll, describe, expect, it, vi } from "vitest";

vi.mock("@/components/shared/motion-shell", () => ({
  MotionShell: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@/components/shared/theme-toggle", () => ({
  ThemeToggle: () => <button type="button">Toggle theme</button>,
}));

describe("Platform landing page", () => {
  beforeAll(() => {
    process.env.NEXT_PUBLIC_API_URL = "http://localhost:8080";
  });

  it("states the active asset-management boundary", async () => {
    const { default: Home } = await import("./page");
    render(<Home />);

    expect(
      screen.getByRole("heading", { name: "Make every client start feel clear." }),
    ).toBeInTheDocument();
    expect(screen.getByText("Modular monolith")).toBeInTheDocument();
    expect(screen.getByText("Phase 6 implemented")).toBeInTheDocument();
  });
});
