import { AuthShell } from "@/features/auth/auth-shell";
import { ClientForgotPasswordForm } from "@/features/portal/client-forgot-password-form";
export default async function ClientForgotPasswordPage({ searchParams }: { searchParams: Promise<{ organization?: string }> }) { const { organization = "" } = await searchParams; return <AuthShell eyebrow="Account recovery" title="Restore client portal access." description="Request a private, one-time reset link for your client account."><ClientForgotPasswordForm organization={organization} /></AuthShell>; }
