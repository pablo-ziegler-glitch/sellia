import Image from "next/image";
import { notFound } from "next/navigation";
import { listStores, getStorefront, getPublicProducts } from "@/lib/catalog";
import type { Metadata } from "next";
import ProductGrid from "./ProductGrid";

export const revalidate = 300;

export async function generateStaticParams() {
  const stores = await listStores();
  return stores.map((s) => ({ tenantId: s.id }));
}

type Props = { params: Promise<{ tenantId: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { tenantId } = await params;
  const storefront = await getStorefront(tenantId);
  const name = storefront?.storeName || "Tienda";
  return {
    title: `${name} - Catálogo | Sellia`,
    description: storefront?.tagline || `Catálogo público de ${name}`,
    openGraph: {
      title: `${name} - Catálogo`,
      description: storefront?.tagline || `Catálogo público de ${name}`,
      ...(storefront?.bannerImageUrl
        ? { images: [{ url: storefront.bannerImageUrl }] }
        : {}),
    },
  };
}

export default async function StorePage({ params }: Props) {
  const { tenantId } = await params;
  const [storefront, products] = await Promise.all([
    getStorefront(tenantId),
    getPublicProducts(tenantId),
  ]);

  if (!storefront) notFound();

  return (
    <div>
      {/* Store header */}
      <div className="mb-8">
        {storefront.bannerImageUrl && (
          <div className="relative w-full h-48 rounded-lg overflow-hidden mb-4">
            <Image
              src={storefront.bannerImageUrl}
              alt={storefront.storeName}
              fill
              className="object-cover"
              priority
            />
          </div>
        )}
        <h1 className="text-2xl font-bold">{storefront.storeName}</h1>
        {storefront.tagline && (
          <p className="text-gray-500 mt-1">{storefront.tagline}</p>
        )}

        {/* Contact info */}
        <div className="flex flex-wrap gap-4 mt-3 text-sm text-gray-600">
          {storefront.contactWhatsapp && (
            <a
              href={`https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}`}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-green-600"
            >
              WhatsApp
            </a>
          )}
          {storefront.contactInstagram && (
            <a
              href={`https://instagram.com/${storefront.contactInstagram.replace("@", "")}`}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-pink-600"
            >
              Instagram
            </a>
          )}
          {storefront.contactAddress && (
            <span>{storefront.contactAddress}</span>
          )}
        </div>
      </div>

      {/* Products grid with category filter */}
      <h2 className="text-lg font-semibold mb-4">
        Productos ({products.length})
      </h2>

      {products.length === 0 ? (
        <p className="text-gray-400">
          Esta tienda no tiene productos publicados todavía.
        </p>
      ) : (
        <ProductGrid tenantId={tenantId} products={products} />
      )}
    </div>
  );
}
