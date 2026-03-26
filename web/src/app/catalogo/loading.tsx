import ProductGridSkeleton from "./ProductGridSkeleton";

export default function CatalogoLoading() {
  return (
    <div className="px-4 pt-8 pb-16 max-w-6xl mx-auto">
      {/* Breadcrumb skeleton */}
      <div
        className="h-4 w-32 rounded animate-pulse mb-6"
        style={{ background: "var(--surface)" }}
      />

      {/* Banner skeleton */}
      <div
        className="w-full h-48 sm:h-64 rounded-xl animate-pulse mb-6"
        style={{ background: "var(--surface)" }}
      />

      {/* Contacto skeleton */}
      <div className="flex gap-2 mb-8">
        {[80, 90].map((w, i) => (
          <div
            key={i}
            className="h-8 rounded-full animate-pulse"
            style={{ width: w, background: "var(--surface)" }}
          />
        ))}
      </div>

      <ProductGridSkeleton count={12} />
    </div>
  );
}
