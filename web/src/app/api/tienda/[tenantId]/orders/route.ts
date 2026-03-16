import { NextRequest, NextResponse } from "next/server";
import { getDb } from "@/lib/firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

interface OrderItem {
  productId: number;
  name: string;
  price: number;
  quantity: number;
  subtotal: number;
}

interface CreateOrderBody {
  customer: { name: string; phone: string };
  paymentMethod: "efectivo" | "transferencia" | "mercado_pago";
  notes?: string | null;
  items: OrderItem[];
  totalAmount: number;
}

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ tenantId: string }> }
) {
  const { tenantId } = await params;

  let body: CreateOrderBody;
  try {
    body = (await req.json()) as CreateOrderBody;
  } catch {
    return NextResponse.json({ error: "Cuerpo inválido." }, { status: 400 });
  }

  const { customer, paymentMethod, items, totalAmount, notes } = body;

  if (!customer?.name?.trim() || !customer?.phone?.trim()) {
    return NextResponse.json(
      { error: "Nombre y teléfono son obligatorios." },
      { status: 400 }
    );
  }

  if (!Array.isArray(items) || items.length === 0) {
    return NextResponse.json(
      { error: "El pedido debe tener al menos un producto." },
      { status: 400 }
    );
  }

  const validMethods = ["efectivo", "transferencia", "mercado_pago"];
  if (!validMethods.includes(paymentMethod)) {
    return NextResponse.json(
      { error: "Método de pago inválido." },
      { status: 400 }
    );
  }

  const db = getDb();
  const orderRef = db
    .collection("tenants")
    .doc(tenantId)
    .collection("orders")
    .doc();

  await orderRef.set({
    orderId: orderRef.id,
    status: "pending_review",
    source: "web_storefront",
    tenantId,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    customer: {
      name: customer.name.trim(),
      phone: customer.phone.trim(),
    },
    paymentMethod,
    notes: notes ?? null,
    items,
    totalAmount,
    currency: "ARS",
  });

  return NextResponse.json({ orderId: orderRef.id }, { status: 201 });
}
