import type { ApiResponse } from "@/types/api";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function csrfHeader(): Promise<Record<string, string>> {
  const response = await fetch(`${apiBaseUrl}/api/v1/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" },
  });
  const payload = (await response.json()) as ApiResponse<{ headerName: string; token: string }>;
  if (!response.ok || !payload.success) {
    throw new ApiClientError("Security token initialization failed.", response.status, "CSRF_UNAVAILABLE");
  }
  return { [payload.data.headerName]: payload.data.token };
}

export class ApiClientError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly requestId?: string,
    readonly fieldErrors: import("@/types/api").FieldError[] = [],
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

export async function apiRequest<T>(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const csrf = ["POST", "PUT", "PATCH", "DELETE"].includes(method) ? await csrfHeader() : {};
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...csrf,
      ...init.headers,
    },
  });

  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    const failure = payload.success ? undefined : payload;
    throw new ApiClientError(
      failure?.error.message ?? "The request could not be completed.",
      response.status,
      failure?.error.code ?? "HTTP_ERROR",
      failure?.requestId,
      failure?.error.fieldErrors,
    );
  }

  return payload.data;
}
