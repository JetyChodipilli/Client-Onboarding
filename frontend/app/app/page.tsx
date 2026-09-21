import type { Metadata } from "next";
import { WorkspaceOverview } from "@/features/settings/workspace-overview";
export const metadata: Metadata = { title: "Workspace" };
export default function WorkspacePage() { return <WorkspaceOverview />; }
