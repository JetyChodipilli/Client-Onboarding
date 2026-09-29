import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceCreate } from "./invoice-create";
import { paise, itemPreview } from "./types";
import { billingApi } from "./billing-api";
vi.mock("./billing-api", () => ({ billingApi: { create: vi.fn() } }));
describe("invoice amounts", () => {
  it("converts decimal amounts exactly and rejects fractional paise and exponent input", () => { expect(paise("0.29")).toBe(29); expect(paise("100.1")).toBe(10010); for (const v of ["-1", "1e3", "1.001", "", "Infinity", "1000000001"]) expect(() => paise(v)).toThrow(); });
  it("rounds tax once per item using half-up and includes quantities", () => { expect(itemPreview([{ description: "A", quantity: 1, unitAmountMinor: 1, taxBasisPoints: 5000 }, { description: "B", quantity: 3, unitAmountMinor: 1999, taxBasisPoints: 1800 }])).toBe(7078); });
  it("submits invoice content with a stable key after a recoverable error", async () => {
    vi.mocked(billingApi.create).mockRejectedValue(new Error("Temporary failure"));
    render(<InvoiceCreate stepId="step" created={vi.fn()} />); const user = userEvent.setup();
    await user.type(screen.getByLabelText("Description"), "Design work");await user.type(screen.getByLabelText("Unit price (INR)"), "100.29");await user.type(screen.getByLabelText("Due date"), "2026-12-31");
    await user.click(screen.getByRole("button", { name: "Create draft invoice" }));expect(await screen.findByRole("alert")).toHaveTextContent("Temporary failure");await user.click(screen.getByRole("button", { name: "Create draft invoice" }));
    expect(billingApi.create).toHaveBeenCalledTimes(2);expect(vi.mocked(billingApi.create).mock.calls[0][3][0].unitAmountMinor).toBe(10029);expect(vi.mocked(billingApi.create).mock.calls[0][4]).toBe(vi.mocked(billingApi.create).mock.calls[1][4]);
  });
});
