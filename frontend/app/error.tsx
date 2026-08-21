"use client";

import { Button } from "@/components/ui/button";

export default function Error({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="grid min-h-screen place-items-center px-5 py-12">
      <section className="w-full max-w-lg rounded-2xl border bg-[hsl(var(--surface))] p-6 shadow-[var(--shadow-card)] sm:p-8">
        <p className="text-sm font-semibold text-[hsl(var(--danger))]">Unable to load this page</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">Something went wrong.</h1>
        <p className="mt-3 text-sm leading-6 text-[hsl(var(--muted-foreground))]">
          Try the request again. If the problem continues, keep the request ID from the API response for support.
        </p>
        <Button className="mt-6" onClick={reset}>Try again</Button>
      </section>
    </main>
  );
}
