import { getDb } from "./firebase-admin";
import { unstable_cache } from "next/cache";

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
  imageUrls: string[];
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

type PublicCatalogInventoryItem = {
  id?: string;
  name?: string;
  imageUrl?: string | null;
  imageUrls?: string[] | null;
  listPrice?: number | null;
  cashPrice?: number | null;
  transferPrice?: number | null;
  parentCategory?: string | null;
  category?: string | null;
  description?: string | null;
};

function normalizeImageUrl(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const raw = value.trim();
  if (!raw) return null;

  if (raw.startsWith("gs://")) {
    const withoutScheme = raw.slice("gs://".length);
    const separator = withoutScheme.indexOf("/");
    if (separator <= 0) return null;
    const bucket = withoutScheme.slice(0, separator);
    const objectPath = withoutScheme.slice(separator + 1);
    if (!bucket || !objectPath) return null;
    return `https://firebasestorage.googleapis.com/v0/b/${bucket}/o/${encodeURIComponent(objectPath)}?alt=media`;
  }

  if (raw.startsWith("//")) return `https:${raw}`;

  try {
    const url = new URL(raw);
    if (url.protocol === "https:" || url.protocol === "http:") return url.toString();
  } catch {
    return null;
  }
  return null;
}

function normalizeImageList(values: unknown): string[] {
  if (!Array.isArray(values)) return [];
  const urls = values
    .map(normalizeImageUrl)
    .filter((url): url is string => Boolean(url));
  return Array.from(new Set(urls));
}

/** Get catalog display config for a tenant. Returns safe defaults if not configured. */
const getCatalogConfigCached = unstable_cache(async (tenantId: string): Promise<CatalogConfig> => {
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
}, ["catalog-config"], { revalidate: 300 });

export async function getCatalogConfig(tenantId: string): Promise<CatalogConfig> {
  return getCatalogConfigCached(tenantId);
}

/** Get storefront configuration for a tenant. */
const getStorefrontCached = unstable_cache(async (
  tenantId: string
): Promise<StorefrontConfig | null> => {
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
}, ["storefront"], { revalidate: 300 });

export async function getStorefront(
  tenantId: string
): Promise<StorefrontConfig | null> {
  return getStorefrontCached(tenantId);
}

const getPublicCatalogInventoryCached = unstable_cache(async (
  tenantId: string
): Promise<Record<string, PublicCatalogInventoryItem> | null> => {
  const db = getDb();
  const inventoryDoc = await db
    .collection("public_catalog_inventory")
    .doc(tenantId)
    .get();

  if (!inventoryDoc.exists) return null;
  const rawItems = inventoryDoc.data()?.items as Record<string, PublicCatalogInventoryItem> | undefined;
  if (!rawItems || typeof rawItems !== "object") return null;
  return rawItems;
}, ["public-catalog-inventory"], { revalidate: 300 });

function inventoryEntryToPublicProduct(
  id: string,
  item: PublicCatalogInventoryItem
): PublicProduct {
  const imageUrls = normalizeImageList(item.imageUrls);
  const primary = normalizeImageUrl(item.imageUrl) ?? imageUrls[0] ?? null;
  return {
    id,
    name: (item.name as string) || "",
    imageUrl: primary,
    imageUrls: primary ? [primary, ...imageUrls.filter((url) => url !== primary)] : imageUrls,
    listPrice: (item.listPrice as number) ?? null,
    cashPrice: (item.cashPrice as number) ?? null,
    transferPrice: (item.transferPrice as number) ?? null,
    category: (item.parentCategory as string) || (item.category as string) || null,
    subcategory: (item.category as string) || null,
    description: (item.description as string) || null,
  };
}

/** Get all published products for a tenant, ordered by name. */
const getPublicProductsCached = unstable_cache(async (
  tenantId: string
): Promise<PublicProduct[]> => {
  const inventory = await getPublicCatalogInventoryCached(tenantId);
  if (inventory && Object.keys(inventory).length > 0) {
    return Object.entries(inventory)
      .map(([id, item]) => inventoryEntryToPublicProduct(id, item))
      .sort((a, b) => a.name.localeCompare(b.name, "es"));
  }

  const db = getDb();
  const snap = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("public_products")
    .orderBy("name")
    .get();

  const products = snap.docs.map((doc) => {
    const d = doc.data();
    const imageUrls = normalizeImageList(d.imageUrls);
    const primary = normalizeImageUrl(d.imageUrl) ?? imageUrls[0] ?? null;
    return {
      id: doc.id,
      name: (d.name as string) || "",
      imageUrl: primary,
      imageUrls: primary ? [primary, ...imageUrls.filter((url) => url !== primary)] : imageUrls,
      listPrice: (d.listPrice as number) ?? null,
      cashPrice: (d.cashPrice as number) ?? null,
      transferPrice: (d.transferPrice as number) ?? null,
      category: (d.parentCategory as string) || (d.category as string) || null,
      subcategory: (d.category as string) || null,
      description: (d.description as string) || null,
    };
  });

  return Array.from(
    products.reduce((acc, product) => {
      if (!acc.has(product.id)) acc.set(product.id, product);
      return acc;
    }, new Map<string, PublicProduct>()).values()
  );
}, ["public-products"], { revalidate: 300 });

export async function getPublicProducts(
  tenantId: string
): Promise<PublicProduct[]> {
  return getPublicProductsCached(tenantId);
}

/** Get a single public product. */
const getPublicProductCached = unstable_cache(async (
  tenantId: string,
  productId: string
): Promise<PublicProduct | null> => {
  const inventory = await getPublicCatalogInventoryCached(tenantId);
  const inventoryEntry = inventory?.[productId];
  if (inventoryEntry) {
    return inventoryEntryToPublicProduct(productId, inventoryEntry);
  }

  const db = getDb();
  const doc = await db
    .collection("tenants")
    .doc(tenantId)
    .collection("public_products")
    .doc(productId)
    .get();
  if (!doc.exists) return null;
  const d = doc.data()!;
  const imageUrls = normalizeImageList(d.imageUrls);
  const primary = normalizeImageUrl(d.imageUrl) ?? imageUrls[0] ?? null;
  return {
    id: doc.id,
    name: (d.name as string) || "",
    imageUrl: primary,
    imageUrls: primary ? [primary, ...imageUrls.filter((url) => url !== primary)] : imageUrls,
    listPrice: (d.listPrice as number) ?? null,
    cashPrice: (d.cashPrice as number) ?? null,
    transferPrice: (d.transferPrice as number) ?? null,
    category: (d.parentCategory as string) || (d.category as string) || null,
    subcategory: (d.category as string) || null,
    description: (d.description as string) || null,
  };
}, ["public-product"], { revalidate: 300 });

export async function getPublicProduct(
  tenantId: string,
  productId: string
): Promise<PublicProduct | null> {
  return getPublicProductCached(tenantId, productId);
}
