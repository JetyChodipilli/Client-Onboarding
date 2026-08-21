import { cn } from "@/lib/utils";
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-lg bg-[hsl(var(--surface-subtle))]", className)} aria-hidden="true" />;
}
