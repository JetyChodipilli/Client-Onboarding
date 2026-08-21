import { ShieldCheck } from "lucide-react";

export function AuthShell({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <main className="min-h-screen bg-[hsl(var(--background))] lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(28rem,0.95fr)]">
      <section className="relative hidden overflow-hidden border-r bg-[hsl(var(--ink))] px-12 py-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute inset-0 opacity-80 [background:radial-gradient(circle_at_20%_10%,hsl(var(--primary)/.35),transparent_30rem),radial-gradient(circle_at_80%_80%,hsl(var(--info)/.18),transparent_26rem)]" />
        <div className="relative flex items-center gap-3">
          <div className="grid size-10 place-items-center rounded-xl bg-white text-sm font-black text-[hsl(var(--ink))]">CO</div>
          <div>
            <p className="font-semibold tracking-tight">Client Onboarding</p>
            <p className="text-xs text-white/55">Relationship operations</p>
          </div>
        </div>
        <div className="relative max-w-xl pb-12">
          <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3 py-1.5 text-xs font-semibold text-white/80">
            <ShieldCheck className="size-4" aria-hidden="true" />
            Tenant-safe by design
          </div>
          <p className="text-balance text-4xl font-semibold leading-[1.08] tracking-[-0.04em] xl:text-5xl">
            One secure workspace for every client relationship.
          </p>
          <p className="mt-5 max-w-lg text-base leading-7 text-white/60">
            Identity, role permissions, and workspace isolation are enforced on the server—not left to the interface.
          </p>
        </div>
        <p className="relative text-xs text-white/45">Secure identity · Permission-based access · Tenant isolation</p>
      </section>

      <section className="flex min-h-screen items-center justify-center px-5 py-10 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden">
            <div className="mb-7 grid size-10 place-items-center rounded-xl bg-[hsl(var(--ink))] text-sm font-black text-white">CO</div>
          </div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-[hsl(var(--primary))]">{eyebrow}</p>
          <h1 className="mt-3 text-balance text-3xl font-semibold tracking-[-0.035em] text-[hsl(var(--foreground))] sm:text-4xl">{title}</h1>
          <p className="mt-3 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{description}</p>
          <div className="mt-8">{children}</div>
        </div>
      </section>
    </main>
  );
}
