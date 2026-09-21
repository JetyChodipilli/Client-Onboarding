import type { Metadata } from "next";
import { AuthShell } from "@/features/auth/auth-shell";
import { LoginForm } from "@/features/auth/login-form";

export const metadata: Metadata = { title: "Sign in" };
export default function LoginPage() { return <AuthShell eyebrow="Secure workspace" title="Welcome back." description="Sign in to manage your organization’s team, roles, and security baseline."><LoginForm /></AuthShell>; }
