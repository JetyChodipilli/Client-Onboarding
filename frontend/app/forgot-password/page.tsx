import { AuthShell } from "@/components/auth/auth-shell";
import { ForgotPasswordForm } from "@/features/auth/forgot-password-form";
export default function ForgotPasswordPage(){return <AuthShell eyebrow="Account recovery" title="Reset your password" description="We’ll send instructions only when the email and workspace match an account."><ForgotPasswordForm/></AuthShell>}
