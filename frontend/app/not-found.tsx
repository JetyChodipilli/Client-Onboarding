import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="page-shell grid min-h-dvh place-items-center py-16">
      <section className="max-w-xl text-center">
        <p className="font-mono text-sm text-muted-foreground">404</p>
        <h1 className="mt-4 text-4xl font-bold tracking-tight">That page is not available.</h1>
        <p className="mt-4 text-muted-foreground">
          Check the address or return to the foundation overview.
        </p>
        <Link href="/" className={`${buttonVariants({ variant: "outline" })} mt-8`}>
          <ArrowLeft aria-hidden="true" />
          Return home
        </Link>
      </section>
    </main>
  );
}

