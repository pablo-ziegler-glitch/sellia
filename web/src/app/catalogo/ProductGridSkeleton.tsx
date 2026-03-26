export default function ProductGridSkeleton({ count = 12 }: { count?: number }) {
  return (
    <div>
      {/* Controles skeleton */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div
          className="flex-1 h-10 rounded-lg animate-pulse"
          style={{ background: "var(--surface)" }}
        />
        <div
          className="h-10 w-40 rounded-lg animate-pulse"
          style={{ background: "var(--surface)" }}
        />
      </div>

      {/* Categorías skeleton */}
      <div className="flex gap-2 mb-6">
        {[80, 110, 90, 120].map((w, i) => (
          <div
            key={i}
            className="h-7 rounded-full animate-pulse flex-shrink-0"
            style={{ width: w, background: "var(--surface)" }}
          />
        ))}
      </div>

      {/* Grid skeleton */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        {Array.from({ length: count }).map((_, i) => (
          <div
            key={i}
            className="rounded-xl overflow-hidden"
            style={{
              background: "var(--surface)",
              border: "1px solid var(--border)",
            }}
          >
            <div
              className="w-full aspect-square animate-pulse"
              style={{ background: "var(--surface-2)" }}
            />
            <div className="p-3 space-y-2">
              <div
                className="h-3.5 rounded animate-pulse"
                style={{ background: "var(--surface-2)", width: "85%" }}
              />
              <div
                className="h-3 rounded animate-pulse"
                style={{ background: "var(--surface-2)", width: "55%" }}
              />
              <div
                className="h-4 rounded animate-pulse mt-1"
                style={{ background: "var(--surface-2)", width: "45%" }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
