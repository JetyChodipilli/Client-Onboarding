import type { Metadata } from "next";
import { ClientDetail } from "@/features/operations/client-detail";

export const metadata: Metadata = { title: "Client" };
export default async function ClientPage({ params }: { params: Promise<{ clientId: string }> }) {
  const { clientId } = await params;
  return <ClientDetail clientId={clientId} />;
}
