import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { listStores, getPublicProducts, getPublicProduct, getStorefront } from "@/lib/catalog";
import { formatPrice } from "@/lib/format";
import type { Metadata } from "next";
import ShareButton from "./ShareButton";

export const revalidate = 300;

export async function generateStaticParams() {
  const stores = await listStores();
  const results = await Promise.all(
    stores.map(async (s) => {
      const products = await getPublicProducts(s.id);
      return products.map((p) => ({ tenantId: s.id, productId: String(p.id) }));
    })
  );
  return results.flat();
}

type Props = { params: Promise<{ tenantId: string; productId: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { tenantId, productId } = await params;
  const [product, storefront] = await Promise.all([
    getPublicProduct(tenantId, productId),
    getStorefront(tenantId),
  ]);
  if (!product) return { title: "Producto no encontrado" };
  const storeName = storefront?.storeName || "Tienda";
  const price = product.listPrice ? ` - ${formatPrice(product.listPrice)}` : "";
  return {
    title: `${product.name}${price} | ${storeName}`,
    description:
      product.description || `${product.name} en ${storeName}`,
    openGraph: {
      title: `${product.name}${price}`,
      description:
        product.description || `${product.name} disponible en ${storeName}`,
      ...(product.imageUrl ? { images: [{ url: product.imageUrl }] } : {}),
    },
  };
}

export default async function ProductPage({ params }: Props) {
  const { tenantId, productId } = await params;
  const [product, storefront] = await Promise.all([
    getPublicProduct(tenantId, productId),
    getStorefront(tenantId),
  ]);

  if (!product) notFound();

  const storeName = storefront?.storeName || "Tienda";
  const baseUrl = process.env.NEXT_PUBLIC_BASE_URL || "https://sellia1993.web.app";
  const productUrl = `${baseUrl}/tienda/${tenantId}/producto/${productId}`;

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "Product",
    name: product.name,
    ...(product.description ? { description: product.description } : {}),
    ...(product.imageUrl ? { image: [product.imageUrl] } : {}),
    ...(product.category ? { category: product.category } : {}),
    offers: {
      "@type": "Offer",
      url: productUrl,
      priceCurrency: "ARS",
      price: product.listPrice ?? product.cashPrice ?? product.transferPrice,
      availability: "https://schema.org/InStock",
      seller: {
        "@type": "Organization",
        name: storeName,
      },
    },
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <div>
        {/* Breadcrumb */}
        <nav className="text-sm text-gray-500 mb-4">
          <Link href="/" className="hover:text-blue-600">
            Inicio
          </Link>
          {" / "}
          <Link href={`/tienda/${tenantId}`} className="hover:text-blue-600">
            {storeName}
          </Link>
          {" / "}
          <span className="text-gray-700">{product.name}</span>
        </nav>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Image */}
          <div>
            {product.imageUrl ? (
              <div className="relative w-full aspect-square rounded-lg overflow-hidden bg-white border border-gray-200">
                <Image
                  src={product.imageUrl}
                  alt={product.name}
                  fill
                  className="object-contain"
                  priority
                  sizes="(max-width: 768px) 100vw, 50vw"
                />
              </div>
            ) : (
              <div className="w-full aspect-square rounded-lg bg-gray-100 flex items-center justify-center text-gray-300 text-lg">
                Sin imagen
              </div>
            )}
          </div>

          {/* Info */}
          <div>
            {product.category && (
              <p className="text-sm text-gray-400 mb-1">{product.category}</p>
            )}
            <h1 className="text-2xl font-bold mb-4">{product.name}</h1>

            {/* Prices */}
            <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
              {product.listPrice != null && (
                <div className="flex justify-between items-center">
                  <span className="text-gray-600">Precio Lista</span>
                  <span className="text-2xl font-bold text-blue-600">
                    {formatPrice(product.listPrice)}
                  </span>
                </div>
              )}
              {product.cashPrice != null &&
                product.cashPrice !== product.listPrice && (
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">Efectivo</span>
                    <span className="text-lg font-semibold text-green-600">
                      {formatPrice(product.cashPrice)}
                    </span>
                  </div>
                )}
              {product.transferPrice != null &&
                product.transferPrice !== product.listPrice && (
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">Transferencia</span>
                    <span className="text-lg font-semibold text-gray-700">
                      {formatPrice(product.transferPrice)}
                    </span>
                  </div>
                )}
            </div>

            {/* Description */}
            {product.description && (
              <div className="mt-6">
                <h2 className="text-sm font-semibold text-gray-600 mb-2">
                  Descripción
                </h2>
                <p className="text-gray-700 whitespace-pre-line">
                  {product.description}
                </p>
              </div>
            )}

            {/* Actions */}
            <div className="mt-6 flex flex-col gap-3">
              {storefront?.contactWhatsapp && (
                <a
                  href={`https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}?text=${encodeURIComponent(`Hola, me interesa: ${product.name}`)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center justify-center w-full bg-green-600 text-white font-medium rounded-lg px-6 py-3 hover:bg-green-700 transition-colors"
                >
                  Consultar por WhatsApp
                </a>
              )}
              <ShareButton
                url={productUrl}
                title={product.name}
                text={product.description || `${product.name} en ${storeName}`}
              />
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
