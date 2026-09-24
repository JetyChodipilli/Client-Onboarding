import { describe, expect, it } from "vitest";
import { validateFile, type AssetRequirement } from "./types";
const requirement: AssetRequirement = { id: "asset", name: "Brand document", allowedMimes: ["text/plain"], maxBytes: 1000, version: 0 };
describe("asset file validation", () => {
  it("rejects empty, oversized and active-content files before requesting an upload", () => {
    expect(validateFile({ size: 0, type: "text/plain" }, requirement)).toContain("non-empty");
    expect(validateFile({ size: 1001, type: "text/plain" }, requirement)).toContain("no larger");
    expect(validateFile({ size: 10, type: "text/html" }, requirement)).toContain("not permitted");
    expect(validateFile({ size: 10, type: "image/svg+xml" }, requirement)).toContain("not permitted");
  });
  it("accepts a supported file at the configured size boundary", () => {
    expect(validateFile({ size: 1000, type: "text/plain" }, requirement)).toBe("");
  });
});
