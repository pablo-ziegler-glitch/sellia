"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useCart } from "../CartContext";
import { formatPrice } from "@/lib/format";

type Props = { params: Promise<{ tenantId: string }> };

type PaymentMethod = "efectivo" | "transferencia" | "mercado_pago";

const PAYMENT_LABELS: Record<PaymentMethod, string> = {
  efectivo: "Efectivo",
  transferencia: "Transferencia bancaria",
  mercado_pago: "MercadoPago",
};

export default function CheckoutPage({ params }: Props) {
  const { tenantId } = use(params);
  const { items, totalAmount, clearCart } = useCart();

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("efectivo");
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [orderId, setOrderId] = useState<string | null>(null);

  if (items.length === 0 && !orderId) {
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

  if (orderId) {
    return (
      <div className="max-w-md mx-auto text-center py-16">
        <div className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center mx-auto mb-4">
          <svg
            className="w-8 h-8 text-green-600"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M5 13l4 4L19 7"
            />
          </svg>
        </div>
        <h1 className="text-2xl font-bold mb-2">Pedido recibido</h1>
        <p className="text-gray-500 mb-1">
          Te avisamos cuando el vendedor lo confirme.
        </p>
        <p className="text-xs text-gray-400 mb-8">Pedido #{orderId}</p>
        <Link
          href={`/tienda/${tenantId}`}
          className="inline-block bg-blue-600 text-white font-medium rounded-lg px-6 py-3 hover:bg-blue-700 transition-colors"
        >
          Volver a la tienda
        </Link>
      </div>
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError("Ingresá tu nombre.");
      return;
    }
    if (!phone.trim()) {
      setError("Ingresá tu teléfono o WhatsApp.");
      return;
    }

    setLoading(true);
    try {
      const res = await fetch(`/api/tienda/${tenantId}/orders`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          customer: { name: name.trim(), phone: phone.trim() },
          paymentMethod,
          notes: notes.trim() || null,
          items: items.map((i) => ({
            productId: i.productId,
            name: i.name,
            price: i.price,
            quantity: i.quantity,
            subtotal: i.price * i.quantity,
          })),
          totalAmount,
        }),
      });

      if (!res.ok) {
        const data = (await res.json().catch(() => ({}))) as { error?: string };
        throw new Error(data.error ?? "Error al enviar el pedido.");
      }

      const data = (await res.json()) as { orderId: string };
      clearCart();
      setOrderId(data.orderId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error inesperado.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <Link
          href={`/tienda/${tenantId}/carrito`}
          className="text-sm text-blue-600 hover:underline"
        >
          &larr; Volver al carrito
        </Link>
        <h1 className="text-xl font-bold">Confirmar pedido</h1>
      </div>

      {/* Order summary */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-6 space-y-2">
        {items.map((item) => (
          <div key={item.productId} className="flex justify-between text-sm">
            <span className="text-gray-700">
              {item.name}{" "}
              <span className="text-gray-400">x{item.quantity}</span>
            </span>
            <span className="font-medium">
              {formatPrice(item.price * item.quantity)}
            </span>
          </div>
        ))}
        <div className="border-t border-gray-100 pt-2 flex justify-between font-bold">
          <span>Total</span>
          <span className="text-blue-600">{formatPrice(totalAmount)}</span>
        </div>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Nombre *
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Tu nombre completo"
            className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Teléfono / WhatsApp *
          </label>
          <input
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="+54 9 11 0000 0000"
            className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Forma de pago *
          </label>
          <div className="grid grid-cols-3 gap-2">
            {(Object.keys(PAYMENT_LABELS) as PaymentMethod[]).map((method) => (
              <button
                key={method}
                type="button"
                onClick={() => setPaymentMethod(method)}
                className={`px-3 py-2.5 rounded-lg border text-sm font-medium transition-colors ${
                  paymentMethod === method
                    ? "bg-blue-600 border-blue-600 text-white"
                    : "bg-white border-gray-300 text-gray-600 hover:border-blue-400"
                }`}
              >
                {PAYMENT_LABELS[method]}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Notas adicionales
          </label>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Dirección de entrega, horario, aclaraciones..."
            rows={3}
            className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
        </div>

        {error && (
          <p className="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={loading}
          className="w-full bg-blue-600 text-white font-semibold rounded-lg px-6 py-4 hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors text-base"
        >
          {loading ? "Enviando pedido..." : "Enviar pedido"}
        </button>
      </form>
    </div>
  );
}
