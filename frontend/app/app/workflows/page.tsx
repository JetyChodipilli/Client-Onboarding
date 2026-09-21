import type { Metadata } from "next";
import { WorkflowsWorkspace } from "@/features/operations/workflows-workspace";

export const metadata: Metadata = { title: "Workflow templates" };
export default function WorkflowsPage() { return <WorkflowsWorkspace />; }
