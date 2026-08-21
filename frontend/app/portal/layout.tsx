import { ClientAuthProvider } from "@/auth/client-auth-provider";
export default function PortalLayout({children}:{children:React.ReactNode}){return <ClientAuthProvider>{children}</ClientAuthProvider>}
