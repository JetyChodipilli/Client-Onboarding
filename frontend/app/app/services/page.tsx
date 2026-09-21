import type { Metadata } from "next";
import { ServicesWorkspace } from "@/features/operations/operations-pages";

export const metadata: Metadata = { title: "Services" };
export default function ServicesPage() { return <ServicesWorkspace />; }
