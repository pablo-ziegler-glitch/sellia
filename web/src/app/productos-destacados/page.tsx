import Image from "next/image";
import Link from "next/link";
import {
  VALKIRJA_TENANT_ID,
  getCatalogConfig,
  getPublicProducts,
} from "@/lib/catalog";
import { formatPrice } from "@/lib/format";

export const revalidate = 300;

export default async function FeaturedProductsPage() {
  const [catalogConfig, allProducts] = await Promise.all([
    getCatalogConfig(VALKIRJA_TENANT_ID),
    getPublicProducts(VALKIRJA_TENANT_ID),
  ]);

  const featuredIds = new Set(catalogConfig.featuredProductIds || []);
  const products = (featuredIds.size
    ? allProducts.filter((product) => featuredIds.has(product.id))
    : allProducts.slice(0, 12)
  ).slice(0, 24);

  return (
    <div className="max-w-6xl mx-auto px-4 py-10">
      <div className="mb-8">
        <p className="text-xs tracking-[0.3em] uppercase mb-2" style={{ color: "var(--gold)" }}>
          Catálogo
        </p>
        <h1 className="text-3xl sm:text-4xl font-bold" style={{ color: "var(--foreground)" }}>
          {catalogConfig.featuredTitle || "Productos destacados"}
        </h1>
      </div>

      {products.length === 0 ? (
        <p style={{ color: "var(--muted)" }}>No hay productos destacados configurados.</p>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
          {products.map((product) => (
            <Link
              key={product.id}
              href={`/catalogo/producto/${product.id}`}
              className="rounded-xl overflow-hidden"
              style={{ background: "var(--surface)", border: "1px solid var(--border)" }}
            >
              <div className="relative w-full aspect-square">
                {product.imageUrl ? (
                  <Image src={product.imageUrl} alt={product.name} fill className="object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-xs" style={{ color: "var(--muted)" }}>
                    Sin imagen
                  </div>
                )}
              </div>
              <div className="p-3">
                <p className="text-sm font-semibold line-clamp-2" style={{ color: "var(--foreground)" }}>{product.name}</p>
                {product.listPrice != null && (
                  <p className="text-sm mt-1" style={{ color: "var(--gold)" }}>{formatPrice(product.listPrice)}</p>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
