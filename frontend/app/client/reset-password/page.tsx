import { AuthShell } from "@/features/auth/auth-shell";
import { TokenPasswordForm } from "@/features/auth/token-password-form";
export default async function ClientResetPasswordPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) { const { token = "" } = await searchParams; return <AuthShell eyebrow="Client account recovery" title="Choose a new password." description="The one-time reset link revokes existing sessions after the password changes."><TokenPasswordForm mode="reset" token={token} continueHref="/client/login" /></AuthShell>; }
