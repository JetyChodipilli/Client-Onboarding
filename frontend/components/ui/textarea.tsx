import * as React from "react";
import { cn } from "@/lib/utils";

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.ComponentProps<"textarea">>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn(
        "min-h-28 w-full resize-y rounded-xl border bg-white px-3.5 py-3 text-sm text-[hsl(var(--foreground))] shadow-xs outline-none transition",
        "placeholder:text-[hsl(var(--muted-foreground))] focus:border-[hsl(var(--primary))] focus:ring-4 focus:ring-[hsl(var(--primary)/0.10)]",
        "disabled:cursor-not-allowed disabled:bg-[hsl(var(--surface-subtle))] disabled:opacity-70",
        className,
      )}
      {...props}
    />
  ),
);
Textarea.displayName = "Textarea";
