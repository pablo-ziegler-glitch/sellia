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
import ProductInquiryForm from "./ProductInquiryForm";

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
  const whatsappUrl = storefront?.contactWhatsapp
    ? `https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}?text=${encodeURIComponent(`Hola, me interesa ${product.name}`)}`
    : null;

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
        <nav className="mb-6 flex items-center gap-1 text-sm text-[var(--muted)]">
          <Link href="/" className="hover:text-[var(--primary-container)]">
            Inicio
          </Link>
          <span className="material-symbols-outlined text-sm">chevron_right</span>
          <Link href="/catalogo" className="hover:text-[var(--primary-container)]">
            Catálogo
          </Link>
          <span className="material-symbols-outlined text-sm">chevron_right</span>
          <span className="line-clamp-1 font-medium text-[var(--foreground)]">{product.name}</span>
        </nav>

        <div className="grid grid-cols-1 gap-10 md:grid-cols-2">
          <div>
            {product.imageUrl ? (
              <div
                className="relative w-full aspect-square overflow-hidden rounded-2xl"
                style={{
                  background: "var(--surface-pop)",
                  boxShadow: "var(--ambient-shadow)",
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
                className="flex w-full aspect-square items-center justify-center rounded-2xl text-sm"
                style={{
                  background: "var(--surface-high)",
                  color: "var(--muted)",
                }}
              >
                Sin imagen
              </div>
            )}
          </div>

          <div>
            {product.category && (
              <p className="mb-2 text-xs uppercase tracking-widest text-[var(--primary-container)]">
                {product.category}
              </p>
            )}
            <h1 className="mb-6 font-plus-jakarta text-3xl font-extrabold text-[var(--foreground)] sm:text-4xl">
              {product.name}
            </h1>

            <div
              className="mb-6 space-y-3 rounded-2xl bg-[var(--surface-low)] p-6"
              style={{
                boxShadow: "var(--ambient-shadow)",
              }}
            >
              {product.listPrice != null && (
                <div className="flex justify-between items-center">
                  <span className="text-sm text-[var(--muted)]">
                    Precio lista
                  </span>
                  <span
                    className="font-plus-jakarta text-2xl font-extrabold text-[var(--primary)]"
                  >
                    {formatPrice(product.listPrice)}
                  </span>
                </div>
              )}
              {product.cashPrice != null &&
                product.cashPrice !== product.listPrice && (
                  <div
                    className="flex justify-between items-center pt-3"
                    style={{
                      borderTop:
                        "1px solid color-mix(in srgb, var(--border) 30%, transparent)",
                    }}
                  >
                    <span className="text-sm text-[var(--muted)]">
                      Efectivo
                    </span>
                    <span className="text-lg font-semibold text-[var(--secondary)]">
                      {formatPrice(product.cashPrice)}
                    </span>
                  </div>
                )}
              {product.transferPrice != null &&
                product.transferPrice !== product.listPrice && (
                  <div
                    className="flex justify-between items-center pt-3"
                    style={{
                      borderTop:
                        "1px solid color-mix(in srgb, var(--border) 30%, transparent)",
                    }}
                  >
                    <span className="text-sm text-[var(--muted)]">
                      Transferencia
                    </span>
                    <span className="text-lg font-semibold text-[var(--foreground)]">
                      {formatPrice(product.transferPrice)}
                    </span>
                  </div>
                )}
            </div>

            {product.description && (
              <div className="mb-6">
                <h2 className="mb-2 text-xs font-semibold uppercase tracking-widest text-[var(--muted)]">
                  Descripción
                </h2>
                <p className="whitespace-pre-line text-sm leading-relaxed text-[var(--foreground)]">
                  {product.description}
                </p>
              </div>
            )}

            <div className="flex flex-col gap-3">
              <div className="md:order-last">
                <ShareButton
                  url={productUrl}
                  title={product.name}
                  text={product.description || `${product.name} en ${storeName}`}
                />
              </div>
              {whatsappUrl && (
                <a
                  href={whatsappUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex w-full items-center justify-center rounded-full bg-[#25D366] px-6 py-3 text-sm font-semibold text-white transition-opacity hover:opacity-85"
                >
                  Consultar por WhatsApp
                </a>
              )}
              <ProductInquiryForm productId={productId} productName={product.name} />
            </div>
          </div>
        </div>
      </div>
      {whatsappUrl && (
        <a
          href={whatsappUrl}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Abrir WhatsApp para consultar este producto"
          className="fixed bottom-5 right-5 z-50 inline-flex items-center justify-center rounded-full shadow-lg"
          style={{ background: "#25D366", color: "#fff", width: 58, height: 58 }}
        >
          <span className="material-symbols-outlined text-[24px]">chat</span>
        </a>
      )}
    </>
  );
}
