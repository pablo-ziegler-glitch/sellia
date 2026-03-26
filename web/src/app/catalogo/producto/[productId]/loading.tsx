export default function ProductLoading() {
  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Breadcrumb skeleton */}
      <div
        className="h-4 w-48 rounded animate-pulse mb-6"
        style={{ background: "var(--surface)" }}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
        {/* Imagen skeleton */}
        <div
          className="w-full aspect-square rounded-xl animate-pulse"
          style={{ background: "var(--surface)" }}
        />

        {/* Info skeleton */}
        <div className="space-y-4">
          <div
            className="h-3 w-24 rounded animate-pulse"
            style={{ background: "var(--surface)" }}
          />
          <div
            className="h-8 w-3/4 rounded animate-pulse"
            style={{ background: "var(--surface)" }}
          />
          <div
            className="h-6 w-1/2 rounded animate-pulse"
            style={{ background: "var(--surface)" }}
          />

          {/* Price box skeleton */}
          <div
            className="rounded-xl p-5 space-y-4 animate-pulse"
            style={{
              background: "var(--surface)",
              border: "1px solid var(--border)",
            }}
          >
            {[1, 2].map((i) => (
              <div key={i} className="flex justify-between">
                <div
                  className="h-4 w-24 rounded"
                  style={{ background: "var(--surface-2)" }}
                />
                <div
                  className="h-6 w-28 rounded"
                  style={{ background: "var(--surface-2)" }}
                />
              </div>
            ))}
          </div>

          {/* Buttons skeleton */}
          <div className="space-y-3 pt-2">
            <div
              className="h-12 w-full rounded animate-pulse"
              style={{ background: "var(--surface)" }}
            />
            <div
              className="h-12 w-full rounded animate-pulse"
              style={{ background: "var(--surface)" }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
