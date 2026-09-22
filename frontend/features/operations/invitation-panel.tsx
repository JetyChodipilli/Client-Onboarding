"use client";

import { CheckCircle2, Mail, RefreshCw, ShieldX } from "lucide-react";
import { useEffect, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useCurrentUser } from "@/features/settings/internal-shell";
import { operationsApi } from "./operations-api";
import { errorMessage } from "./operations-pages";
import type { ClientContact, ClientInvitation } from "./types";

export function InvitationPanel({ onboardingId, clientId, onboardingStatus }: {
  onboardingId: string;
  clientId: string;
  onboardingStatus: string;
}) {
  const user = useCurrentUser();
  const [contacts, setContacts] = useState<ClientContact[]>([]);
  const [invitations, setInvitations] = useState<ClientInvitation[]>([]);
  const [contactId, setContactId] = useState("");
  const [role, setRole] = useState<ClientInvitation["role"]>("ADMIN");
  const [loaded, setLoaded] = useState(false);
  const [pending, setPending] = useState(false);
  const [confirmRevoke, setConfirmRevoke] = useState<string>();
  const [error, setError] = useState("");
  const canInvite = user.permissions.includes("ONBOARDING_INVITE") && user.permissions.includes("CLIENT_READ");

  useEffect(() => {
    if (!canInvite) return;
    let active = true;
    Promise.all([
        operationsApi.contacts(clientId),
        operationsApi.invitations(onboardingId),
      ]).then(([contactData, invitationData]) => {
      if (!active) return;
      const activeContacts = contactData.filter((contact) => !contact.archivedAt);
      setContacts(activeContacts);
      setContactId((current) => current || activeContacts.find((contact) => contact.primary)?.id || activeContacts[0]?.id || "");
      setInvitations(invitationData);
    }).catch((cause) => { if (active) setError(errorMessage(cause)); })
      .finally(() => { if (active) setLoaded(true); });
    return () => { active = false; };
  }, [canInvite, clientId, onboardingId]);

  async function invite() {
    if (!contactId) return;
    setPending(true); setError("");
    try {
      const created = await operationsApi.inviteClient(onboardingId, contactId, role, crypto.randomUUID());
      setInvitations((values) => [created, ...values]);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setPending(false); }
  }

  async function update(value: ClientInvitation, action: "resend" | "revoke") {
    setPending(true); setError("");
    try {
      const updated = action === "resend"
        ? await operationsApi.resendClientInvitation(value.id, value.version)
        : await operationsApi.revokeClientInvitation(value.id, value.version);
      setInvitations((values) => values.map((item) => item.id === updated.id ? updated : item));
      setConfirmRevoke(undefined);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setPending(false); }
  }

  if (!canInvite) return null;
  return <section className="mt-8" aria-labelledby="client-access-heading">
    <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="text-xs font-bold uppercase tracking-[0.16em] text-primary">Client access</p><h2 id="client-access-heading" className="mt-2 text-xl font-bold">Portal invitations</h2><p className="mt-1 text-sm text-muted-foreground">Each invitation is single-use, expires automatically, and grants access only to this project.</p></div>
      <Badge tone={onboardingStatus === "DRAFT" ? "info" : "neutral"}>{onboardingStatus}</Badge>
    </div>
    {error && <Alert tone="error" className="mt-4">{error}</Alert>}
    {!loaded && <Alert className="mt-4">Loading portal contacts and invitations…</Alert>}
    {["DRAFT", "INVITED", "IN_PROGRESS"].includes(onboardingStatus) && <Card className="mt-5 grid gap-4 md:grid-cols-[minmax(0,1fr)_12rem_auto] md:items-end">
      <label className="text-sm font-semibold">Client contact<select value={contactId} onChange={(event) => setContactId(event.target.value)} className="mt-2 h-12 w-full cursor-pointer rounded-md border bg-background px-3.5 text-base"><option value="">Select a contact</option>{contacts.map((contact) => <option key={contact.id} value={contact.id}>{contact.name} · {contact.email}</option>)}</select></label>
      <label className="text-sm font-semibold">Portal role<select value={role} onChange={(event) => setRole(event.target.value as ClientInvitation["role"])} className="mt-2 h-12 w-full cursor-pointer rounded-md border bg-background px-3.5 text-base"><option value="ADMIN">Client admin</option><option value="MEMBER">Client member</option></select></label>
      <Button variant="accent" size="lg" onClick={invite} disabled={pending || !contactId}><Mail aria-hidden="true" />{pending ? "Sending…" : "Send invitation"}</Button>
    </Card>}
    <div className="mt-4 space-y-3">
      {loaded && invitations.length === 0 ? <Alert>No invitation has been sent. Add a client contact first if this list is empty.</Alert> : invitations.map((invitation) => <Card key={invitation.id} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><p className="truncate font-semibold">{invitation.email}</p><Badge tone={invitation.effectiveStatus === "ACCEPTED" ? "success" : invitation.deliveryStatus === "FAILED" ? "warning" : "neutral"}>{invitation.effectiveStatus}</Badge><Badge>{invitation.role}</Badge></div><p className="mt-1 text-sm text-muted-foreground">Delivery: {invitation.deliveryStatus.toLowerCase()} · expires {new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(invitation.expiresAt))}</p></div>
        {invitation.status === "PENDING" && <div className="flex flex-wrap gap-2"><Button size="sm" variant="outline" onClick={() => update(invitation, "resend")} disabled={pending}><RefreshCw aria-hidden="true" />Resend</Button>{confirmRevoke === invitation.id ? <><Button size="sm" variant="danger" onClick={() => update(invitation, "revoke")} disabled={pending}><ShieldX aria-hidden="true" />Confirm revoke</Button><Button size="sm" variant="ghost" onClick={() => setConfirmRevoke(undefined)} disabled={pending}>Cancel</Button></> : <Button size="sm" variant="ghost" onClick={() => setConfirmRevoke(invitation.id)} disabled={pending}><ShieldX aria-hidden="true" />Revoke</Button>}</div>}
        {invitation.status === "ACCEPTED" && <span className="inline-flex items-center gap-2 text-sm font-semibold text-success"><CheckCircle2 aria-hidden="true" className="size-4" />Portal active</span>}
      </Card>)}
    </div>
  </section>;
}
