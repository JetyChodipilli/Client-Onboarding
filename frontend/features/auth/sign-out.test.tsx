import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PortalShell } from "@/features/portal/portal-shell";
import { InternalShell } from "@/features/settings/internal-shell";
import { ApiClientError } from "@/lib/api-client";
import { authApi } from "./auth-api";

const navigation = vi.hoisted(() => ({ replace: vi.fn(), refresh: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation, usePathname: () => "/app" }));
vi.mock("@/components/shared/theme-toggle", () => ({ ThemeToggle: () => null }));
vi.mock("./auth-api", () => ({ authApi: { me: vi.fn(), logout: vi.fn() } }));

describe.each([
  { name: "client", Shell: PortalShell, destination: "/client/login" },
  { name: "internal", Shell: InternalShell, destination: "/login" },
])("$name sign out", ({ Shell, destination }) => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.me).mockResolvedValue({ id: "user", organizationId: "org", email: "client@example.test", displayName: "Client", role: "MEMBER", permissions: ["CLIENT_PORTAL_READ"], sessionId: "session" });
  });

  it("keeps the workspace open on network failure and allows a successful retry", async () => {
    vi.mocked(authApi.logout).mockRejectedValueOnce(new TypeError("Network unavailable"))
      .mockResolvedValueOnce({ message: "Signed out" });
    render(<Shell><p>Current work</p></Shell>);
    const buttons = await screen.findAllByRole("button", { name: "Sign out" });
    await userEvent.click(buttons[0]);
    expect(await screen.findByRole("alert")).toHaveTextContent("Your session may still be active");
    expect(screen.getByText("Current work")).toBeVisible();
    expect(navigation.replace).not.toHaveBeenCalled();
    await userEvent.click(buttons[0]);
    await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith(destination));
    expect(navigation.refresh).toHaveBeenCalledOnce();
  });

  it("returns to sign in when the server confirms the session is already absent", async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new ApiClientError("Expired", 401, "AUTHENTICATION_REQUIRED"));
    render(<Shell><p>Current work</p></Shell>);
    await userEvent.click((await screen.findAllByRole("button", { name: "Sign out" }))[0]);
    await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith(destination));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
