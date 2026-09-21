import { AuthShell } from "@/features/auth/auth-shell";
import { TokenPasswordForm } from "@/features/auth/token-password-form";
export default async function VerifyEmailPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) { const { token = "" } = await searchParams; return <AuthShell eyebrow="Account activation" title="Verify your account." description="Choose a strong password to complete activation and enter your organization workspace."><TokenPasswordForm mode="verify" token={token} /></AuthShell>; }
