"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiClientError } from "@/lib/api-client";
import { authApi } from "./auth-api";

export function useSignOut(destination: string) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function signOut() {
    if (pending) return;
    setPending(true);
    setError("");
    try {
      await authApi.logout();
      router.replace(destination);
      router.refresh();
    } catch (failure) {
      if (failure instanceof ApiClientError && failure.status === 401 && failure.code === "AUTHENTICATION_REQUIRED") {
        router.replace(destination);
        router.refresh();
      } else {
        setError("Sign out failed. Your session may still be active. Please try again.");
      }
    } finally {
      setPending(false);
    }
  }

  return { signOut, pending, error };
}
