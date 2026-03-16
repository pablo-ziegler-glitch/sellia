"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import Image from "next/image";
import type { PublicProduct } from "@/lib/catalog";
import { formatPrice } from "@/lib/format";
import AddToCartButton from "./AddToCartButton";

interface Props {
  tenantId: string;
  products: PublicProduct[];
}

export default function ProductGrid({ tenantId, products }: Props) {
  const categories = useMemo(() => {
    const cats = Array.from(
      new Set(products.map((p) => p.category).filter(Boolean))
    ) as string[];
    return cats.sort();
  }, [products]);

  const [activeCategory, setActiveCategory] = useState<string>("all");

  const filtered = useMemo(
    () =>
      activeCategory === "all"
        ? products
        : products.filter((p) => p.category === activeCategory),
    [products, activeCategory]
  );

  return (
    <div>
      {categories.length > 1 && (
        <div className="flex flex-wrap gap-2 mb-5">
          <button
            onClick={() => setActiveCategory("all")}
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
                onClick={() => setActiveCategory(cat)}
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

      {filtered.length === 0 ? (
        <p className="text-gray-400">No hay productos en esta categoría.</p>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {filtered.map((product) => (
            <div
              key={product.id}
              className="bg-white rounded-lg border border-gray-200 overflow-hidden hover:shadow-md transition-shadow flex flex-col"
            >
              <Link href={`/tienda/${tenantId}/producto/${product.id}`}>
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

              {product.listPrice != null && (
                <div className="px-3 pb-3 mt-auto">
                  <AddToCartButton
                    productId={product.id}
                    name={product.name}
                    price={product.listPrice}
                  />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
