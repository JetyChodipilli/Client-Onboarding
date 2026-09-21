"use client";

import { AlertTriangle, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";

export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="page-shell grid min-h-dvh place-items-center py-16">
      <section className="glass-surface max-w-xl rounded-xl p-8 text-center sm:p-12">
        <AlertTriangle aria-hidden="true" className="mx-auto size-9 text-danger" />
        <h1 className="mt-6 text-3xl font-bold tracking-tight">This page could not be loaded.</h1>
        <p className="mt-4 text-muted-foreground">
          Retry the page. If the problem continues, share the request time with your support contact.
        </p>
        <Button className="mt-8" onClick={reset}>
          <RotateCcw aria-hidden="true" />
          Try again
        </Button>
      </section>
    </main>
  );
}

