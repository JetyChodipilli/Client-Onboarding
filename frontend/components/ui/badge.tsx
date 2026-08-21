import type { ComponentProps } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva("inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold", {
  variants: {
    variant: {
      neutral: "bg-white text-[hsl(var(--foreground))]",
      success: "border-[hsl(var(--success)/.18)] bg-[hsl(var(--success-soft))] text-[hsl(var(--success))]",
      warning: "border-[hsl(var(--warning)/.18)] bg-[hsl(var(--warning-soft))] text-[hsl(var(--warning))]",
      info: "border-[hsl(var(--info)/.18)] bg-[hsl(var(--info-soft))] text-[hsl(var(--info))]",
      danger: "border-[hsl(var(--danger)/.18)] bg-[hsl(var(--danger-soft))] text-[hsl(var(--danger))]",
    },
  },
  defaultVariants: { variant: "neutral" },
});

export function Badge({ className, variant, ...props }: ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
