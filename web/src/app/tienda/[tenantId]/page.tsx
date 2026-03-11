import Link from "next/link";
import Image from "next/image";
import { notFound } from "next/navigation";
import { getStorefront, getPublicProducts } from "@/lib/catalog";
import { formatPrice } from "@/lib/format";
import type { Metadata } from "next";

export const revalidate = 300;

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

      {/* Products grid */}
      <h2 className="text-lg font-semibold mb-4">
        Productos ({products.length})
      </h2>

      {products.length === 0 ? (
        <p className="text-gray-400">
          Esta tienda no tiene productos publicados todavía.
        </p>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {products.map((product) => (
            <Link
              key={product.id}
              href={`/tienda/${tenantId}/producto/${product.id}`}
              className="bg-white rounded-lg border border-gray-200 overflow-hidden hover:shadow-md transition-shadow"
            >
              {product.imageUrl ? (
                <div className="relative w-full aspect-square">
                  <Image
                    src={product.imageUrl}
                    alt={product.name}
                    fill
                    className="object-cover"
                    sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
                  />
                </div>
              ) : (
                <div className="w-full aspect-square bg-gray-100 flex items-center justify-center text-gray-300">
                  Sin imagen
                </div>
              )}
              <div className="p-3">
                <h3 className="text-sm font-medium line-clamp-2">
                  {product.name}
                </h3>
                {product.category && (
                  <p className="text-xs text-gray-400 mt-0.5">
                    {product.category}
                  </p>
                )}
                <div className="mt-2">
                  {product.listPrice != null && (
                    <p className="text-base font-bold text-blue-600">
                      {formatPrice(product.listPrice)}
                    </p>
                  )}
                  {product.cashPrice != null &&
                    product.cashPrice !== product.listPrice && (
                      <p className="text-xs text-green-600">
                        Efectivo: {formatPrice(product.cashPrice)}
                      </p>
                    )}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
