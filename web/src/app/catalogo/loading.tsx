import ProductGridSkeleton from "./ProductGridSkeleton";

export default function CatalogoLoading() {
  return (
    <div className="mx-auto max-w-7xl px-4 pb-16 pt-6 sm:px-6">
      <div className="mb-6 h-4 w-40 animate-pulse rounded bg-[var(--surface-high)]" />
      <div className="mb-8 h-52 w-full animate-pulse rounded-xl bg-[var(--surface-high)] sm:h-64" />
      <div className="mb-8 flex gap-2">
        {[80, 90].map((w, i) => (
          <div
            key={i}
            className="h-8 animate-pulse rounded-full bg-[var(--surface-high)]"
            style={{ width: w }}
          />
        ))}
      </div>
      <ProductGridSkeleton count={12} />
    </div>
  );
}
