import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  VALKIRJA_TENANT_ID,
  getPublicProducts,
  getPublicProduct,
  getStorefront,
} from "@/lib/catalog";
import { formatPrice } from "@/lib/format";
import type { Metadata } from "next";
import ShareButton from "./ShareButton";

export const revalidate = 300;

export async function generateStaticParams() {
  const products = await getPublicProducts(VALKIRJA_TENANT_ID);
  return products.map((p) => ({ productId: p.id }));
}

type Props = { params: Promise<{ productId: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { productId } = await params;
  const [product, storefront] = await Promise.all([
    getPublicProduct(VALKIRJA_TENANT_ID, productId),
    getStorefront(VALKIRJA_TENANT_ID),
  ]);
  if (!product) return { title: "Producto no encontrado" };
  const storeName = storefront?.storeName || "VALKIRJA";
  const price = product.listPrice ? ` — ${formatPrice(product.listPrice)}` : "";
  const baseUrl = process.env.NEXT_PUBLIC_BASE_URL || "https://sellia1993.web.app";
  const productUrl = `${baseUrl}/catalogo/producto/${productId}`;
  const ogTitle = `${product.name}${price}`;
  const ogDescription = product.description || `${product.name} disponible en ${storeName}`;
  return {
    title: `${product.name}${price} | ${storeName}`,
    description: product.description || `${product.name} en ${storeName}`,
    openGraph: {
      title: ogTitle,
      description: ogDescription,
      url: productUrl,
      siteName: storeName,
      type: "website",
      ...(product.imageUrl
        ? {
            images: [
              {
                url: product.imageUrl,
                width: 800,
                height: 800,
                alt: product.name,
              },
            ],
          }
        : {}),
    },
    twitter: {
      card: "summary_large_image",
      title: ogTitle,
      description: ogDescription,
      ...(product.imageUrl ? { images: [product.imageUrl] } : {}),
    },
  };
}

export default async function ProductPage({ params }: Props) {
  const { productId } = await params;
  const [product, storefront] = await Promise.all([
    getPublicProduct(VALKIRJA_TENANT_ID, productId),
    getStorefront(VALKIRJA_TENANT_ID),
  ]);

  if (!product) notFound();

  const storeName = storefront?.storeName || "VALKIRJA";
  const baseUrl =
    process.env.NEXT_PUBLIC_BASE_URL || "https://sellia1993.web.app";
  const productUrl = `${baseUrl}/catalogo/producto/${productId}`;

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

      <div className="max-w-6xl mx-auto px-4 py-8">
        {/* Breadcrumb */}
        <nav className="text-sm mb-6" style={{ color: "var(--muted)" }}>
          <Link
            href="/"
            className="transition-colors hover:underline"
            style={{ color: "var(--gold)" }}
          >
            Inicio
          </Link>
          {" / "}
          <Link
            href="/catalogo"
            className="transition-colors hover:underline"
            style={{ color: "var(--gold)" }}
          >
            Catálogo
          </Link>
          {" / "}
          <span style={{ color: "var(--foreground)" }}>{product.name}</span>
        </nav>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
          {/* Imagen */}
          <div>
            {product.imageUrl ? (
              <div
                className="relative w-full aspect-square rounded-xl overflow-hidden"
                style={{
                  background: "var(--surface)",
                  border: "1px solid var(--border)",
                }}
              >
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
              <div
                className="w-full aspect-square rounded-xl flex items-center justify-center text-sm"
                style={{
                  background: "var(--surface)",
                  color: "var(--muted)",
                  border: "1px solid var(--border)",
                }}
              >
                Sin imagen
              </div>
            )}
          </div>

          {/* Info */}
          <div>
            {product.category && (
              <p
                className="text-xs tracking-widest uppercase mb-2"
                style={{ color: "var(--gold)" }}
              >
                {product.category}
              </p>
            )}
            <h1
              className="text-2xl sm:text-3xl font-bold mb-6"
              style={{ color: "var(--foreground)" }}
            >
              {product.name}
            </h1>

            {/* Precios */}
            <div
              className="rounded-xl p-5 space-y-3 mb-6"
              style={{
                background: "var(--surface)",
                border: "1px solid var(--border)",
              }}
            >
              {product.listPrice != null && (
                <div className="flex justify-between items-center">
                  <span style={{ color: "var(--muted)" }} className="text-sm">
                    Precio lista
                  </span>
                  <span
                    className="text-2xl font-bold"
                    style={{ color: "var(--gold)" }}
                  >
                    {formatPrice(product.listPrice)}
                  </span>
                </div>
              )}
              {product.cashPrice != null &&
                product.cashPrice !== product.listPrice && (
                  <div
                    className="flex justify-between items-center pt-3"
                    style={{ borderTop: "1px solid var(--border)" }}
                  >
                    <span style={{ color: "var(--muted)" }} className="text-sm">
                      Efectivo
                    </span>
                    <span
                      className="text-lg font-semibold"
                      style={{ color: "#4ade80" }}
                    >
                      {formatPrice(product.cashPrice)}
                    </span>
                  </div>
                )}
              {product.transferPrice != null &&
                product.transferPrice !== product.listPrice && (
                  <div
                    className="flex justify-between items-center pt-3"
                    style={{ borderTop: "1px solid var(--border)" }}
                  >
                    <span style={{ color: "var(--muted)" }} className="text-sm">
                      Transferencia
                    </span>
                    <span
                      className="text-lg font-semibold"
                      style={{ color: "var(--foreground)" }}
                    >
                      {formatPrice(product.transferPrice)}
                    </span>
                  </div>
                )}
            </div>

            {/* Descripción */}
            {product.description && (
              <div className="mb-6">
                <h2
                  className="text-xs font-semibold tracking-widest uppercase mb-2"
                  style={{ color: "var(--muted)" }}
                >
                  Descripción
                </h2>
                <p
                  className="text-sm leading-relaxed whitespace-pre-line"
                  style={{ color: "var(--foreground)", opacity: 0.85 }}
                >
                  {product.description}
                </p>
              </div>
            )}

            {/* Acciones — en mobile: ShareButton primero como CTA principal */}
            <div className="flex flex-col gap-3">
              {/* ShareButton: primer lugar en mobile, último en desktop */}
              <div className="md:order-last">
                <ShareButton
                  url={productUrl}
                  title={product.name}
                  text={product.description || `${product.name} en ${storeName}`}
                />
              </div>
              {storefront?.contactWhatsapp && (
                <a
                  href={`https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}?text=${encodeURIComponent(`Hola, me interesa: ${product.name}`)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center justify-center w-full rounded py-3 px-6 text-sm font-semibold transition-opacity hover:opacity-80"
                  style={{ background: "#25D366", color: "#fff" }}
                >
                  Consultar por WhatsApp
                </a>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
