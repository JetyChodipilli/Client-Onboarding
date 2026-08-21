import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ErrorPage from "./error";

describe("global error state", () => {
  it("offers an explicit retry action", () => {
    const reset = vi.fn();

    render(<ErrorPage error={new Error("test failure")} reset={reset} />);

    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
