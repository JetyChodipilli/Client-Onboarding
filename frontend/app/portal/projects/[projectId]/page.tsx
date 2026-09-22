import type { Metadata } from "next";
import { PortalDashboard } from "@/features/portal/portal-dashboard";
export const metadata: Metadata = { title: "Project onboarding" };
export default async function PortalProjectPage({ params }: { params: Promise<{ projectId: string }> }) { const { projectId } = await params; return <PortalDashboard projectId={projectId} />; }
