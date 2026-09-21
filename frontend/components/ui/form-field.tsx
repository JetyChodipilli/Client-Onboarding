import type * as React from "react";

export function FormField({ label, htmlFor, hint, error, children }: {
  label: string;
  htmlFor: string;
  hint?: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-2 block text-sm font-semibold" htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && <p id={`${htmlFor}-hint`} className="mt-2 text-sm text-muted-foreground">{hint}</p>}
      {error && <p id={`${htmlFor}-error`} role="alert" className="mt-2 text-sm font-medium text-danger">{error}</p>}
    </div>
  );
}
