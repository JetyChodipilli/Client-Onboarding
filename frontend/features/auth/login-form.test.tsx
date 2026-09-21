import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LoginForm } from "./login-form";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const response = (body: unknown, ok = true, status = 200) => Promise.resolve({ ok, status, json: async () => body }) as Promise<Response>;
const csrf = { success: true, data: { headerName: "X-XSRF-TOKEN", token: "csrf" }, meta: {}, requestId: "req" };

describe("LoginForm", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows linked inline validation errors without making a request", async () => {
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    render(<LoginForm />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() => expect(screen.getByText("Check the highlighted fields.")).toHaveFocus());
    expect(screen.getAllByText("Enter a valid email address.")).toHaveLength(2);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("shows a recoverable API error with its request identifier", async () => {
    const fetch = vi.fn()
      .mockImplementationOnce(() => response(csrf))
      .mockImplementationOnce(() => response({ success: false, error: { code: "INVALID_CREDENTIALS", message: "The organization or credentials are invalid.", fieldErrors: [] }, requestId: "request-123" }, false, 401));
    vi.stubGlobal("fetch", fetch);
    render(<LoginForm />);
    await userEvent.type(screen.getByLabelText("Work email"), "person@example.com");
    await userEvent.type(screen.getByLabelText("Organization slug"), "agency");
    await userEvent.type(screen.getByLabelText("Password"), "WrongPassword7");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText(/Request request-123/)).toBeInTheDocument();
  });

  it("moves privileged users into accessible MFA enrollment", async () => {
    const fetch = vi.fn()
      .mockImplementationOnce(() => response(csrf))
      .mockImplementationOnce(() => response({ success: true, data: { state: "MFA_ENROLLMENT_REQUIRED", challengeToken: "challenge", enrollmentSecret: "BASE32SECRET", recoveryCodes: [], user: {} }, meta: {}, requestId: "req" }, true, 202));
    vi.stubGlobal("fetch", fetch);
    render(<LoginForm />);
    await userEvent.type(screen.getByLabelText("Work email"), "admin@example.com");
    await userEvent.type(screen.getByLabelText("Organization slug"), "agency");
    await userEvent.type(screen.getByLabelText("Password"), "CorrectHorse7Battery");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("heading", { name: "Secure your account" })).toBeInTheDocument();
    expect(screen.getByText("BASE32SECRET")).toBeInTheDocument();
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  });
});
