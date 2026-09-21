import type { Metadata } from "next";
import { AuthShell } from "@/features/auth/auth-shell";
import { ForgotPasswordForm } from "@/features/auth/forgot-password-form";

export const metadata: Metadata = { title: "Forgot password" };
export default function ForgotPasswordPage() { return <AuthShell eyebrow="Account recovery" title="Get back to your workspace." description="Recovery links are single-use and expire quickly. We never reveal whether an account exists."><ForgotPasswordForm /></AuthShell>; }
