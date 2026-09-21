import type { Metadata } from "next";
import { ProjectDetail } from "@/features/operations/project-detail";

export const metadata: Metadata = { title: "Project workflow" };
export default async function ProjectPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = await params;
  return <ProjectDetail projectId={projectId} />;
}
