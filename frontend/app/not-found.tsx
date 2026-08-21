import Link from "next/link";

export default function NotFound() {
  return (
    <main className="grid min-h-screen place-items-center px-5 py-12">
      <section className="w-full max-w-lg rounded-2xl border bg-[hsl(var(--surface))] p-6 shadow-[var(--shadow-card)] sm:p-8">
        <p className="text-sm font-semibold text-[hsl(var(--info))]">404</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">Page not found</h1>
        <p className="mt-3 text-sm leading-6 text-[hsl(var(--muted-foreground))]">
          The requested route does not exist in the current implementation phase.
        </p>
        <Link className="mt-6 inline-block text-sm font-semibold text-[hsl(var(--primary))] underline-offset-4 hover:underline" href="/">
          Return to foundation status
        </Link>
      </section>
    </main>
  );
}
