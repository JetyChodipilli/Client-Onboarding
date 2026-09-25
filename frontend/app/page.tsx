import {
  ArrowRight,
  Blocks,
  CheckCircle2,
  CircleDot,
  Database,
  FileCode2,
  Gauge,
  GitBranch,
  LockKeyhole,
  Network,
  ShieldCheck,
} from "lucide-react";
import { MotionShell } from "@/components/shared/motion-shell";
import { ThemeToggle } from "@/components/shared/theme-toggle";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";

const capabilities = [
  {
    icon: Blocks,
    title: "Modular by domain",
    description:
      "Explicit ownership prevents cross-module table writes and keeps future extraction practical.",
  },
  {
    icon: ShieldCheck,
    title: "Secure by boundary",
    description:
      "Authentication, tenant context, permission and resource relationships are checked in order.",
  },
  {
    icon: Database,
    title: "PostgreSQL authority",
    description:
      "Flyway migrations, durable constraints and transactional outbox decisions start at foundation.",
  },
];

const lifecycleBoundaries = [
  "Project lifecycle",
  "Onboarding lifecycle",
  "Step lifecycle",
  "Financial and legal states",
];

export default function Home() {
  return (
    <MotionShell>
      <a
        href="#main-content"
        className="fixed left-3 top-3 z-50 -translate-y-24 rounded-md bg-foreground px-4 py-3 text-sm font-semibold text-background transition-transform focus:translate-y-0"
      >
        Skip to main content
      </a>

      <div className="relative min-h-dvh w-full max-w-full overflow-x-hidden">
        <div aria-hidden="true" className="ambient-grid pointer-events-none absolute inset-x-0 top-0 h-[52rem]" />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-32 top-32 size-[28rem] rounded-full bg-primary/12 blur-3xl"
        />

        <header className="page-shell relative z-10 flex min-h-20 items-center justify-between py-4">
          <a
            href="#main-content"
            className="group inline-flex min-h-11 items-center gap-3 rounded-md font-semibold tracking-tight"
            aria-label="Client Onboarding Platform home"
          >
            <span className="grid size-10 place-items-center rounded-lg bg-primary text-primary-foreground shadow-sm transition-transform duration-200 group-hover:-translate-y-0.5">
              <Network aria-hidden="true" className="size-5" />
            </span>
            <span className="hidden sm:inline">Client Onboarding</span>
          </a>
          <nav aria-label="Primary" className="flex items-center gap-1 sm:gap-3">
            <a
              href="#architecture"
              className="inline-flex min-h-11 items-center rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              Architecture
            </a>
            <a
              href="/login"
              className="hidden min-h-11 items-center rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground sm:inline-flex"
            >
              Sign in
            </a>
            <ThemeToggle />
          </nav>
        </header>

        <main id="main-content" tabIndex={-1}>
          <section className="page-shell relative z-10 grid min-h-[calc(100dvh-5rem)] items-center gap-14 py-16 lg:grid-cols-12 lg:py-24">
            <div data-hero className="lg:col-span-7">
              <Badge tone="info">
                <CircleDot aria-hidden="true" className="size-3" />
                Workflow foundation verified
              </Badge>
              <h1 className="mt-7 max-w-5xl text-balance text-[clamp(3rem,6.2vw,6.4rem)] font-bold leading-[0.98] tracking-[-0.055em]">
                Make every client start feel clear.
              </h1>
              <p className="mt-7 max-w-2xl text-pretty text-lg leading-8 text-muted-foreground sm:text-xl">
                A secure, configurable foundation for guiding clients from agreement to a project that is genuinely ready for delivery.
              </p>
              <div className="mt-9 flex flex-col gap-3 sm:flex-row">
                <a
                  href="/login"
                  className={cn(buttonVariants({ variant: "accent", size: "lg" }), "group")}
                >
                  Sign in securely
                  <ArrowRight
                    aria-hidden="true"
                    className="transition-transform duration-200 group-hover:translate-x-1"
                  />
                </a>
                <a
                  href="/app"
                  className={buttonVariants({ variant: "outline", size: "lg" })}
                >
                  Open workspace
                </a>
              </div>
            </div>

            <div data-hero className="lg:col-span-5 lg:pl-8">
              <Card className="relative overflow-hidden p-0">
                <div className="border-b bg-muted/65 px-6 py-5">
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <p className="text-sm font-semibold">Platform status</p>
                      <p className="mt-1 text-sm text-muted-foreground">Phase 6 boundary</p>
                    </div>
                    <Badge tone="success">
                      <CheckCircle2 aria-hidden="true" className="size-3.5" />
                      Established
                    </Badge>
                  </div>
                </div>
                <div className="space-y-7 p-6">
                  <div>
                    <div className="flex items-end justify-between gap-4">
                      <span className="text-sm font-medium">Foundation through client portal</span>
                      <span className="font-mono text-sm tabular-nums">100%</span>
                    </div>
                    <div className="mt-3 h-2 overflow-hidden rounded-full bg-muted">
                      <div data-progress-line className="h-full w-full rounded-full bg-success" />
                    </div>
                  </div>
                  <dl className="space-y-4">
                    <div className="flex items-center justify-between gap-5 border-b pb-4">
                      <dt className="text-sm text-muted-foreground">Architecture</dt>
                      <dd className="text-right text-sm font-semibold">Modular monolith</dd>
                    </div>
                    <div className="flex items-center justify-between gap-5 border-b pb-4">
                      <dt className="text-sm text-muted-foreground">Data authority</dt>
                      <dd className="text-right text-sm font-semibold">PostgreSQL + Flyway</dd>
                    </div>
                    <div className="flex items-center justify-between gap-5">
                      <dt className="text-sm text-muted-foreground">Client portal</dt>
                      <dd className="text-right text-sm font-semibold">Phase 6 implemented</dd>
                    </div>
                  </dl>
                </div>
              </Card>
            </div>
          </section>

          <section id="architecture" className="section-space page-shell relative z-10 scroll-mt-12">
            <div data-reveal>
              <div className="max-w-3xl">
                <p className="text-sm font-semibold uppercase tracking-[0.16em] text-primary">
                  Built for controlled growth
                </p>
                <h2 className="mt-4 text-balance text-4xl font-bold tracking-[-0.04em] sm:text-5xl">
                  Strong boundaries before complex workflows.
                </h2>
                <p className="mt-5 max-w-2xl text-lg text-muted-foreground">
                  The foundation makes security, state ownership and delivery reliability structural concerns instead of late fixes.
                </p>
              </div>

              <div className="mt-12 grid grid-flow-dense gap-4 lg:grid-cols-12">
                <Card className="group relative overflow-hidden lg:col-span-7 lg:min-h-80">
                  <div className="absolute inset-x-0 top-0 h-1 bg-primary" />
                  <GitBranch aria-hidden="true" className="size-8 text-primary" />
                  <h3 className="mt-8 max-w-xl text-3xl font-bold tracking-[-0.035em]">
                    One deployable. Explicit domain ownership.
                  </h3>
                  <p className="mt-4 max-w-xl text-muted-foreground">
                    Workflow capabilities connect through application ports and handler strategies—not through shared tables or scattered type conditionals.
                  </p>
                  <div className="mt-10 flex flex-wrap gap-2" aria-label="Architecture qualities">
                    <Badge>Controlled dependencies</Badge>
                    <Badge>Transaction clarity</Badge>
                    <Badge>Extraction-ready</Badge>
                  </div>
                </Card>

                <Card className="group flex flex-col justify-between bg-foreground text-background lg:col-span-5 lg:min-h-80">
                  <LockKeyhole aria-hidden="true" className="size-8 text-background/80" />
                  <div>
                    <p className="text-3xl font-bold tracking-[-0.035em]">Tenant context is mandatory.</p>
                    <p className="mt-4 text-background/75">
                      Every future tenant resource is scoped at authorization, application and query layers, with cross-tenant tests as a release gate.
                    </p>
                  </div>
                </Card>

                {capabilities.map(({ icon: Icon, title, description }) => (
                  <Card key={title} className="group lg:col-span-4">
                    <Icon aria-hidden="true" className="size-6 text-primary" />
                    <h3 className="mt-7 text-xl font-bold tracking-[-0.025em]">{title}</h3>
                    <p className="mt-3 text-sm leading-6 text-muted-foreground">{description}</p>
                  </Card>
                ))}
              </div>
            </div>
          </section>

          <section className="section-space page-shell relative z-10">
            <div data-reveal className="grid gap-14 lg:grid-cols-12 lg:items-start">
              <div className="lg:sticky lg:top-24 lg:col-span-5">
                <FileCode2 aria-hidden="true" className="size-8 text-primary" />
                <h2 className="mt-7 max-w-xl text-balance text-4xl font-bold tracking-[-0.04em] sm:text-5xl">
                  Independent states. Reliable readiness.
                </h2>
                <p className="mt-5 max-w-xl text-lg text-muted-foreground">
                  Payment or signature events can complete workflow steps, but they never become project lifecycle values.
                </p>
              </div>

              <div className="space-y-4 lg:col-span-7">
                {lifecycleBoundaries.map((item, index) => (
                  <div
                    key={item}
                    className="glass-surface flex min-h-24 items-center gap-5 rounded-xl p-5 sm:p-6"
                  >
                    <span className="grid size-10 shrink-0 place-items-center rounded-full bg-secondary font-mono text-sm font-semibold tabular-nums text-secondary-foreground">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold">{item}</p>
                      <p className="mt-1 text-sm text-muted-foreground">Owned and validated separately</p>
                    </div>
                    <Gauge aria-hidden="true" className="hidden size-5 shrink-0 text-muted-foreground sm:block" />
                  </div>
                ))}
                <div className="rounded-xl border border-primary/25 bg-primary/8 p-6">
                  <p className="font-semibold">Readiness rule</p>
                  <p className="mt-2 text-sm leading-6 text-muted-foreground">
                    Ready only when every applicable blocking step is complete. Optional and non-blocking work cannot accidentally prevent activation.
                  </p>
                </div>
              </div>
            </div>
          </section>

          <section className="page-shell relative z-10 pb-24 pt-8 sm:pb-32">
            <div className="overflow-hidden rounded-xl bg-primary p-8 text-primary-foreground shadow-2xl sm:p-12 lg:p-16">
              <div className="max-w-4xl">
                <p className="text-sm font-semibold uppercase tracking-[0.16em] text-primary-foreground/75">
                  Phase discipline
                </p>
                <h2 className="mt-5 text-balance text-4xl font-bold tracking-[-0.04em] sm:text-5xl lg:text-6xl">
                  Versioned workflows are ready for controlled onboarding.
                </h2>
                <p className="mt-6 max-w-2xl text-lg text-primary-foreground/80">
                  Identity, client/project core, workflow snapshots, the client portal, questionnaires and secure file collection are implemented.
                </p>
                <a
                  href="/login"
                  className={cn(
                    buttonVariants({ variant: "secondary", size: "lg" }),
                    "mt-9 bg-primary-foreground text-primary hover:bg-primary-foreground/90",
                  )}
                >
                  Sign in to workspace
                  <ArrowRight aria-hidden="true" />
                </a>
              </div>
            </div>
          </section>
        </main>

        <footer className="border-t bg-card/45">
          <div className="page-shell flex flex-col gap-3 py-8 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <p>Client Onboarding Platform</p>
            <p>Phase 6 · Asset Management</p>
          </div>
        </footer>
      </div>
    </MotionShell>
  );
}
