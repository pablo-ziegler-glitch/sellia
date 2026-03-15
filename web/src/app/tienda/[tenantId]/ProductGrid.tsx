"use client";

import { useMemo, useState, useEffect } from "react";
import { useRouter, useSearchParams, usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import type { PublicProduct } from "@/lib/catalog";
import { formatPrice } from "@/lib/format";

const PAGE_SIZE = 24;

interface Props {
  tenantId: string;
  products: PublicProduct[];
}

export default function ProductGrid({ tenantId, products }: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const activeCategory = searchParams.get("categoria") ?? "all";
  const [page, setPage] = useState(1);

  // Reset page when category changes
  useEffect(() => {
    setPage(1);
  }, [activeCategory]);

  const categories = useMemo(() => {
    const cats = Array.from(
      new Set(products.map((p) => p.category).filter(Boolean))
    ) as string[];
    return cats.sort();
  }, [products]);

  const filtered = useMemo(
    () =>
      activeCategory === "all"
        ? products
        : products.filter((p) => p.category === activeCategory),
    [products, activeCategory]
  );

  const visible = filtered.slice(0, page * PAGE_SIZE);
  const hasMore = visible.length < filtered.length;

  function selectCategory(cat: string) {
    const params = new URLSearchParams(searchParams.toString());
    if (cat === "all") {
      params.delete("categoria");
    } else {
      params.set("categoria", cat);
    }
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  }

  return (
    <div>
      {categories.length > 1 && (
        <div className="flex flex-wrap gap-2 mb-5">
          <button
            onClick={() => selectCategory("all")}
            className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
              activeCategory === "all"
                ? "bg-blue-600 text-white"
                : "bg-white border border-gray-200 text-gray-600 hover:border-blue-400"
            }`}
          >
            Todos ({products.length})
          </button>
          {categories.map((cat) => {
            const count = products.filter((p) => p.category === cat).length;
            return (
              <button
                key={cat}
                onClick={() => selectCategory(cat)}
                className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
                  activeCategory === cat
                    ? "bg-blue-600 text-white"
                    : "bg-white border border-gray-200 text-gray-600 hover:border-blue-400"
                }`}
              >
                {cat} ({count})
              </button>
            );
          })}
        </div>
      )}

      {visible.length === 0 ? (
        <p className="text-gray-400">No hay productos en esta categoría.</p>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {visible.map((product) => (
              <Link
                key={product.id}
                href={`/tienda/${tenantId}/producto/${product.id}`}
                className="bg-white rounded-lg border border-gray-200 overflow-hidden hover:shadow-md transition-shadow"
              >
                {product.imageUrl ? (
                  <div className="relative w-full aspect-square">
                    <Image
                      src={product.imageUrl}
                      alt={product.name}
                      fill
                      className="object-cover"
                      sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
                    />
                  </div>
                ) : (
                  <div className="w-full aspect-square bg-gray-100 flex items-center justify-center text-gray-300">
                    Sin imagen
                  </div>
                )}
                <div className="p-3">
                  <h3 className="text-sm font-medium line-clamp-2">
                    {product.name}
                  </h3>
                  {product.category && (
                    <p className="text-xs text-gray-400 mt-0.5">
                      {product.category}
                    </p>
                  )}
                  <div className="mt-2">
                    {product.listPrice != null && (
                      <p className="text-base font-bold text-blue-600">
                        {formatPrice(product.listPrice)}
                      </p>
                    )}
                    {product.cashPrice != null &&
                      product.cashPrice !== product.listPrice && (
                        <p className="text-xs text-green-600">
                          Efectivo: {formatPrice(product.cashPrice)}
                        </p>
                      )}
                  </div>
                </div>
              </Link>
            ))}
          </div>

          {hasMore && (
            <div className="mt-8 text-center">
              <button
                onClick={() => setPage((p) => p + 1)}
                className="px-6 py-2.5 rounded-lg border border-gray-300 text-sm font-medium text-gray-700 hover:border-blue-400 hover:text-blue-600 transition-colors"
              >
                Ver más ({filtered.length - visible.length} restantes)
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
