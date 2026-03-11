import { getDb } from "./firebase-admin";

export interface TenantSummary {
  id: string;
  name: string;
}

export interface StorefrontConfig {
  storeName: string;
  tagline: string;
  logoUrl: string;
  bannerImageUrl: string;
  bannerTitle: string;
  bannerSubtitle: string;
  contactWhatsapp: string;
  contactInstagram: string;
  contactAddress: string;
}

export interface PublicProduct {
  id: number;
  name: string;
  imageUrl: string | null;
  listPrice: number | null;
  cashPrice: number | null;
  transferPrice: number | null;
  category: string | null;
  subcategory: string | null;
  description: string | null;
}

/** List all stores in the public directory. */
export async function listStores(): Promise<TenantSummary[]> {
  const db = getDb();
  const snap = await db.collection("public_tenant_directory").get();
  return snap.docs.map((doc) => ({
    id: doc.id,
    name: (doc.data().name as string) || doc.id,
  }));
}

/** Get storefront configuration for a tenant. */
export async function getStorefront(
  tenantId: string
): Promise<StorefrontConfig | null> {
  const db = getDb();
  const doc = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("config")
    .doc("storefront")
    .get();
  if (!doc.exists) return null;
  const raw = doc.data()?.data as Record<string, unknown> | undefined;
  if (!raw) return null;
  return {
    storeName: (raw.storeName as string) || "",
    tagline: (raw.tagline as string) || "",
    logoUrl: (raw.logoUrl as string) || "",
    bannerImageUrl: (raw.bannerImageUrl as string) || "",
    bannerTitle: (raw.bannerTitle as string) || "",
    bannerSubtitle: (raw.bannerSubtitle as string) || "",
    contactWhatsapp: (raw.contactWhatsapp as string) || "",
    contactInstagram: (raw.contactInstagram as string) || "",
    contactAddress: (raw.contactAddress as string) || "",
  };
}

/** Get all published products for a tenant. */
export async function getPublicProducts(
  tenantId: string
): Promise<PublicProduct[]> {
  const db = getDb();
  const snap = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("public_products")
    .get();
  return snap.docs.map((doc) => {
    const d = doc.data();
    return {
      id: Number(doc.id) || 0,
      name: (d.name as string) || "",
      imageUrl: (d.imageUrl as string) || null,
      listPrice: (d.listPrice as number) ?? null,
      cashPrice: (d.cashPrice as number) ?? null,
      transferPrice: (d.transferPrice as number) ?? null,
      category: (d.parentCategory as string) || (d.category as string) || null,
      subcategory: (d.category as string) || null,
      description: (d.description as string) || null,
    };
  });
}

/** Get a single public product. */
export async function getPublicProduct(
  tenantId: string,
  productId: string
): Promise<PublicProduct | null> {
  const db = getDb();
  const doc = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("public_products")
    .doc(productId)
    .get();
  if (!doc.exists) return null;
  const d = doc.data()!;
  return {
    id: Number(doc.id) || 0,
    name: (d.name as string) || "",
    imageUrl: (d.imageUrl as string) || null,
    listPrice: (d.listPrice as number) ?? null,
    cashPrice: (d.cashPrice as number) ?? null,
    transferPrice: (d.transferPrice as number) ?? null,
    category: (d.parentCategory as string) || (d.category as string) || null,
    subcategory: (d.category as string) || null,
    description: (d.description as string) || null,
  };
}
