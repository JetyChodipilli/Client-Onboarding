export default function Loading() {
  return (
    <main className="page-shell min-h-dvh py-20" aria-busy="true" aria-label="Loading page">
      <div className="h-10 w-48 animate-pulse rounded-md bg-muted" />
      <div className="mt-16 max-w-4xl space-y-5">
        <div className="h-16 w-full animate-pulse rounded-lg bg-muted" />
        <div className="h-16 w-4/5 animate-pulse rounded-lg bg-muted" />
        <div className="h-6 w-2/3 animate-pulse rounded-md bg-muted" />
      </div>
    </main>
  );
}

