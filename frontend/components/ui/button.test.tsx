import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "./button";

describe("Button", () => {
  it("renders an accessible button and forwards native disabled state", () => {
    render(<Button disabled>Continue</Button>);
    expect(screen.getByRole("button", { name: "Continue" })).toBeDisabled();
  });
});
