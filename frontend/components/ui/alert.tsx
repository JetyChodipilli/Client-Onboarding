import { AlertCircle } from "lucide-react";
export function Alert({ children }: { children: React.ReactNode }) {
  return <div role="alert" className="flex gap-3 rounded-xl border border-[hsl(var(--danger)/0.22)] bg-[hsl(var(--danger-soft))] p-3.5 text-sm text-[hsl(var(--danger))]">
    <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
    <div>{children}</div>
  </div>;
}
