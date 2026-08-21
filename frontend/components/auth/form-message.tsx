import { AlertCircle, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function FormMessage({ tone = "error", children }: { tone?: "error" | "success" | "info"; children: React.ReactNode }) {
  const Icon = tone === "success" ? CheckCircle2 : AlertCircle;
  return (
    <div role={tone === "error" ? "alert" : "status"} className={cn(
      "flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm leading-5",
      tone === "error" && "border-[hsl(var(--danger)/.22)] bg-[hsl(var(--danger-soft))] text-[hsl(var(--danger))]",
      tone === "success" && "border-[hsl(var(--success)/.2)] bg-[hsl(var(--success-soft))] text-[hsl(var(--success))]",
      tone === "info" && "border-[hsl(var(--info)/.2)] bg-[hsl(var(--info-soft))] text-[hsl(var(--info))]",
    )}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}
