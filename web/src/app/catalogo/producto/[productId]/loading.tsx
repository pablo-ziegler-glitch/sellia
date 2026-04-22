export default function ProductLoading() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div className="mb-6 h-4 w-48 animate-pulse rounded bg-[var(--surface-high)]" />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
        <div
          className="w-full aspect-square animate-pulse rounded-2xl bg-[var(--surface-high)]"
        />

        <div className="space-y-4">
          <div
            className="h-3 w-24 rounded animate-pulse"
            style={{ background: "var(--surface-high)" }}
          />
          <div
            className="h-8 w-3/4 rounded animate-pulse"
            style={{ background: "var(--surface-high)" }}
          />
          <div
            className="h-6 w-1/2 rounded animate-pulse"
            style={{ background: "var(--surface-high)" }}
          />

          <div
            className="rounded-2xl p-5 space-y-4 animate-pulse"
            style={{
              background: "var(--surface-low)",
            }}
          >
            {[1, 2].map((i) => (
              <div key={i} className="flex justify-between">
              <div
                className="h-4 w-24 rounded"
                style={{ background: "var(--surface-highest)" }}
              />
              <div
                className="h-6 w-28 rounded"
                style={{ background: "var(--surface-highest)" }}
              />
            </div>
            ))}
          </div>

          <div className="space-y-3 pt-2">
            <div
              className="h-12 w-full rounded animate-pulse"
              style={{ background: "var(--surface-high)" }}
            />
            <div
              className="h-12 w-full rounded animate-pulse"
              style={{ background: "var(--surface-high)" }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
