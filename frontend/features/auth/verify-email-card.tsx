"use client";
import Link from "next/link";
import { useEffect,useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { FormMessage } from "@/components/auth/form-message";
import { apiRequest,ApiClientError } from "@/services/api-client";
export function VerifyEmailCard({token}:{token:string}){const [state,setState]=useState<"loading"|"done"|"error">(token?"loading":"error");const [message,setMessage]=useState(token?"":"This verification link is missing its security token.");useEffect(()=>{if(!token)return;let active=true;apiRequest<{message:string}>("/auth/verify-email",{method:"POST",body:JSON.stringify({token})}).then(r=>{if(active){setMessage(r.message);setState("done")}}).catch(e=>{if(active){setMessage(e instanceof ApiClientError?e.message:"Email could not be verified.");setState("error")}});return()=>{active=false}},[token]);if(state==="loading")return <div className="flex items-center gap-3 rounded-xl border bg-white p-4 text-sm"><Loader2 className="size-4 animate-spin"/>Verifying your email…</div>;return <div className="space-y-4"><FormMessage tone={state==="done"?"success":"error"}>{message}</FormMessage><Button className="w-full" asChild><Link href="/login">Continue to sign in</Link></Button></div>}
