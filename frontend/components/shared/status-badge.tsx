import { Badge } from "@/components/ui/badge";

type Variant = "neutral" | "success" | "warning" | "info" | "danger";

const variants: Record<string, Variant> = {
  ACTIVE: "success",
  COMPLETED: "success",
  READY: "success",
  PROSPECT: "info",
  DRAFT: "neutral",
  ONBOARDING: "info",
  ON_HOLD: "warning",
  INACTIVE: "warning",
  CANCELLED: "danger",
  ARCHIVED: "neutral",
  PUBLISHED: "success",
  INVITED: "info",
  IN_PROGRESS: "info",
  AWAITING_INTERNAL_REVIEW: "warning",
  NEEDS_REVISION: "warning",
  APPROVED: "success",
  PAUSED: "warning",
  EXPIRED: "danger",
  LOCKED: "neutral",
  AVAILABLE: "info",
  SUBMITTED: "info",
  UNDER_REVIEW: "warning",
  SKIPPED: "neutral",
  FAILED: "danger",
};

export function StatusBadge({ status }: { status: string }) {
  return <Badge variant={variants[status] ?? "neutral"}>{status.replaceAll("_", " ")}</Badge>;
}
