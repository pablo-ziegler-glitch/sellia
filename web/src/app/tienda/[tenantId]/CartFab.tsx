"use client";

import Link from "next/link";
import { useCart } from "./CartContext";
import { formatPrice } from "@/lib/format";

export default function CartFab({ tenantId }: { tenantId: string }) {
  const { totalItems, totalAmount } = useCart();

  if (totalItems === 0) return null;

  return (
    <Link
      href={`/tienda/${tenantId}/carrito`}
      className="fixed bottom-6 right-6 bg-blue-600 text-white rounded-full shadow-lg px-5 py-3.5 flex items-center gap-3 hover:bg-blue-700 transition-colors z-50 font-medium"
    >
      <span className="text-base leading-none">
        {totalItems} {totalItems === 1 ? "item" : "items"}
      </span>
      <span className="w-px h-4 bg-blue-400" />
      <span className="text-base leading-none">{formatPrice(totalAmount)}</span>
    </Link>
  );
}
