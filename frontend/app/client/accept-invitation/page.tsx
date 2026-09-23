import type { Metadata } from "next";
import { AuthShell } from "@/features/auth/auth-shell";
import { ClientInvitationForm } from "@/features/portal/client-invitation-form";

export const metadata: Metadata = { title: "Activate client portal" };
export default async function ClientInvitationPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) { const { token = "" } = await searchParams; return <AuthShell eyebrow="Client invitation" title="Your project workspace is ready." description="Verify the invitation, activate your account, and see exactly what happens next."><ClientInvitationForm token={token} /></AuthShell>; }
