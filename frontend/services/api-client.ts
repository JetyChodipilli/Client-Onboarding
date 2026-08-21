import { publicEnv } from "@/lib/env";
import type { ApiResponse, ApiResult } from "@/types/api";

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
    public readonly fieldErrors: Array<{ field: string; message: string }> = [],
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

async function requestEnvelope<T, M = Record<string, unknown>>(
  path: string,
  init: RequestInit = {},
  accessToken?: string | null,
): Promise<ApiResult<T, M>> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const response = await fetch(`${publicEnv.NEXT_PUBLIC_API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  let envelope: ApiResponse<T, M>;
  try {
    envelope = (await response.json()) as ApiResponse<T, M>;
  } catch {
    throw new ApiClientError("The server returned an unreadable response.", response.status, "INVALID_RESPONSE");
  }

  // Narrow the discriminated union before touching error fields. This also preserves
  // the backend's structured domain error even when the HTTP status is non-2xx.
  if (!envelope.success) {
    throw new ApiClientError(
      envelope.error.message ?? "The request could not be completed.",
      response.status,
      envelope.error.code ?? "REQUEST_FAILED",
      envelope.error.fieldErrors ?? [],
    );
  }

  // A success envelope paired with a non-success HTTP status is a protocol violation.
  if (!response.ok) {
    throw new ApiClientError("The server returned an inconsistent response.", response.status, "INVALID_RESPONSE");
  }

  return { data: envelope.data, meta: envelope.meta, requestId: envelope.requestId };
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  accessToken?: string | null,
): Promise<T> {
  return (await requestEnvelope<T>(path, init, accessToken)).data;
}

export async function apiRequestWithMeta<T, M = Record<string, unknown>>(
  path: string,
  init: RequestInit = {},
  accessToken?: string | null,
): Promise<ApiResult<T, M>> {
  return requestEnvelope<T, M>(path, init, accessToken);
}
