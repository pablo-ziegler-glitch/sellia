"use client";

import { use } from "react";
import Link from "next/link";
import { useCart } from "../CartContext";
import { formatPrice } from "@/lib/format";

type Props = { params: Promise<{ tenantId: string }> };

export default function CartPage({ params }: Props) {
  const { tenantId } = use(params);
  const { items, removeItem, setQuantity, totalAmount } = useCart();

  if (items.length === 0) {
    return (
      <div className="text-center py-20">
        <p className="text-gray-400 text-lg mb-6">Tu carrito está vacío.</p>
        <Link
          href={`/tienda/${tenantId}`}
          className="inline-block bg-blue-600 text-white font-medium rounded-lg px-6 py-3 hover:bg-blue-700 transition-colors"
        >
          Volver a la tienda
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <Link
          href={`/tienda/${tenantId}`}
          className="text-sm text-blue-600 hover:underline"
        >
          &larr; Seguir comprando
        </Link>
        <h1 className="text-xl font-bold">Tu carrito</h1>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 divide-y divide-gray-100">
        {items.map((item) => (
          <div key={item.productId} className="flex items-center gap-4 p-4">
            <div className="flex-1 min-w-0">
              <p className="font-medium text-sm line-clamp-2">{item.name}</p>
              <p className="text-xs text-gray-500 mt-0.5">
                {formatPrice(item.price)} c/u
              </p>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <button
                onClick={() => setQuantity(item.productId, item.quantity - 1)}
                className="w-7 h-7 rounded-full border border-gray-300 flex items-center justify-center text-gray-600 hover:bg-gray-50 transition-colors text-sm font-bold"
              >
                -
              </button>
              <span className="w-6 text-center text-sm font-medium">
                {item.quantity}
              </span>
              <button
                onClick={() => setQuantity(item.productId, item.quantity + 1)}
                className="w-7 h-7 rounded-full border border-gray-300 flex items-center justify-center text-gray-600 hover:bg-gray-50 transition-colors text-sm font-bold"
              >
                +
              </button>
            </div>

            <p className="w-24 text-right text-sm font-bold text-blue-600 shrink-0">
              {formatPrice(item.price * item.quantity)}
            </p>

            <button
              onClick={() => removeItem(item.productId)}
              className="text-gray-300 hover:text-red-500 transition-colors text-lg leading-none shrink-0"
              aria-label="Eliminar"
            >
              &times;
            </button>
          </div>
        ))}
      </div>

      <div className="mt-4 bg-white rounded-lg border border-gray-200 p-4">
        <div className="flex justify-between items-center text-lg font-bold">
          <span>Total</span>
          <span className="text-blue-600">{formatPrice(totalAmount)}</span>
        </div>
      </div>

      <Link
        href={`/tienda/${tenantId}/pedido`}
        className="mt-4 flex items-center justify-center w-full bg-blue-600 text-white font-semibold rounded-lg px-6 py-4 hover:bg-blue-700 transition-colors text-base"
      >
        Confirmar pedido
      </Link>
    </div>
  );
}
