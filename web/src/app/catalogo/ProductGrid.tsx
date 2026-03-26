"use client";

import { useMemo, useState, useEffect } from "react";
import { useRouter, useSearchParams, usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import type { PublicProduct } from "@/lib/catalog";
import { formatPrice } from "@/lib/format";

const PAGE_SIZE = 24;

interface Props {
  products: PublicProduct[];
}

export default function ProductGrid({ products }: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const activeCategory = searchParams.get("categoria") ?? "all";
  const [page, setPage] = useState(1);

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
        <div className="flex flex-wrap gap-2 mb-6">
          <button
            onClick={() => selectCategory("all")}
            className="px-4 py-1.5 rounded-full text-xs font-medium tracking-wide uppercase transition-all"
            style={
              activeCategory === "all"
                ? { background: "var(--gold)", color: "#0a0a0a" }
                : {
                    background: "var(--surface-2)",
                    color: "var(--muted)",
                    border: "1px solid var(--border)",
                  }
            }
          >
            Todos ({products.length})
          </button>
          {categories.map((cat) => {
            const count = products.filter((p) => p.category === cat).length;
            return (
              <button
                key={cat}
                onClick={() => selectCategory(cat)}
                className="px-4 py-1.5 rounded-full text-xs font-medium tracking-wide uppercase transition-all"
                style={
                  activeCategory === cat
                    ? { background: "var(--gold)", color: "#0a0a0a" }
                    : {
                        background: "var(--surface-2)",
                        color: "var(--muted)",
                        border: "1px solid var(--border)",
                      }
                }
              >
                {cat} ({count})
              </button>
            );
          })}
        </div>
      )}

      {visible.length === 0 ? (
        <p style={{ color: "var(--muted)" }}>
          No hay productos en esta categoría.
        </p>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {visible.map((product) => (
              <Link
                key={product.id}
                href={`/catalogo/producto/${product.id}`}
                className="group rounded-xl overflow-hidden transition-all"
                style={{
                  background: "var(--surface)",
                  border: "1px solid var(--border)",
                }}
              >
                {product.imageUrl ? (
                  <div className="relative w-full aspect-square overflow-hidden">
                    <Image
                      src={product.imageUrl}
                      alt={product.name}
                      fill
                      className="object-cover transition-transform duration-500 group-hover:scale-105"
                      sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
                    />
                  </div>
                ) : (
                  <div
                    className="w-full aspect-square flex items-center justify-center text-xs"
                    style={{
                      background: "var(--surface-2)",
                      color: "var(--muted)",
                    }}
                  >
                    Sin imagen
                  </div>
                )}
                <div className="p-3">
                  <h3
                    className="text-sm font-medium line-clamp-2"
                    style={{ color: "var(--foreground)" }}
                  >
                    {product.name}
                  </h3>
                  {product.category && (
                    <p
                      className="text-xs mt-0.5"
                      style={{ color: "var(--muted)" }}
                    >
                      {product.category}
                    </p>
                  )}
                  <div className="mt-2">
                    {product.listPrice != null && (
                      <p
                        className="text-base font-bold"
                        style={{ color: "var(--gold)" }}
                      >
                        {formatPrice(product.listPrice)}
                      </p>
                    )}
                    {product.cashPrice != null &&
                      product.cashPrice !== product.listPrice && (
                        <p className="text-xs" style={{ color: "#4ade80" }}>
                          Efectivo: {formatPrice(product.cashPrice)}
                        </p>
                      )}
                  </div>
                </div>
              </Link>
            ))}
          </div>

          {hasMore && (
            <div className="mt-10 text-center">
              <button
                onClick={() => setPage((p) => p + 1)}
                className="px-8 py-3 rounded text-sm font-medium tracking-widest uppercase transition-all border"
                style={{
                  borderColor: "var(--gold)",
                  color: "var(--gold)",
                  background: "transparent",
                }}
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
