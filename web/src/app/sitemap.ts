import type { MetadataRoute } from "next";
import { listStores, getPublicProducts } from "@/lib/catalog";

const BASE_URL = process.env.NEXT_PUBLIC_BASE_URL || "https://sellia1993.web.app";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const stores = await listStores();

  const storeEntries: MetadataRoute.Sitemap = stores.map((s) => ({
    url: `${BASE_URL}/tienda/${s.id}`,
    changeFrequency: "daily",
    priority: 0.8,
  }));

  const productLists = await Promise.all(
    stores.map((s) => getPublicProducts(s.id).then((ps) => ({ tenantId: s.id, products: ps })))
  );

  const productEntries: MetadataRoute.Sitemap = productLists.flatMap(({ tenantId, products }) =>
    products.map((p) => ({
      url: `${BASE_URL}/tienda/${tenantId}/producto/${p.id}`,
      changeFrequency: "weekly" as const,
      priority: 0.6,
    }))
  );

  return [
    { url: BASE_URL, changeFrequency: "daily", priority: 1 },
    ...storeEntries,
    ...productEntries,
  ];
}
