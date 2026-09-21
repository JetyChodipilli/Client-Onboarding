import { AuthShell } from "@/features/auth/auth-shell";
import { TokenPasswordForm } from "@/features/auth/token-password-form";
export default async function ResetPasswordPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) { const { token = "" } = await searchParams; return <AuthShell eyebrow="Secure recovery" title="Create a fresh credential." description="Completing this action signs out every existing session for your account."><TokenPasswordForm mode="reset" token={token} /></AuthShell>; }
