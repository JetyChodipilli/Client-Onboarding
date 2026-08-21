export default function Loading() {
  return (
    <main className="mx-auto min-h-screen w-full max-w-6xl px-5 py-12 sm:px-8 lg:px-10" aria-busy="true">
      <div className="h-10 w-52 animate-pulse rounded-xl bg-black/5" />
      <div className="mt-20 h-16 max-w-3xl animate-pulse rounded-2xl bg-black/5" />
      <div className="mt-4 h-7 max-w-xl animate-pulse rounded-xl bg-black/5" />
    </main>
  );
}
