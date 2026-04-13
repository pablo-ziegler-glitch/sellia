import { getDb } from "./firebase-admin";

/**
 * Tenant público por defecto para la web.
 * Orden de prioridad:
 * 1) PUBLIC_WEB_TENANT_ID
 * 2) VALKIRJA_TENANT_ID (compat legacy)
 * 3) NEXT_PUBLIC_TENANT_ID
 * 4) sellia1993 (fallback operativo)
 */
export const VALKIRJA_TENANT_ID =
  process.env.PUBLIC_WEB_TENANT_ID ||
  process.env.VALKIRJA_TENANT_ID ||
  process.env.NEXT_PUBLIC_TENANT_ID ||
  "sellia1993";

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
  locationLat?: number | null;
  locationLng?: number | null;
  locationAddress?: string | null;
  locationCity?: string | null;
  locationCountry?: string | null;
  storeType?: "physical" | "online" | "both";
  hasDelivery?: boolean;
}

export interface PublicProduct {
  id: string;
  name: string;
  imageUrl: string | null;
  listPrice: number | null;
  cashPrice: number | null;
  transferPrice: number | null;
  category: string | null;
  subcategory: string | null;
  description: string | null;
}

export interface CatalogConfig {
  showPrices: boolean;
  showCashPrice: boolean;
  heroTitle: string;
  heroSubtitle: string;
  footerText: string;
  featuredProductIds: string[];
  featuredTitle: string;
}

/** Get catalog display config for a tenant. Returns safe defaults if not configured. */
export async function getCatalogConfig(tenantId: string): Promise<CatalogConfig> {
  const db = getDb();
  const doc = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("config")
    .doc("public_catalog")
    .get();
  const raw = (doc.data()?.data as Record<string, unknown> | undefined) ?? {};
  return {
    showPrices: raw.showPrices !== false,
    showCashPrice: raw.showCashPrice !== false,
    heroTitle: (raw.heroTitle as string) || "",
    heroSubtitle: (raw.heroSubtitle as string) || "",
    footerText: (raw.footerText as string) || "",
    featuredProductIds: Array.isArray(raw.featuredProductIds)
      ? raw.featuredProductIds.filter((id): id is string => typeof id === "string")
      : [],
    featuredTitle: (raw.featuredTitle as string) || "Productos destacados",
  };
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
  const location = (raw.location as Record<string, unknown> | undefined) ?? {};
  const storeTypeRaw = (raw.storeType as string | undefined)?.toLowerCase();
  const storeType =
    storeTypeRaw === "physical" ||
    storeTypeRaw === "online" ||
    storeTypeRaw === "both"
      ? storeTypeRaw
      : "physical";
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
    locationLat: (location.lat as number | undefined) ?? null,
    locationLng: (location.lng as number | undefined) ?? null,
    locationAddress: (location.address as string | undefined) ?? null,
    locationCity: (location.city as string | undefined) ?? null,
    locationCountry: (location.country as string | undefined) ?? null,
    storeType,
    hasDelivery: Boolean(raw.hasDelivery),
  };
}

/** Get all published products for a tenant, ordered by name. */
export async function getPublicProducts(
  tenantId: string
): Promise<PublicProduct[]> {
  const db = getDb();
  const snap = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("public_products")
    .orderBy("name")
    .get();
  return snap.docs.map((doc) => {
    const d = doc.data();
    return {
      id: doc.id,
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
    id: doc.id,
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
