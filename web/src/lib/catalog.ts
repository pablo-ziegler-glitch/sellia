import { getDb } from "./firebase-admin";

/** Único tenant de la tienda VALKIRJA. Configurable vía variable de entorno. */
export const VALKIRJA_TENANT_ID =
  process.env.VALKIRJA_TENANT_ID || "valkirja";

export interface TenantSummary {
  id: string;
  name: string;
  lat?: number | null;
  lng?: number | null;
  address?: string | null;
  city?: string | null;
  country?: string | null;
  storeType?: "physical" | "online" | "both";
  hasDelivery?: boolean;
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
  locationLat?: number | null;
  locationLng?: number | null;
  locationAddress?: string | null;
  locationCity?: string | null;
  locationCountry?: string | null;
  storeType?: "physical" | "online" | "both";
  hasDelivery?: boolean;
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
  return snap.docs.map((doc) => {
    const data = doc.data() as Record<string, unknown>;
    const storeTypeRaw = (data.storeType as string | undefined)?.toLowerCase();
    const storeType =
      storeTypeRaw === "physical" ||
      storeTypeRaw === "online" ||
      storeTypeRaw === "both"
        ? storeTypeRaw
        : "physical";

    return {
      id: doc.id,
      name: (data.name as string) || doc.id,
      lat: (data.lat as number | undefined) ?? null,
      lng: (data.lng as number | undefined) ?? null,
      address: (data.address as string | undefined) ?? null,
      city: (data.city as string | undefined) ?? null,
      country: (data.country as string | undefined) ?? null,
      storeType,
      hasDelivery: Boolean(data.hasDelivery),
    };
  });
}

export function haversineKm(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const R = 6371;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export async function getStoresByLocation(
  lat: number,
  lng: number,
  radiusKm: number = 5
): Promise<Array<TenantSummary & { distanceKm: number }>> {
  const stores = await listStores();
  return stores
    .filter(
      (store) =>
        (store.storeType === "physical" || store.storeType === "both") &&
        typeof store.lat === "number" &&
        typeof store.lng === "number"
    )
    .map((store) => {
      const distance = haversineKm(lat, lng, store.lat as number, store.lng as number);
      return { ...store, distanceKm: Math.round(distance * 10) / 10 };
    })
    .filter((store) => store.distanceKm <= radiusKm)
    .sort((a, b) => a.distanceKm - b.distanceKm)
    .slice(0, 20);
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
