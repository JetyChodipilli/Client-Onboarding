import { apiRequest } from "@/lib/api-client";

export type PlatformInfo = {
  name: string;
  phase: "PHASE_3";
  architecture: "MODULAR_MONOLITH";
  healthEndpoints: Record<string, string>;
};

export function getPlatformInfo() {
  return apiRequest<PlatformInfo>("/api/v1/platform/info", {
    cache: "no-store",
  });
}
