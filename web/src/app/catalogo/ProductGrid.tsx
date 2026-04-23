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

function parseActiveCategories(params: { get: (key: string) => string | null }): string[] {
  const csv = params.get("categorias")?.trim();
  if (csv) {
    return Array.from(
      new Set(
        csv
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean)
      )
    );
  }
  const legacy = params.get("categoria")?.trim();
  return legacy ? [legacy] : [];
}

function primaryProductImage(product: PublicProduct): string | null {
  return product.imageUrl || product.imageUrls[0] || null;
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

  const activeSort = (searchParams.get("orden") ?? "name_asc") as SortKey;
  const activeCategories = parseActiveCategories(searchParams);
  const searchQuery = searchParams.get("q") ?? "";
  const minPrice = searchParams.get("precioMin") ?? "";
  const maxPrice = searchParams.get("precioMax") ?? "";

  const [page, setPage] = useState(1);
  const [localSearch, setLocalSearch] = useState(searchQuery);
  const [localMinPrice, setLocalMinPrice] = useState(minPrice);
  const [localMaxPrice, setLocalMaxPrice] = useState(maxPrice);
  const [selectedProduct, setSelectedProduct] = useState<PublicProduct | null>(null);

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
  }, [activeSort, searchQuery, minPrice, maxPrice, activeCategories.join("|")]);

  useEffect(() => {
    document.body.style.overflow = selectedProduct ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [selectedProduct]);

  useEffect(() => {
    if (!selectedProduct) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setSelectedProduct(null);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [selectedProduct]);

  const updateParams = useCallback(
    (updates: Record<string, string | null>) => {
      const params = new URLSearchParams(searchParams.toString());
      for (const [key, value] of Object.entries(updates)) {
        if (!value) {
          params.delete(key);
        } else {
          params.set(key, value);
        }
      }
      const nextQuery = params.toString();
      router.replace(nextQuery ? `${pathname}?${nextQuery}` : pathname, { scroll: false });
    },
    [router, pathname, searchParams]
  );

  useEffect(() => {
    const timer = setTimeout(() => {
      updateParams({ q: localSearch.trim() || null });
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch, updateParams]);

  const categories = useMemo(() => {
    return Array.from(new Set(products.map((product) => product.category).filter(Boolean) as string[]))
      .sort((a, b) => a.localeCompare(b, "es"));
  }, [products]);

  const filtered = useMemo(() => {
    const min = Number(minPrice);
    const max = Number(maxPrice);
    const hasMin = Number.isFinite(min) && min >= 0;
    const hasMax = Number.isFinite(max) && max >= 0;
    const categorySet = new Set(activeCategories);

    return products.filter((product) => {
      if (categorySet.size > 0) {
        const category = product.category ?? "";
        if (!categorySet.has(category)) return false;
      }

      const q = searchQuery.trim().toLowerCase();
      if (q) {
        const matches =
          product.name.toLowerCase().includes(q) ||
          (product.category ?? "").toLowerCase().includes(q) ||
          (product.description ?? "").toLowerCase().includes(q);
        if (!matches) return false;
      }

      const price = product.listPrice ?? product.cashPrice;
      if (price == null) return !(hasMin || hasMax);
      if (hasMin && price < min) return false;
      if (hasMax && price > max) return false;
      return true;
    });
  }, [products, activeCategories, searchQuery, minPrice, maxPrice]);

  const sorted = useMemo(() => {
    const items = [...filtered];
    switch (activeSort) {
      case "name_desc":
        items.sort((a, b) => b.name.localeCompare(a.name, "es"));
        break;
      case "price_asc":
        items.sort(
          (a, b) =>
            (a.listPrice ?? a.cashPrice ?? Number.POSITIVE_INFINITY) -
            (b.listPrice ?? b.cashPrice ?? Number.POSITIVE_INFINITY)
        );
        break;
      case "price_desc":
        items.sort(
          (a, b) =>
            (b.listPrice ?? b.cashPrice ?? Number.NEGATIVE_INFINITY) -
            (a.listPrice ?? a.cashPrice ?? Number.NEGATIVE_INFINITY)
        );
        break;
      default:
        items.sort((a, b) => a.name.localeCompare(b.name, "es"));
    }
    return items;
  }, [filtered, activeSort]);

  const visible = sorted.slice(0, page * PAGE_SIZE);
  const hasMore = visible.length < sorted.length;
  const isFiltered =
    searchQuery.trim() !== "" ||
    activeCategories.length > 0 ||
    minPrice.trim() !== "" ||
    maxPrice.trim() !== "" ||
    activeSort !== "name_asc";

  const toggleCategory = (category: string) => {
    const current = new Set(activeCategories);
    if (current.has(category)) current.delete(category);
    else current.add(category);
    const updated = Array.from(current).sort((a, b) => a.localeCompare(b, "es"));
    updateParams({
      categorias: updated.length ? updated.join(",") : null,
      categoria: null,
    });
  };

  const clearFilters = () =>
    updateParams({
      q: null,
      categorias: null,
      categoria: null,
      orden: null,
      precioMin: null,
      precioMax: null,
    });

  return (
    <>
      <section className="space-y-6">
        <div className="mb-2 flex flex-col justify-between gap-4 md:mb-6 md:flex-row md:items-end">
          <div>
            <h1 className="font-plus-jakarta text-4xl font-extrabold tracking-tight text-[var(--foreground)] md:text-5xl">
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
              onChange={(event) => setLocalSearch(event.target.value)}
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
                    onClick={clearFilters}
                    className="text-sm font-medium text-[var(--primary-container)] hover:underline"
                  >
                    Limpiar
                  </button>
                )}
              </div>

              {(activeCategories.length > 0 || searchQuery.trim()) && (
                <div className="flex flex-wrap gap-2">
                  {activeCategories.map((category) => (
                    <FilterChip
                      key={category}
                      label={category}
                      onClick={() => toggleCategory(category)}
                    />
                  ))}
                  {searchQuery.trim() !== "" && (
                    <FilterChip label={`"${searchQuery.trim()}"`} onClick={() => updateParams({ q: null })} />
                  )}
                </div>
              )}

              <div>
                <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-[var(--muted)]">
                  Categoría
                </h3>
                <div className="space-y-2">
                  {categories.length === 0 ? (
                    <p className="text-sm text-[var(--outline)]">Sin categorías disponibles</p>
                  ) : (
                    categories.map((category) => {
                      const checked = activeCategories.includes(category);
                      return (
                        <label
                          key={category}
                          className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-1.5 transition-colors hover:bg-[var(--surface-high)]"
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleCategory(category)}
                            className="h-4 w-4 rounded border-[var(--border)] text-[var(--primary)] focus:ring-[var(--primary)]"
                          />
                          <span className="text-sm text-[var(--foreground)]">{category}</span>
                        </label>
                      );
                    })
                  )}
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
                    onChange={(event) => setLocalMinPrice(event.target.value)}
                    onBlur={() => updateParams({ precioMin: localMinPrice.trim() || null })}
                    className="w-full rounded-lg border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] px-3 py-2 text-sm outline-none focus:border-[var(--primary)]"
                  />
                  <span className="text-[var(--outline)]">-</span>
                  <input
                    type="number"
                    min={0}
                    value={localMaxPrice}
                    placeholder="Max"
                    onChange={(event) => setLocalMaxPrice(event.target.value)}
                    onBlur={() => updateParams({ precioMax: localMaxPrice.trim() || null })}
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
                  onChange={(event) =>
                    updateParams({
                      orden: event.target.value === "name_asc" ? null : event.target.value,
                    })
                  }
                  className="w-full rounded-lg border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] px-3 py-2 text-sm outline-none focus:border-[var(--primary)]"
                >
                  {(Object.keys(SORT_LABELS) as SortKey[]).map((sortKey) => (
                    <option key={sortKey} value={sortKey}>
                      {SORT_LABELS[sortKey]}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </aside>

          <div className="flex-1 space-y-5">
            {sorted.length === 0 ? (
              <div className="rounded-2xl bg-[var(--surface-low)] px-6 py-20 text-center">
                <p className="font-plus-jakarta text-2xl font-bold text-[var(--foreground)]">
                  Sin resultados
                </p>
                <p className="mt-2 text-sm text-[var(--muted)]">
                  No encontramos productos con los filtros actuales.
                </p>
                {isFiltered && (
                  <button
                    type="button"
                    onClick={clearFilters}
                    className="mt-4 text-sm font-medium text-[var(--primary-container)] underline"
                  >
                    Limpiar filtros
                  </button>
                )}
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
                      onOpenDetail={() => setSelectedProduct(product)}
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
                      onOpenDetail={() => setSelectedProduct(product)}
                    />
                  ))}
                </div>

                {hasMore && (
                  <div className="pt-4 text-center">
                    <button
                      type="button"
                      onClick={() => setPage((current) => current + 1)}
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

      {selectedProduct && (
        <ProductDetailModal
          product={selectedProduct}
          showPrices={showPrices}
          showCashPrice={showCashPrice}
          onClose={() => setSelectedProduct(null)}
        />
      )}
    </>
  );
}

function FilterChip({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-1 rounded-md bg-[var(--primary-fixed)] px-3 py-1 text-xs font-semibold text-[var(--on-primary-fixed)]"
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
  onOpenDetail,
}: {
  product: PublicProduct;
  showPrices: boolean;
  showCashPrice: boolean;
  storeName: string;
  onOpenDetail: () => void;
}) {
  const productImage = primaryProductImage(product);
  return (
    <article className="group overflow-hidden rounded-2xl bg-[var(--surface-pop)] soft-pop transition-shadow hover:shadow-[0_10px_28px_rgba(0,68,170,0.1)]">
      <div className="flex flex-col sm:flex-row">
        <button
          type="button"
          onClick={onOpenDetail}
          className="relative aspect-square w-full shrink-0 overflow-hidden bg-[var(--surface-high)] sm:w-64 md:w-80"
        >
          {productImage ? (
            <Image
              src={productImage}
              alt={product.name}
              fill
              className="object-cover transition-transform duration-500 group-hover:scale-105"
              sizes="(max-width: 1024px) 100vw, 320px"
            />
          ) : (
            <div className="absolute inset-0 bg-[var(--surface-highest)]" />
          )}
        </button>

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
              <button
                type="button"
                onClick={onOpenDetail}
                className="rounded-full bg-[var(--surface-high)] px-6 py-3 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--surface-highest)]"
              >
                Ver Detalle
              </button>
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
  onOpenDetail,
}: {
  product: PublicProduct;
  showPrices: boolean;
  showCashPrice: boolean;
  onOpenDetail: () => void;
}) {
  const productImage = primaryProductImage(product);
  return (
    <button
      type="button"
      onClick={onOpenDetail}
      className="group block overflow-hidden rounded-xl bg-[var(--surface-pop)] text-left soft-pop"
    >
      <div className="relative aspect-square bg-[var(--surface-high)]">
        {productImage ? (
          <Image
            src={productImage}
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
    </button>
  );
}

function ProductDetailModal({
  product,
  showPrices,
  showCashPrice,
  onClose,
}: {
  product: PublicProduct;
  showPrices: boolean;
  showCashPrice: boolean;
  onClose: () => void;
}) {
  const images = useMemo(() => {
    const all = [product.imageUrl, ...product.imageUrls].filter((url): url is string => Boolean(url));
    return Array.from(new Set(all));
  }, [product.imageUrl, product.imageUrls]);
  const [activeImage, setActiveImage] = useState<string | null>(images[0] ?? null);
  const [lightboxOpen, setLightboxOpen] = useState(false);

  useEffect(() => {
    setActiveImage(images[0] ?? null);
    setLightboxOpen(false);
  }, [product.id, images]);

  return (
    <>
      <div
        className="fixed inset-0 z-[100] bg-black/55 backdrop-blur-[2px]"
        onClick={onClose}
        role="presentation"
      />
      <div className="fixed inset-0 z-[101] grid place-items-center p-4">
        <section className="max-h-[92vh] w-full max-w-5xl overflow-hidden rounded-2xl bg-[var(--surface-pop)] shadow-2xl">
          <div className="flex items-center justify-between border-b border-[color:color-mix(in_srgb,var(--border)_35%,transparent)] px-4 py-3 md:px-6">
            <h2 className="line-clamp-1 pr-4 font-plus-jakarta text-lg font-bold md:text-xl">
              {product.name}
            </h2>
            <button
              type="button"
              onClick={onClose}
              className="rounded-full p-2 text-[var(--foreground)] transition-colors hover:bg-[var(--surface-low)]"
              aria-label="Cerrar detalle"
            >
              <span className="material-symbols-outlined">close</span>
            </button>
          </div>

          <div className="grid max-h-[calc(92vh-62px)] grid-cols-1 overflow-auto md:grid-cols-2">
            <div className="space-y-3 bg-[var(--surface-low)] p-4 md:p-6">
              <button
                type="button"
                onClick={() => activeImage && setLightboxOpen(true)}
                className="relative block aspect-square w-full overflow-hidden rounded-xl bg-[var(--surface-high)]"
              >
                {activeImage ? (
                  <Image
                    src={activeImage}
                    alt={product.name}
                    fill
                    className="object-cover"
                    sizes="(max-width: 1024px) 100vw, 45vw"
                  />
                ) : (
                  <div className="absolute inset-0 bg-[var(--surface-highest)]" />
                )}
              </button>
              {images.length > 1 && (
                <div className="grid grid-cols-5 gap-2">
                  {images.map((url) => (
                    <button
                      key={url}
                      type="button"
                      onClick={() => setActiveImage(url)}
                      className={`relative aspect-square overflow-hidden rounded-md border ${
                        activeImage === url
                          ? "border-[var(--primary)]"
                          : "border-[color:color-mix(in_srgb,var(--border)_40%,transparent)]"
                      }`}
                    >
                      <Image
                        src={url}
                        alt={product.name}
                        fill
                        className="object-cover"
                        sizes="96px"
                      />
                    </button>
                  ))}
                </div>
              )}
              <button
                type="button"
                onClick={() => activeImage && setLightboxOpen(true)}
                disabled={!activeImage}
                className="w-full rounded-full border border-[var(--primary)] px-4 py-2 text-sm font-medium text-[var(--primary)] transition-colors hover:bg-[var(--primary-fixed)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                Agrandar imagen
              </button>
            </div>

            <div className="space-y-4 p-4 md:p-6">
              {product.category && (
                <p className="inline-flex rounded-full bg-[var(--secondary-container)] px-3 py-1 text-xs font-bold uppercase tracking-wide text-[var(--on-secondary-container)]">
                  {product.category}
                </p>
              )}
              {showPrices && product.listPrice != null && (
                <p className="font-plus-jakarta text-3xl font-extrabold text-[var(--primary)]">
                  {formatPrice(product.listPrice)}
                </p>
              )}
              {showPrices &&
                showCashPrice &&
                product.cashPrice != null &&
                product.cashPrice !== product.listPrice && (
                  <p className="text-sm text-[var(--secondary)]">
                    Efectivo: {formatPrice(product.cashPrice)}
                  </p>
                )}
              <div>
                <h3 className="font-plus-jakarta text-sm font-bold uppercase tracking-wider text-[var(--muted)]">
                  Descripción
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-[var(--foreground)] md:text-base">
                  {product.description?.trim() || "Este producto todavía no tiene descripción cargada."}
                </p>
              </div>

              <div className="flex flex-wrap gap-3 pt-2">
                <Link
                  href={`/catalogo/producto/${product.id}`}
                  className="rounded-full bg-[var(--primary)] px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[var(--primary-container)]"
                >
                  Ver ficha completa
                </Link>
                <button
                  type="button"
                  onClick={onClose}
                  className="rounded-full bg-[var(--surface-high)] px-5 py-2.5 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--surface-highest)]"
                >
                  Seguir explorando
                </button>
              </div>
            </div>
          </div>
        </section>
      </div>

      {lightboxOpen && activeImage && (
        <>
          <div
            className="fixed inset-0 z-[120] bg-black/90"
            onClick={() => setLightboxOpen(false)}
            role="presentation"
          />
          <div className="fixed inset-0 z-[121] grid place-items-center p-4">
            <div className="relative h-[90vh] w-full max-w-6xl">
              <Image
                src={activeImage}
                alt={product.name}
                fill
                className="object-contain"
                sizes="100vw"
              />
              <button
                type="button"
                onClick={() => setLightboxOpen(false)}
                className="absolute right-2 top-2 rounded-full bg-black/65 p-2 text-white transition-colors hover:bg-black/80"
                aria-label="Cerrar imagen ampliada"
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>
          </div>
        </>
      )}
    </>
  );
}
