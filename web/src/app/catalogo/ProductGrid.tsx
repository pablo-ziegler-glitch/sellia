"use client";

import { useMemo, useState, useEffect, useCallback } from "react";
import { useRouter, useSearchParams, usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import type { PublicProduct } from "@/lib/catalog";
import { formatPrice } from "@/lib/format";

const PAGE_SIZE = 24;

type SortKey = "name_asc" | "name_desc" | "price_asc" | "price_desc";

const SORT_LABELS: Record<SortKey, string> = {
  name_asc: "Nombre A-Z",
  name_desc: "Nombre Z-A",
  price_asc: "Menor precio",
  price_desc: "Mayor precio",
};

interface Props {
  products: PublicProduct[];
  showPrices?: boolean;
  showCashPrice?: boolean;
}

export default function ProductGrid({
  products,
  showPrices = true,
  showCashPrice = true,
}: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const activeCategory = searchParams.get("categoria") ?? "all";
  const activeSort = (searchParams.get("orden") ?? "name_asc") as SortKey;
  const searchQuery = searchParams.get("q") ?? "";

  const [page, setPage] = useState(1);
  const [localSearch, setLocalSearch] = useState(searchQuery);

  // Sincroniza el input local con la URL (ej: navegación hacia atrás)
  useEffect(() => {
    setLocalSearch(searchQuery);
  }, [searchQuery]);

  // Reset página al cambiar filtros
  useEffect(() => {
    setPage(1);
  }, [activeCategory, activeSort, searchQuery]);

  const updateParams = useCallback(
    (updates: Record<string, string | null>) => {
      const params = new URLSearchParams(searchParams.toString());
      for (const [key, value] of Object.entries(updates)) {
        if (value === null || value === "") {
          params.delete(key);
        } else {
          params.set(key, value);
        }
      }
      // Reset página al filtrar
      params.delete("page");
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [router, pathname, searchParams]
  );

  // Debounce búsqueda: actualiza la URL 350ms después de que el usuario deja de escribir
  useEffect(() => {
    const timer = setTimeout(() => {
      updateParams({ q: localSearch.trim() || null });
    }, 350);
    return () => clearTimeout(timer);
  }, [localSearch, updateParams]);

  const categories = useMemo(() => {
    const cats = Array.from(
      new Set(products.map((p) => p.category).filter(Boolean))
    ) as string[];
    return cats.sort();
  }, [products]);

  const filtered = useMemo(() => {
    let result = products;

    // Filtro por categoría
    if (activeCategory !== "all") {
      result = result.filter((p) => p.category === activeCategory);
    }

    // Filtro por búsqueda
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      result = result.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          (p.category ?? "").toLowerCase().includes(q) ||
          (p.description ?? "").toLowerCase().includes(q)
      );
    }

    return result;
  }, [products, activeCategory, searchQuery]);

  const sorted = useMemo(() => {
    const arr = [...filtered];
    switch (activeSort) {
      case "name_desc":
        arr.sort((a, b) => b.name.localeCompare(a.name, "es"));
        break;
      case "price_asc":
        arr.sort(
          (a, b) =>
            (a.listPrice ?? a.cashPrice ?? Infinity) -
            (b.listPrice ?? b.cashPrice ?? Infinity)
        );
        break;
      case "price_desc":
        arr.sort(
          (a, b) =>
            (b.listPrice ?? b.cashPrice ?? -Infinity) -
            (a.listPrice ?? a.cashPrice ?? -Infinity)
        );
        break;
      case "name_asc":
      default:
        // Ya viene ordenado por nombre del servidor
        break;
    }
    return arr;
  }, [filtered, activeSort]);

  const visible = sorted.slice(0, page * PAGE_SIZE);
  const hasMore = visible.length < sorted.length;

  const isFiltered = searchQuery.trim() !== "" || activeCategory !== "all";

  return (
    <div>
      {/* ── Controles ───────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        {/* Búsqueda */}
        <div className="relative flex-1">
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            style={{ color: "var(--muted)" }}
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            type="search"
            placeholder="Buscar productos..."
            value={localSearch}
            onChange={(e) => setLocalSearch(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 rounded-lg text-sm outline-none transition-all"
            style={{
              background: "var(--surface)",
              border: "1px solid var(--border)",
              color: "var(--foreground)",
            }}
            onFocus={(e) =>
              (e.currentTarget.style.borderColor = "var(--gold)")
            }
            onBlur={(e) =>
              (e.currentTarget.style.borderColor = "var(--border)")
            }
          />
        </div>

        {/* Ordenamiento */}
        <select
          value={activeSort}
          onChange={(e) =>
            updateParams({ orden: e.target.value === "name_asc" ? null : e.target.value })
          }
          className="px-3 py-2.5 rounded-lg text-sm outline-none cursor-pointer"
          style={{
            background: "var(--surface)",
            border: "1px solid var(--border)",
            color: "var(--foreground)",
            minWidth: "160px",
          }}
        >
          {(Object.keys(SORT_LABELS) as SortKey[]).map((key) => (
            <option key={key} value={key}>
              {SORT_LABELS[key]}
            </option>
          ))}
        </select>
      </div>

      {/* ── Categorías ──────────────────────────────────── */}
      {categories.length > 1 && (
        <div
          className="flex gap-2 mb-6 overflow-x-auto pb-1"
          style={{ scrollbarWidth: "none" }}
        >
          <FilterChip
            label={`Todos (${products.length})`}
            active={activeCategory === "all"}
            onClick={() => updateParams({ categoria: null })}
          />
          {categories.map((cat) => {
            const count = products.filter((p) => p.category === cat).length;
            return (
              <FilterChip
                key={cat}
                label={`${cat} (${count})`}
                active={activeCategory === cat}
                onClick={() => updateParams({ categoria: cat })}
              />
            );
          })}
        </div>
      )}

      {/* ── Contador / sin resultados ────────────────────── */}
      {isFiltered && (
        <div
          className="flex items-center justify-between mb-4 text-sm"
          style={{ color: "var(--muted)" }}
        >
          <span>
            {sorted.length === 0
              ? "Sin resultados"
              : `${sorted.length} producto${sorted.length !== 1 ? "s" : ""}`}
          </span>
          <button
            onClick={() =>
              updateParams({ q: null, categoria: null, orden: null })
            }
            className="text-xs underline transition-opacity hover:opacity-70"
            style={{ color: "var(--gold)" }}
          >
            Limpiar filtros
          </button>
        </div>
      )}

      {/* ── Grid ────────────────────────────────────────── */}
      {sorted.length === 0 ? (
        <div
          className="py-16 text-center rounded-xl"
          style={{
            background: "var(--surface)",
            border: "1px solid var(--border)",
          }}
        >
          <p className="text-2xl mb-2" style={{ color: "var(--gold)" }}>
            ◈
          </p>
          <p className="text-sm" style={{ color: "var(--muted)" }}>
            No encontramos productos{" "}
            {searchQuery ? `para "${searchQuery}"` : "en esta categoría"}.
          </p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {visible.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                showPrices={showPrices}
                showCashPrice={showCashPrice}
              />
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
                Ver más — {sorted.length - visible.length} restantes
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function FilterChip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="px-4 py-1.5 rounded-full text-xs font-medium tracking-wide uppercase transition-all whitespace-nowrap flex-shrink-0"
      style={
        active
          ? { background: "var(--gold)", color: "#0a0a0a" }
          : {
              background: "var(--surface-2)",
              color: "var(--muted)",
              border: "1px solid var(--border)",
            }
      }
    >
      {label}
    </button>
  );
}

function ProductCard({
  product,
  showPrices,
  showCashPrice,
}: {
  product: PublicProduct;
  showPrices: boolean;
  showCashPrice: boolean;
}) {
  return (
    <Link
      href={`/catalogo/producto/${product.id}`}
      className="group rounded-xl overflow-hidden transition-all block"
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
          className="w-full aspect-square flex flex-col items-center justify-center gap-1 text-xs"
          style={{
            background: "var(--surface-2)",
            color: "var(--muted)",
          }}
        >
          <svg
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            style={{ opacity: 0.4 }}
          >
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <path d="m21 15-5-5L5 21" />
          </svg>
        </div>
      )}
      <div className="p-3">
        <h3
          className="text-sm font-medium line-clamp-2 leading-snug"
          style={{ color: "var(--foreground)" }}
        >
          {product.name}
        </h3>
        {product.category && (
          <p className="text-xs mt-0.5" style={{ color: "var(--muted)" }}>
            {product.category}
          </p>
        )}
        {showPrices && (
          <div className="mt-2 space-y-0.5">
            {product.listPrice != null && (
              <p className="text-base font-bold" style={{ color: "var(--gold)" }}>
                {formatPrice(product.listPrice)}
              </p>
            )}
            {showCashPrice &&
              product.cashPrice != null &&
              product.cashPrice !== product.listPrice && (
                <p className="text-xs" style={{ color: "#4ade80" }}>
                  Efectivo: {formatPrice(product.cashPrice)}
                </p>
              )}
          </div>
        )}
      </div>
    </Link>
  );
}
