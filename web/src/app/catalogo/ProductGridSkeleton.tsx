export default function ProductGridSkeleton({ count = 12 }: { count?: number }) {
  return (
    <div>
      {/* Controles skeleton */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div
          className="flex-1 h-10 rounded-lg animate-pulse"
          style={{ background: "var(--surface-high)" }}
        />
        <div
          className="h-10 w-40 rounded-lg animate-pulse"
          style={{ background: "var(--surface-high)" }}
        />
      </div>

      {/* Categorías skeleton */}
      <div className="flex gap-2 mb-6">
        {[80, 110, 90, 120].map((w, i) => (
          <div
            key={i}
            className="h-7 rounded-full animate-pulse flex-shrink-0"
            style={{ width: w, background: "var(--surface-high)" }}
          />
        ))}
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {Array.from({ length: count }).map((_, i) => (
          <div
            key={i}
            className="rounded-xl overflow-hidden"
            style={{
              background: "var(--surface-pop)",
              boxShadow: "var(--ambient-shadow)",
            }}
          >
            <div className="flex flex-col sm:flex-row">
              <div
                className="w-full sm:w-64 md:w-72 aspect-square animate-pulse"
                style={{ background: "var(--surface-highest)" }}
              />
              <div className="p-4 sm:p-6 flex-1 space-y-3">
                <div
                  className="h-4 rounded animate-pulse"
                  style={{ background: "var(--surface-highest)", width: "35%" }}
                />
                <div
                  className="h-6 rounded animate-pulse"
                  style={{ background: "var(--surface-highest)", width: "78%" }}
                />
                <div
                  className="h-6 rounded animate-pulse"
                  style={{ background: "var(--surface-highest)", width: "24%" }}
                />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
