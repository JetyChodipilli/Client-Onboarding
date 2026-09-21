import { afterEach, describe, expect, it, vi } from "vitest";
import { operationsApi } from "./operations-api";
import type { WorkflowStep } from "./types";

const response = (body: unknown, ok = true, status = 200) => Promise.resolve({
  ok,
  status,
  json: async () => body,
}) as Promise<Response>;
const csrf = { success: true, data: { headerName: "X-XSRF-TOKEN", token: "csrf-token" }, meta: {}, requestId: "req" };

describe("operationsApi", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends onboarding idempotency and CSRF headers", async () => {
    const fetch = vi.fn()
      .mockImplementationOnce(() => response(csrf))
      .mockImplementationOnce(() => response({ success: true, data: { onboarding: { id: "onboarding" }, steps: [], progress: 0 }, meta: {}, requestId: "req" }));
    vi.stubGlobal("fetch", fetch);

    await operationsApi.startOnboarding("project", "version", 3, "start-project-123");

    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fetch.mock.calls[1]?.[0]).toBe("http://localhost:8080/api/v1/projects/project/onboarding");
    expect(fetch.mock.calls[1]?.[1]).toMatchObject({
      method: "POST",
      headers: expect.objectContaining({
        "Idempotency-Key": "start-project-123",
        "X-XSRF-TOKEN": "csrf-token",
      }),
    });
    expect(JSON.parse(String(fetch.mock.calls[1]?.[1]?.body))).toEqual({
      templateVersionId: "version",
      projectVersion: 3,
    });
  });

  it("preserves dependency and condition configuration when saving a draft", async () => {
    const step: WorkflowStep = {
      id: "step-2",
      stepKey: "LEGAL_REVIEW",
      name: "Legal review",
      stepType: "APPROVAL",
      displayOrder: 1,
      required: true,
      blocking: true,
      clientVisible: false,
      requiresReview: true,
      dependencyMode: "ALL",
      dependencyStepIds: ["step-1"],
      condition: { field: "PROJECT_VALUE_MINOR", operator: "GREATER_THAN", value: "500000" },
      allowSkip: false,
      allowReopen: true,
      configuration: {},
      version: 0,
    };
    const fetch = vi.fn()
      .mockImplementationOnce(() => response(csrf))
      .mockImplementationOnce(() => response({ success: true, data: { version: { id: "v1" }, steps: [step] }, meta: {}, requestId: "req" }));
    vi.stubGlobal("fetch", fetch);

    await operationsApi.replaceSteps("v1", 4, [step]);

    const body = JSON.parse(String(fetch.mock.calls[1]?.[1]?.body));
    expect(body.steps[0]).toMatchObject({
      dependencyMode: "ALL",
      dependencyStepIds: ["step-1"],
      condition: { field: "PROJECT_VALUE_MINOR", operator: "GREATER_THAN", value: "500000" },
      blocking: true,
    });
    expect(body.version).toBe(4);
  });
});
