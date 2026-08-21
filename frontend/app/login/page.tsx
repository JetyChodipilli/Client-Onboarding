import { AuthShell } from "@/components/auth/auth-shell";
import { LoginForm } from "@/features/auth/login-form";
export default function LoginPage() { return <AuthShell eyebrow="Secure sign in" title="Welcome back" description="Use your organization workspace and verified work account to continue."><LoginForm /></AuthShell>; }
