"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { PageMeta } from "@/types/api";

export function PaginationControls({
  meta,
  onPageChange,
  disabled = false,
}: {
  meta: PageMeta;
  onPageChange: (page: number) => void;
  disabled?: boolean;
}) {
  if (meta.totalElements === 0) return null;

  const start = meta.page * meta.size + 1;
  const end = Math.min(meta.totalElements, start + meta.size - 1);
  const hasPrevious = meta.page > 0;
  const hasNext = meta.page + 1 < meta.totalPages;

  return (
    <div className="flex flex-col gap-3 border-t px-4 py-3 text-xs text-[hsl(var(--muted-foreground))] sm:flex-row sm:items-center sm:justify-between sm:px-5">
      <p aria-live="polite">
        Showing <span className="font-semibold text-[hsl(var(--foreground))]">{start}–{end}</span> of{" "}
        <span className="font-semibold text-[hsl(var(--foreground))]">{meta.totalElements}</span>
      </p>
      <div className="flex items-center justify-between gap-2 sm:justify-end">
        <span className="mr-1 tabular-nums">Page {meta.page + 1} of {Math.max(meta.totalPages, 1)}</span>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={disabled || !hasPrevious}
          onClick={() => onPageChange(meta.page - 1)}
        >
          <ChevronLeft className="size-4" />Previous
        </Button>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={disabled || !hasNext}
          onClick={() => onPageChange(meta.page + 1)}
        >
          Next<ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  );
}
