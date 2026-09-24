import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QuestionFields } from "./question-fields";
import { visibleFields, type FormField } from "./types";

const fields: FormField[] = [
  { key: "has_site", label: "Existing website?", type: "BOOLEAN", required: true, options: [] },
  { key: "website", label: "Website URL", type: "URL", required: true, options: [], condition: { fieldKey: "has_site", operator: "EQUALS", value: "true" } },
  { key: "details", label: "Details", type: "TEXTAREA", required: false, options: [], condition: { fieldKey: "website", operator: "NOT_EQUALS", value: "none" } },
];
afterEach(cleanup);
describe("questionnaires", () => {
  it("does not let hidden answers enable downstream questions", () => {
    expect(visibleFields(fields, { has_site: false, website: "https://hidden.example" }).map((f) => f.key)).toEqual(["has_site"]);
    expect(visibleFields(fields, { has_site: true, website: "https://example.test" })).toHaveLength(3);
    expect(visibleFields(fields, { has_site: true, website: "" })).toHaveLength(2);
    expect(visibleFields(fields, { has_site: true, website: "  " })).toHaveLength(2);
  });
  it("compares numeric conditions consistently and ignores inherited object properties", () => {
    const numeric: FormField[] = [
      { key: "constructor", label: "Count", type: "NUMBER", required: false, options: [] },
      { key: "details", label: "Details", type: "TEXT", required: false, options: [], condition: { fieldKey: "constructor", operator: "EQUALS", value: "1.0" } },
    ];
    expect(visibleFields(numeric, {})).toHaveLength(1);
    expect(visibleFields(numeric, { constructor: 1 })).toHaveLength(2);
  });
  it("keeps false as a valid boolean answer and exposes field errors", async () => {
    const update = vi.fn();
    render(<QuestionFields fields={fields} answers={{ has_site: true }} update={update} errors={{ website: "Enter an https URL." }} />);
    expect(screen.getByLabelText(/Website URL/)).toHaveAttribute("aria-invalid", "true");
    await userEvent.selectOptions(screen.getByLabelText(/Existing website/), "false");
    expect(update).toHaveBeenCalledWith("has_site", false);
    expect(screen.getByText("Enter an https URL.")).toBeVisible();
  });
  it("disables every answer control while a submitted form is read only", () => {
    render(<QuestionFields fields={fields} answers={{ has_site: true, website: "https://example.test" }} update={vi.fn()} disabled />);
    expect(screen.getByLabelText(/Existing website/)).toBeDisabled();
    expect(screen.getByLabelText(/Website URL/)).toBeDisabled();
    expect(screen.getByLabelText(/Details/)).toBeDisabled();
  });
});
