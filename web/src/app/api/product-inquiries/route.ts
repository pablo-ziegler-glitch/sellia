import { NextResponse } from "next/server";
import { getDb } from "@/lib/firebase-admin";
import { VALKIRJA_TENANT_ID } from "@/lib/catalog";

export async function POST(request: Request) {
  try {
    const body = (await request.json()) as {
      productId?: string;
      productName?: string;
      name?: string;
      message?: string;
    };

    const productId = (body.productId || "").trim();
    const productName = (body.productName || "").trim();
    const name = (body.name || "").trim();
    const message = (body.message || "").trim();

    if (!/^[A-Za-z0-9_-]{3,120}$/.test(productId)) {
      return NextResponse.json({ error: "productId inválido" }, { status: 400 });
    }
    if (name.length < 2 || name.length > 80 || message.length < 4 || message.length > 500) {
      return NextResponse.json({ error: "Campos inválidos" }, { status: 400 });
    }

    const db = getDb();
    await db.collection("tenants").doc(VALKIRJA_TENANT_ID).collection("product_inquiries").add({
      productId,
      productName,
      name,
      message,
      source: "public_catalog",
      createdAt: new Date().toISOString(),
    });

    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "No se pudo guardar consulta" }, { status: 500 });
  }
}
