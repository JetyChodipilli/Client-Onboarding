import type { Metadata } from "next";
import { WorkflowEditor } from "@/features/operations/workflow-editor";

export const metadata: Metadata = { title: "Workflow builder" };
export default async function WorkflowPage({ params }: { params: Promise<{ templateId: string }> }) {
  const { templateId } = await params;
  return <WorkflowEditor templateId={templateId} />;
}
