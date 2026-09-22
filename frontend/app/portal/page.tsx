import type { Metadata } from "next";
import { PortalProjects } from "@/features/portal/portal-projects";
export const metadata: Metadata = { title: "Client portal" };
export default function PortalPage() { return <PortalProjects />; }
