import { AuthShell } from "@/components/auth/auth-shell";
import { ResetPasswordForm } from "@/features/auth/reset-password-form";
export default async function ResetPasswordPage({searchParams}:{searchParams:Promise<{token?:string}>}){const {token=""}=await searchParams;return <AuthShell eyebrow="Account recovery" title="Choose a new password" description="A successful reset signs out existing sessions for this identity."><ResetPasswordForm token={token}/></AuthShell>}
