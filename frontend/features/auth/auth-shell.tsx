import { Fingerprint, LockKeyhole, Network, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { ThemeToggle } from "@/components/shared/theme-toggle";

export function AuthShell({ eyebrow, title, description, children }: {
  eyebrow: string;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <main className="relative min-h-dvh overflow-hidden bg-background">
      <a href="#auth-form" className="fixed left-3 top-3 z-50 -translate-y-24 rounded-md bg-foreground px-4 py-3 text-sm font-semibold text-background transition-transform focus:translate-y-0">
        Skip to form
      </a>
      <div aria-hidden="true" className="ambient-grid pointer-events-none absolute inset-0" />
      <div className="page-shell relative z-10 flex min-h-dvh flex-col py-5 sm:py-8">
        <header className="flex items-center justify-between">
          <Link href="/" className="inline-flex min-h-11 items-center gap-3 rounded-md font-semibold" aria-label="Client Onboarding home">
            <span className="grid size-10 place-items-center rounded-lg bg-primary text-primary-foreground"><Network aria-hidden="true" className="size-5" /></span>
            <span className="hidden sm:inline">Client Onboarding</span>
          </Link>
          <ThemeToggle />
        </header>

        <div className="my-auto grid gap-10 py-12 lg:grid-cols-[minmax(0,1fr)_minmax(22rem,30rem)] lg:items-center lg:gap-20">
          <section className="max-w-2xl lg:pr-8">
            <p className="text-sm font-bold uppercase tracking-[0.16em] text-primary">{eyebrow}</p>
            <h1 className="mt-4 text-balance text-4xl font-bold tracking-[-0.045em] sm:text-5xl">{title}</h1>
            <p className="mt-5 max-w-xl text-lg leading-8 text-muted-foreground">{description}</p>
            <ul className="mt-10 hidden space-y-5 lg:block" aria-label="Security assurances">
              <li className="flex gap-3"><ShieldCheck aria-hidden="true" className="mt-0.5 size-5 text-success" /><span><strong className="block text-sm">Tenant-aware access</strong><span className="text-sm text-muted-foreground">Every request stays inside your organization boundary.</span></span></li>
              <li className="flex gap-3"><Fingerprint aria-hidden="true" className="mt-0.5 size-5 text-primary" /><span><strong className="block text-sm">Extra verification for privileged access</strong><span className="text-sm text-muted-foreground">Sensitive permissions require a second factor.</span></span></li>
              <li className="flex gap-3"><LockKeyhole aria-hidden="true" className="mt-0.5 size-5 text-primary" /><span><strong className="block text-sm">Revocable sessions</strong><span className="text-sm text-muted-foreground">Credential changes invalidate existing access.</span></span></li>
            </ul>
          </section>
          <section id="auth-form" tabIndex={-1} className="glass-surface rounded-xl p-5 sm:p-8">{children}</section>
        </div>
        <footer className="text-center text-sm text-muted-foreground lg:text-left">Protected access · Need help? Contact your organization administrator.</footer>
      </div>
    </main>
  );
}
