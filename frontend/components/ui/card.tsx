import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";
export function Card({ className, ...props }: ComponentProps<"section">) {
  return <section className={cn("rounded-[var(--radius-lg)] border bg-white shadow-[var(--shadow-card)]", className)} {...props} />;
}
