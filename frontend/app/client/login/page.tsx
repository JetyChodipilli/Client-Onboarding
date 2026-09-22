import type { Metadata } from "next";
import { AuthShell } from "@/features/auth/auth-shell";
import { ClientLoginForm } from "@/features/portal/client-login-form";

export const metadata: Metadata = { title: "Client portal sign in" };
export default async function ClientLoginPage({ searchParams }: { searchParams: Promise<{ organization?: string }> }) { const { organization = "" } = await searchParams; return <AuthShell eyebrow="Secure client portal" title="Continue your onboarding." description="Your portal shows progress, the next action, blockers, deadlines, and available help in one place."><ClientLoginForm organization={organization} /></AuthShell>; }
