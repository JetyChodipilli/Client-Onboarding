import * as React from "react";
import { cn } from "@/lib/utils";

export const Select = React.forwardRef<HTMLSelectElement, React.ComponentProps<"select">>(
  ({ className, children, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        "h-11 w-full rounded-xl border bg-white px-3.5 text-sm text-[hsl(var(--foreground))] shadow-xs outline-none transition",
        "focus:border-[hsl(var(--primary))] focus:ring-4 focus:ring-[hsl(var(--primary)/0.10)]",
        "disabled:cursor-not-allowed disabled:bg-[hsl(var(--surface-subtle))] disabled:opacity-70",
        className,
      )}
      {...props}
    >
      {children}
    </select>
  ),
);
Select.displayName = "Select";
