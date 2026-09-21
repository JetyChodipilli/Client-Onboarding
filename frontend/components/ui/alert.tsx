import { AlertCircle, CheckCircle2, Info } from "lucide-react";
import type * as React from "react";
import { cn } from "@/lib/utils";

const icons = { error: AlertCircle, success: CheckCircle2, info: Info };

export function Alert({ tone = "info", className, children, ...props }: React.HTMLAttributes<HTMLDivElement> & {
  tone?: keyof typeof icons;
}) {
  const Icon = icons[tone];
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={cn(
        "flex gap-3 rounded-lg border p-4 text-sm",
        tone === "error" && "border-danger/30 bg-danger/8 text-foreground",
        tone === "success" && "border-success/30 bg-success/8 text-foreground",
        tone === "info" && "border-info/30 bg-info/8 text-foreground",
        className,
      )}
      {...props}
    >
      <Icon aria-hidden="true" className={cn("mt-0.5 size-4 shrink-0", tone === "error" && "text-danger", tone === "success" && "text-success", tone === "info" && "text-info")} />
      <div className="min-w-0">{children}</div>
    </div>
  );
}
