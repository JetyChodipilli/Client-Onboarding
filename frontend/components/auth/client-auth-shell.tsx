import { ArrowRight, CheckCircle2, LockKeyhole, ShieldCheck } from "lucide-react";

export function ClientAuthShell({ eyebrow, title, description, children }: {
  eyebrow: string; title: string; description: string; children: React.ReactNode;
}) {
  return <main className="min-h-screen bg-[hsl(var(--background))] lg:grid lg:grid-cols-[minmax(0,1.08fr)_minmax(28rem,.92fr)]">
    <section className="relative hidden overflow-hidden border-r bg-[hsl(var(--ink))] px-12 py-12 text-white lg:flex lg:flex-col lg:justify-between">
      <div className="absolute inset-0 opacity-90 [background:radial-gradient(circle_at_15%_10%,hsl(var(--primary)/.32),transparent_28rem),radial-gradient(circle_at_82%_78%,hsl(var(--info)/.16),transparent_25rem)]" />
      <div className="relative flex items-center gap-3"><div className="grid size-10 place-items-center rounded-xl bg-white text-sm font-black text-[hsl(var(--ink))]">CO</div><div><p className="font-semibold tracking-tight">Client workspace</p><p className="text-xs text-white/55">A clear path from kickoff to ready</p></div></div>
      <div className="relative max-w-xl pb-10">
        <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3 py-1.5 text-xs font-semibold text-white/80"><ShieldCheck className="size-4"/>Secure project access</div>
        <h2 className="text-balance text-4xl font-semibold leading-[1.08] tracking-[-.045em] xl:text-5xl">Know what needs your attention—without chasing email threads.</h2>
        <div className="mt-8 grid gap-3 text-sm text-white/68">
          <Promise icon={ArrowRight}>One clear next action</Promise><Promise icon={CheckCircle2}>Live onboarding progress</Promise><Promise icon={LockKeyhole}>Project-scoped secure access</Promise>
        </div>
      </div>
      <p className="relative text-xs text-white/45">Secure invitation · Project-scoped authorization · Auditable access</p>
    </section>
    <section className="flex min-h-screen items-center justify-center px-5 py-10 sm:px-8"><div className="w-full max-w-md"><div className="mb-8 lg:hidden"><div className="grid size-10 place-items-center rounded-xl bg-[hsl(var(--ink))] text-sm font-black text-white">CO</div></div><p className="text-xs font-bold uppercase tracking-[.18em] text-[hsl(var(--primary))]">{eyebrow}</p><h1 className="mt-3 text-balance text-3xl font-semibold tracking-[-.035em] sm:text-4xl">{title}</h1><p className="mt-3 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{description}</p><div className="mt-8">{children}</div></div></section>
  </main>;
}
function Promise({icon:Icon,children}:{icon:typeof ArrowRight;children:React.ReactNode}){return <div className="flex items-center gap-3"><div className="grid size-8 place-items-center rounded-lg border border-white/10 bg-white/5"><Icon className="size-4"/></div><span>{children}</span></div>}
