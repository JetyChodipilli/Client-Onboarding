import type { Metadata } from "next";
import { ProjectsWorkspace } from "@/features/operations/operations-pages";

export const metadata: Metadata = { title: "Projects" };
export default function ProjectsPage() { return <ProjectsWorkspace />; }
