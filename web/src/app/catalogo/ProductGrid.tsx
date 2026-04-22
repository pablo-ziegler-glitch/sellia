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
  storeName?: string;
}

export default function ProductGrid({
  products,
  showPrices = true,
  showCashPrice = true,
  storeName = "VALKIRJA",
}: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const activeCategory = searchParams.get("categoria") ?? "all";
  const activeSort = (searchParams.get("orden") ?? "name_asc") as SortKey;
  const searchQuery = searchParams.get("q") ?? "";
  const minPrice = searchParams.get("precioMin") ?? "";
  const maxPrice = searchParams.get("precioMax") ?? "";

  const [page, setPage] = useState(1);
  const [localSearch, setLocalSearch] = useState(searchQuery);
  const [localMinPrice, setLocalMinPrice] = useState(minPrice);
  const [localMaxPrice, setLocalMaxPrice] = useState(maxPrice);

  useEffect(() => {
    setLocalSearch(searchQuery);
  }, [searchQuery]);

  useEffect(() => {
    setLocalMinPrice(minPrice);
  }, [minPrice]);

  useEffect(() => {
    setLocalMaxPrice(maxPrice);
  }, [maxPrice]);

  useEffect(() => {
    setPage(1);
  }, [activeCategory, activeSort, searchQuery, minPrice, maxPrice]);

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
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [router, pathname, searchParams]
  );

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
    return cats.sort((a, b) => a.localeCompare(b, "es"));
  }, [products]);

  const filtered = useMemo(() => {
    const min = Number(localMinPrice);
    const max = Number(localMaxPrice);
    const hasMin = Number.isFinite(min) && min >= 0;
    const hasMax = Number.isFinite(max) && max >= 0;

    return products.filter((p) => {
      if (activeCategory !== "all" && p.category !== activeCategory) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.trim().toLowerCase();
        const matches =
          p.name.toLowerCase().includes(q) ||
          (p.category ?? "").toLowerCase().includes(q) ||
          (p.description ?? "").toLowerCase().includes(q);
        if (!matches) return false;
      }

      const price = p.listPrice ?? p.cashPrice;
      if (price == null) return !(hasMin || hasMax);
      if (hasMin && price < min) return false;
      if (hasMax && price > max) return false;
      return true;
    });
  }, [products, activeCategory, searchQuery, localMinPrice, localMaxPrice]);

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
        break;
    }
    return arr;
  }, [filtered, activeSort]);

  const visible = sorted.slice(0, page * PAGE_SIZE);
  const hasMore = visible.length < sorted.length;
  const isFiltered =
    searchQuery.trim() !== "" ||
    activeCategory !== "all" ||
    minPrice !== "" ||
    maxPrice !== "";

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <h1 className="font-plus-jakarta text-4xl font-extrabold tracking-tight text-[var(--foreground)]">
            Catálogo de Productos
          </h1>
          <p className="mt-2 text-[var(--muted)]">
            Mostrando {sorted.length} resultado{sorted.length === 1 ? "" : "s"}
          </p>
        </div>
        <div className="relative w-full md:w-96">
          <span className="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-[var(--outline)]">
            search
          </span>
          <input
            type="search"
            placeholder="Buscar productos..."
            value={localSearch}
            onChange={(e) => setLocalSearch(e.target.value)}
            className="w-full rounded-full border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] py-3 pl-12 pr-4 text-sm text-[var(--foreground)] outline-none transition-colors focus:border-[var(--primary)]"
          />
        </div>
      </div>

      <div className="flex flex-col gap-8 lg:flex-row lg:items-start">
        <aside className="w-full lg:w-72 lg:shrink-0">
          <div className="space-y-7 rounded-2xl bg-[var(--surface-low)] p-6 lg:sticky lg:top-28">
            <div className="flex items-center justify-between">
              <h2 className="font-plus-jakarta text-xl font-bold">Filtros</h2>
              {isFiltered && (
                <button
                  type="button"
                  onClick={() =>
                    updateParams({
                      q: null,
                      categoria: null,
                      orden: null,
                      precioMin: null,
                      precioMax: null,
                    })
                  }
                  className="text-sm font-medium text-[var(--primary-container)] hover:underline"
                >
                  Limpiar
                </button>
              )}
            </div>

            <div className="flex flex-wrap gap-2">
              {activeCategory !== "all" && (
                <FilterChip
                  label={activeCategory}
                  active
                  onClick={() => updateParams({ categoria: null })}
                />
              )}
              {searchQuery.trim() !== "" && (
                <FilterChip
                  label={`"${searchQuery.trim()}"`}
                  active
                  onClick={() => updateParams({ q: null })}
                />
              )}
            </div>

            <div>
              <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-[var(--muted)]">
                Categoría
              </h3>
              <div className="space-y-2">
                <button
                  type="button"
                  onClick={() => updateParams({ categoria: null })}
                  className={`w-full rounded-lg px-3 py-2 text-left text-sm transition-colors ${
                    activeCategory === "all"
                      ? "bg-[var(--primary-fixed)] text-[var(--on-primary-fixed)]"
                      : "bg-[var(--surface-pop)] text-[var(--foreground)] hover:bg-[var(--surface-high)]"
                  }`}
                >
                  Todas
                </button>
                {categories.map((cat) => (
                  <button
                    key={cat}
                    type="button"
                    onClick={() => updateParams({ categoria: cat })}
                    className={`w-full rounded-lg px-3 py-2 text-left text-sm transition-colors ${
                      activeCategory === cat
                        ? "bg-[var(--primary-fixed)] text-[var(--on-primary-fixed)]"
                        : "bg-[var(--surface-pop)] text-[var(--foreground)] hover:bg-[var(--surface-high)]"
                    }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-[var(--muted)]">
                Rango de precio
              </h3>
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  min={0}
                  value={localMinPrice}
                  placeholder="Min"
                  onChange={(e) => setLocalMinPrice(e.target.value)}
                  onBlur={() =>
                    updateParams({
                      precioMin: localMinPrice.trim() || null,
                    })
                  }
                  className="w-full rounded-lg border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] px-3 py-2 text-sm outline-none focus:border-[var(--primary)]"
                />
                <span className="text-[var(--outline)]">-</span>
                <input
                  type="number"
                  min={0}
                  value={localMaxPrice}
                  placeholder="Max"
                  onChange={(e) => setLocalMaxPrice(e.target.value)}
                  onBlur={() =>
                    updateParams({
                      precioMax: localMaxPrice.trim() || null,
                    })
                  }
                  className="w-full rounded-lg border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] px-3 py-2 text-sm outline-none focus:border-[var(--primary)]"
                />
              </div>
            </div>

            <div>
              <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-[var(--muted)]">
                Orden
              </h3>
              <select
                value={activeSort}
                onChange={(e) =>
                  updateParams({
                    orden: e.target.value === "name_asc" ? null : e.target.value,
                  })
                }
                className="w-full rounded-lg border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] px-3 py-2 text-sm outline-none focus:border-[var(--primary)]"
              >
                {(Object.keys(SORT_LABELS) as SortKey[]).map((key) => (
                  <option key={key} value={key}>
                    {SORT_LABELS[key]}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </aside>

        <div className="flex-1 space-y-5">
          {isFiltered && (
            <div className="flex items-center justify-between text-sm text-[var(--muted)]">
              <span>
                {sorted.length === 0
                  ? "Sin resultados"
                  : `${sorted.length} producto${sorted.length === 1 ? "" : "s"}`}
              </span>
              <button
                type="button"
                onClick={() =>
                  updateParams({
                    q: null,
                    categoria: null,
                    orden: null,
                    precioMin: null,
                    precioMax: null,
                  })
                }
                className="text-[var(--primary-container)] underline"
              >
                Limpiar filtros
              </button>
            </div>
          )}

          {sorted.length === 0 ? (
            <div className="rounded-2xl bg-[var(--surface-low)] px-6 py-20 text-center">
              <p className="font-plus-jakarta text-2xl font-bold text-[var(--foreground)]">
                Sin resultados
              </p>
              <p className="mt-2 text-sm text-[var(--muted)]">
                No encontramos productos {searchQuery ? `para "${searchQuery}"` : "con los filtros aplicados"}.
              </p>
            </div>
          ) : (
            <>
              <div className="hidden lg:flex lg:flex-col lg:gap-6">
                {visible.map((product) => (
                  <DesktopProductCard
                    key={product.id}
                    product={product}
                    showPrices={showPrices}
                    showCashPrice={showCashPrice}
                    storeName={storeName}
                  />
                ))}
              </div>

              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:hidden">
                {visible.map((product) => (
                  <MobileProductCard
                    key={product.id}
                    product={product}
                    showPrices={showPrices}
                    showCashPrice={showCashPrice}
                  />
                ))}
              </div>

              {hasMore && (
                <div className="pt-4 text-center">
                  <button
                    type="button"
                    onClick={() => setPage((p) => p + 1)}
                    className="rounded-full border border-[var(--primary)] px-8 py-3 text-sm font-medium uppercase tracking-widest text-[var(--primary)] transition-colors hover:bg-[var(--primary-fixed)]"
                  >
                    Ver más - {sorted.length - visible.length} restantes
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </section>
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
      type="button"
      onClick={onClick}
      className={`inline-flex items-center gap-1 rounded-md px-3 py-1 text-xs font-semibold ${
        active
          ? "bg-[var(--primary-fixed)] text-[var(--on-primary-fixed)]"
          : "bg-[var(--surface-high)] text-[var(--foreground)]"
      }`}
    >
      {label}
      <span className="material-symbols-outlined text-sm">close</span>
    </button>
  );
}

function DesktopProductCard({
  product,
  showPrices,
  showCashPrice,
  storeName,
}: {
  product: PublicProduct;
  showPrices: boolean;
  showCashPrice: boolean;
  storeName: string;
}) {
  return (
    <article className="group overflow-hidden rounded-2xl bg-[var(--surface-pop)] soft-pop transition-shadow hover:shadow-[0_12px_38px_rgba(0,68,170,0.12)]">
      <div className="flex flex-col sm:flex-row">
        <div className="relative aspect-square w-full shrink-0 overflow-hidden bg-[var(--surface-high)] sm:w-64 md:w-80">
          {product.imageUrl ? (
            <Image
              src={product.imageUrl}
              alt={product.name}
              fill
              className="object-cover transition-transform duration-500 group-hover:scale-105"
              sizes="(max-width: 1024px) 100vw, 320px"
            />
          ) : (
            <div className="absolute inset-0 bg-[var(--surface-highest)]" />
          )}
          {product.category && (
            <span className="absolute left-4 top-4 rounded-full bg-[var(--secondary-container)] px-3 py-1 text-xs font-bold uppercase tracking-wide text-[var(--on-secondary-container)]">
              {product.category}
            </span>
          )}
        </div>

        <div className="flex flex-1 flex-col justify-center p-6 md:p-8">
          <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div className="flex-1">
              <p className="mb-1 text-sm text-[var(--outline)]">{storeName}</p>
              <h3 className="font-plus-jakarta text-xl font-bold leading-tight text-[var(--foreground)] md:text-2xl">
                {product.name}
              </h3>
              {showPrices && product.listPrice != null && (
                <p className="mt-3 font-plus-jakarta text-2xl font-extrabold text-[var(--primary)]">
                  {formatPrice(product.listPrice)}
                </p>
              )}
              {showPrices &&
                showCashPrice &&
                product.cashPrice != null &&
                product.cashPrice !== product.listPrice && (
                  <p className="mt-1 text-sm text-[var(--secondary)]">
                    Efectivo: {formatPrice(product.cashPrice)}
                  </p>
                )}
            </div>
            <div className="flex items-center gap-3">
              <Link
                href={`/catalogo/producto/${product.id}`}
                className="rounded-full bg-[var(--surface-high)] px-6 py-3 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--surface-highest)]"
              >
                Ver Detalle
              </Link>
              <Link
                href={`/catalogo/producto/${product.id}`}
                aria-label={`Ver ${product.name}`}
                className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--primary)] text-white transition-colors hover:bg-[var(--primary-container)]"
              >
                <span
                  className="material-symbols-outlined text-[22px]"
                  style={{ fontVariationSettings: "'FILL' 1" }}
                >
                  chat
                </span>
              </Link>
            </div>
          </div>
        </div>
      </div>
    </article>
  );
}

function MobileProductCard({
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
      className="group block overflow-hidden rounded-xl bg-[var(--surface-pop)] soft-pop"
    >
      <div className="relative aspect-square bg-[var(--surface-high)]">
        {product.imageUrl ? (
          <Image
            src={product.imageUrl}
            alt={product.name}
            fill
            className="object-cover transition-transform duration-500 group-hover:scale-105"
            sizes="(max-width: 1024px) 100vw, 50vw"
          />
        ) : (
          <div className="absolute inset-0 bg-[var(--surface-highest)]" />
        )}
      </div>
      <div className="space-y-1 p-4">
        <h3 className="line-clamp-2 font-plus-jakarta text-lg font-bold text-[var(--foreground)]">
          {product.name}
        </h3>
        {product.category && (
          <p className="text-xs text-[var(--muted)]">{product.category}</p>
        )}
        {showPrices && product.listPrice != null && (
          <p className="pt-1 font-plus-jakarta text-lg font-bold text-[var(--primary)]">
            {formatPrice(product.listPrice)}
          </p>
        )}
        {showPrices &&
          showCashPrice &&
          product.cashPrice != null &&
          product.cashPrice !== product.listPrice && (
            <p className="text-xs text-[var(--secondary)]">
              Efectivo: {formatPrice(product.cashPrice)}
            </p>
          )}
      </div>
    </Link>
  );
}
