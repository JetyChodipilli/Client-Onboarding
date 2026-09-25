export type AssetRequirement = { id: string; name: string; instructions?: string; allowedMimes: string[]; maxBytes: number; version: number };
export type AssetFile = { id: string; versionNumber: number; filename: string; mime: string; byteSize: number; status: string; scanStatus: string; scanMessage?: string; reviewNote?: string; createdAt: string; downloadable: boolean };
export type AssetView = { projectId: string; stepId: string; stepName: string; stepStatus: string; projectStatus: string; onboardingStatus: string; deadline?: string; allowSkip: boolean; allowReopen: boolean; requirement: AssetRequirement; version: number; current?: AssetFile };
export type AssetVersion = { file: AssetFile; reviews: { decision: string; note?: string; createdAt: string }[] };
export type SignedTransfer = { url: string; method: string; headers: Record<string, string>; expiresAt: string };
export const mimeLabels: Record<string, string> = { "image/png": "PNG image", "image/jpeg": "JPEG image", "image/gif": "GIF image", "image/webp": "WebP image", "application/pdf": "PDF document", "text/plain": "Text document", "video/mp4": "MP4 video" };
export const control = "mt-2 min-h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-base text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50";
export function fileSize(bytes: number) { return bytes < 1024 ? `${bytes} bytes` : bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KiB` : `${(bytes / 1048576).toFixed(1)} MiB`; }
export function validateFile(file: Pick<File, "size" | "type">, requirement: AssetRequirement) {
  if (!file.size || file.size > requirement.maxBytes) return `Choose a non-empty file no larger than ${fileSize(requirement.maxBytes)}.`;
  if (!requirement.allowedMimes.includes(file.type)) return "This file type is not permitted. Choose one of the types listed below.";
  return "";
}
