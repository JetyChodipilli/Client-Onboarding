import type { Metadata } from "next";
import { ClientsWorkspace } from "@/features/operations/operations-pages";

export const metadata: Metadata = { title: "Clients" };
export default function ClientsPage() { return <ClientsWorkspace />; }
