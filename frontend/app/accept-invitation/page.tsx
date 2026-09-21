import { AuthShell } from "@/features/auth/auth-shell";
import { TokenPasswordForm } from "@/features/auth/token-password-form";
export default async function AcceptInvitationPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) { const { token = "" } = await searchParams; return <AuthShell eyebrow="Team invitation" title="Join your organization." description="Accept the tenant-scoped invitation before signing in to this workspace."><TokenPasswordForm mode="invitation" token={token} /></AuthShell>; }
