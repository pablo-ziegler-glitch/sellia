import type { MetadataRoute } from "next";
import { VALKIRJA_TENANT_ID, getPublicProducts } from "@/lib/catalog";

const BASE_URL = process.env.NEXT_PUBLIC_BASE_URL || "https://sellia1993.web.app";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const products = await getPublicProducts(VALKIRJA_TENANT_ID);

  const productEntries: MetadataRoute.Sitemap = products.map((p) => ({
    url: `${BASE_URL}/catalogo/producto/${p.id}`,
    changeFrequency: "weekly" as const,
    priority: 0.6,
  }));

  return [
    { url: BASE_URL, changeFrequency: "daily", priority: 1 },
    { url: `${BASE_URL}/catalogo`, changeFrequency: "daily", priority: 0.9 },
    ...productEntries,
  ];
}
