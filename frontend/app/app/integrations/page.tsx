"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { CheckCircle2, CircleAlert, Loader2, PlugZap, RefreshCw, ShieldCheck } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { PageHeading } from "@/components/layout/page-heading";
import { EmptyState } from "@/components/shared/empty-state";
import { PermissionState } from "@/components/shared/permission-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/services/api-client";
import type { IntegrationState, IntegrationStatusItem, IntegrationStatusResponse } from "@/types/integrations";

const variant: Record<IntegrationState, "success" | "warning" | "danger" | "neutral"> = {
  READY: "success",
  DEVELOPMENT: "warning",
  UNAVAILABLE: "danger",
  DISABLED: "neutral",
};

export default function IntegrationsPage() {
  const { authorizedRequest, hasPermission } = useAuth();
  const canRead = hasPermission("ORG_READ") || hasPermission("ORG_UPDATE");
  const [items, setItems] = useState<IntegrationStatusItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await authorizedRequest<IntegrationStatusResponse>("/integrations");
      setItems(response.integrations);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Integration health could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [authorizedRequest]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  const metrics = useMemo(() => ({
    ready: items.filter((item) => item.productionReady).length,
    attention: items.filter((item) => item.enabled && !item.productionReady).length,
    disabled: items.filter((item) => !item.enabled).length,
  }), [items]);

  if (!canRead) return <PermissionState title="Integration access is restricted" description="Organization read or update permission is required to inspect integration readiness." />;

  return <div className="page-enter">
    <PageHeading
      eyebrow="Administration"
      title="Integrations"
      description="A credential-safe readiness view for the external systems this onboarding platform depends on. Secret values are intentionally never returned to the browser."
      actions={<Button variant="secondary" onClick={() => void load()} disabled={loading}><RefreshCw className={loading ? "size-4 animate-spin" : "size-4"}/>Refresh</Button>}
    />

    <div className="mb-6 grid gap-4 sm:grid-cols-3">
      <Metric label="Production ready" value={metrics.ready} icon={CheckCircle2}/>
      <Metric label="Needs attention" value={metrics.attention} icon={CircleAlert}/>
      <Metric label="Disabled" value={metrics.disabled} icon={PlugZap}/>
    </div>

    <section className="panel mb-6 flex items-start gap-3 p-5 sm:p-6">
      <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-[hsl(var(--primary-soft))] text-[hsl(var(--primary))]"><ShieldCheck className="size-5"/></div>
      <div>
        <h2 className="text-sm font-semibold">Deployment safety, not secret management</h2>
        <p className="mt-1 text-sm leading-6 text-[hsl(var(--muted-foreground))]">This screen reports whether an adapter or capability is configured and production-ready. Provider keys, SMTP credentials, storage secrets and webhook secrets remain server-side deployment configuration.</p>
      </div>
    </section>

    {loading ? <Loading/> : error ? <div className="panel border-[hsl(var(--danger)/.25)] p-6"><p className="text-sm font-medium text-[hsl(var(--danger))]">{error}</p><Button className="mt-4" size="sm" variant="secondary" onClick={() => void load()}>Try again</Button></div> : items.length === 0 ? <EmptyState title="No integration capabilities reported" description="The backend did not expose any configured integration capabilities."/> : <div className="grid gap-4 xl:grid-cols-2">{items.map((item) => <IntegrationCard key={item.key} item={item}/>)}</div>}
  </div>;
}

function IntegrationCard({ item }: { item: IntegrationStatusItem }) {
  return <article className="panel interactive-card p-5 sm:p-6">
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0"><p className="text-xs font-semibold uppercase tracking-[.13em] text-[hsl(var(--muted-foreground))]">{item.category}</p><h2 className="mt-1.5 text-lg font-semibold tracking-[-.02em]">{item.name}</h2></div>
      <Badge variant={variant[item.status]}>{item.status}</Badge>
    </div>
    <p className="mt-4 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{item.message}</p>
    <div className="mt-5 flex flex-wrap items-center gap-2 border-t pt-4 text-xs text-[hsl(var(--muted-foreground))]">
      {item.provider && <Badge variant="neutral">Provider: {item.provider}</Badge>}
      <span>{item.productionReady ? "Production gate satisfied" : item.enabled ? "Not production-ready" : "Capability disabled"}</span>
    </div>
  </article>;
}

function Metric({ label, value, icon: Icon }: { label: string; value: number; icon: typeof CheckCircle2 }) {
  return <div className="panel metric-card p-5"><div className="flex items-start justify-between"><div><p className="text-xs font-semibold uppercase tracking-[.12em] text-[hsl(var(--muted-foreground))]">{label}</p><p className="mt-2 text-3xl font-semibold tracking-[-.04em]">{value}</p></div><div className="grid size-10 place-items-center rounded-xl bg-[hsl(var(--surface-subtle))]"><Icon className="size-4.5"/></div></div></div>;
}

function Loading() {
  return <div className="flex min-h-72 items-center justify-center text-sm text-[hsl(var(--muted-foreground))]"><Loader2 className="mr-2 size-4 animate-spin"/>Loading integration readiness…</div>;
}
