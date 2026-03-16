"use client";

import { useCart } from "./CartContext";

interface Props {
  productId: number;
  name: string;
  price: number;
}

export default function AddToCartButton({ productId, name, price }: Props) {
  const { addItem, items } = useCart();
  const inCart = items.find((i) => i.productId === productId);

  return (
    <button
      onClick={() => addItem({ productId, name, price })}
      className="w-full bg-blue-600 text-white font-medium rounded-lg px-4 py-2.5 hover:bg-blue-700 active:bg-blue-800 transition-colors text-sm"
    >
      {inCart ? `En carrito (${inCart.quantity})` : "Agregar al carrito"}
    </button>
  );
}
