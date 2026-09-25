import { apiRequest } from "@/lib/api-client";
import type { AssetRequirement, AssetVersion, AssetView, SignedTransfer } from "./types";
const body = (data: unknown) => ({ method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });
const path = (step: string, project?: string): `/api/v1/${string}` => project ? `/api/v1/client-portal/projects/${project}/assets/${step}` : `/api/v1/asset-responses/${step}`;
export const assetsApi = {
  list: (search = "", page = 0) => apiRequest<AssetRequirement[]>(`/api/v1/asset-requirements?search=${encodeURIComponent(search)}&page=${page}&size=20`),
  create: (name: string, instructions: string, allowedMimes: string[], maxBytes: number) => apiRequest<AssetRequirement>("/api/v1/asset-requirements", body({ name, instructions, allowedMimes, maxBytes })),
  archive: (id: string, version: number) => apiRequest(`/api/v1/asset-requirements/${id}/archive?version=${version}`, { method: "POST" }),
  view: (step: string, project?: string) => apiRequest<AssetView>(path(step, project)),
  history: (step: string, project?: string, page = 0) => apiRequest<AssetVersion[]>(`${path(step, project)}/versions?page=${page}&size=10`),
  upload: (step: string, project: string, version: number, file: File, sha256: string) => apiRequest<{ asset: AssetView; upload: SignedTransfer }>(`${path(step, project)}/upload-url`, body({ version, filename: file.name, mime: file.type, byteSize: file.size, sha256 })),
  submit: (step: string, project: string, version: number) => apiRequest<AssetView>(`${path(step, project)}/submit`, body({ version })),
  review: (step: string, version: number, decision: string, note: string) => apiRequest<AssetView>(`${path(step)}/review`, body({ version, decision, note })),
  exception: (step: string, version: number, action: "skip" | "reopen", note: string) => apiRequest<AssetView>(`${path(step)}/${action}`, body({ version, note })),
  download: (step: string, id: string, project?: string) => apiRequest<SignedTransfer>(`${path(step, project)}/versions/${id}/download`, { method: "POST" }),
};

/** The storage request carries only the signed headers; never app cookies, CSRF or session tokens. */
export function uploadFile(target: SignedTransfer, file: File, progress: (percent: number) => void, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const cancel = () => xhr.abort();
    const done = (error?: Error) => { signal.removeEventListener("abort", cancel); if (error) reject(error); else resolve(); };
    xhr.open("PUT", target.url); xhr.withCredentials = false; xhr.timeout = 120_000;
    for (const [key, value] of Object.entries(target.headers)) xhr.setRequestHeader(key, value);
    xhr.upload.onprogress = (event) => { if (event.lengthComputable) progress(Math.round(event.loaded / event.total * 100)); };
    xhr.onload = () => done(xhr.status >= 200 && xhr.status < 300 ? undefined : new Error("The upload was rejected or expired. Choose the file again to retry."));
    xhr.onerror = () => done(new Error("The file could not reach storage. Check your connection and retry."));
    xhr.ontimeout = () => done(new Error("The upload timed out. Please try again."));
    xhr.onabort = () => done(new Error("Upload cancelled. You can choose a file and start again."));
    signal.addEventListener("abort", cancel, { once: true });
    if (signal.aborted) { done(new Error("Upload cancelled.")); return; }
    xhr.send(file);
  });
}
