import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  VALKIRJA_TENANT_ID,
  getStorefront,
  getPublicProducts,
} from "@/lib/catalog";
import type { Metadata } from "next";
import ProductGrid from "./ProductGrid";

export const revalidate = 300;

export async function generateMetadata(): Promise<Metadata> {
  const storefront = await getStorefront(VALKIRJA_TENANT_ID);
  const name = storefront?.storeName || "VALKIRJA";
  return {
    title: `Catálogo | ${name}`,
    description: storefront?.tagline || `Catálogo completo de ${name}`,
    openGraph: {
      title: `Catálogo | ${name}`,
      description: storefront?.tagline || `Catálogo completo de ${name}`,
      ...(storefront?.bannerImageUrl
        ? { images: [{ url: storefront.bannerImageUrl }] }
        : {}),
    },
  };
}

export default async function CatalogoPage() {
  const [storefront, products] = await Promise.all([
    getStorefront(VALKIRJA_TENANT_ID),
    getPublicProducts(VALKIRJA_TENANT_ID),
  ]);

  if (!storefront) notFound();

  const baseUrl =
    process.env.NEXT_PUBLIC_BASE_URL || "https://sellia1993.web.app";

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "Store",
    name: storefront.storeName,
    ...(storefront.tagline ? { description: storefront.tagline } : {}),
    url: `${baseUrl}/catalogo`,
    ...(storefront.bannerImageUrl ? { image: storefront.bannerImageUrl } : {}),
    ...(storefront.contactAddress ? { address: storefront.contactAddress } : {}),
    ...(storefront.contactWhatsapp
      ? { telephone: storefront.contactWhatsapp }
      : {}),
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      {/* Cabecera del catálogo */}
      <div className="px-4 pt-8 pb-6 max-w-6xl mx-auto">
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
          <span style={{ color: "var(--foreground)" }}>Catálogo</span>
        </nav>

        {/* Banner */}
        {storefront.bannerImageUrl && (
          <div className="relative w-full h-48 rounded-xl overflow-hidden mb-6">
            <Image
              src={storefront.bannerImageUrl}
              alt={storefront.storeName}
              fill
              className="object-cover"
              priority
              style={{ opacity: 0.6 }}
            />
            <div
              className="absolute inset-0 flex items-end p-6"
              style={{
                background:
                  "linear-gradient(to top, rgba(10,10,10,0.9) 0%, transparent 60%)",
              }}
            >
              <div>
                <h1
                  className="text-3xl font-black tracking-wide uppercase"
                  style={{ color: "var(--gold)" }}
                >
                  {storefront.storeName}
                </h1>
                {storefront.tagline && (
                  <p
                    className="text-sm mt-1"
                    style={{ color: "var(--foreground)", opacity: 0.8 }}
                  >
                    {storefront.tagline}
                  </p>
                )}
              </div>
            </div>
          </div>
        )}

        {!storefront.bannerImageUrl && (
          <div className="mb-6">
            <h1
              className="text-3xl font-black tracking-wide uppercase"
              style={{ color: "var(--gold)" }}
            >
              {storefront.storeName}
            </h1>
            {storefront.tagline && (
              <p className="text-sm mt-1" style={{ color: "var(--muted)" }}>
                {storefront.tagline}
              </p>
            )}
          </div>
        )}

        {/* Contacto rápido */}
        <div className="flex flex-wrap gap-3 text-sm mb-8">
          {storefront.contactWhatsapp && (
            <a
              href={`https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}`}
              target="_blank"
              rel="noopener noreferrer"
              className="px-4 py-2 rounded-full text-xs font-medium transition-opacity hover:opacity-80"
              style={{ background: "#25D366", color: "#fff" }}
            >
              WhatsApp
            </a>
          )}
          {storefront.contactInstagram && (
            <a
              href={`https://instagram.com/${storefront.contactInstagram.replace("@", "")}`}
              target="_blank"
              rel="noopener noreferrer"
              className="px-4 py-2 rounded-full text-xs font-medium transition-opacity hover:opacity-80"
              style={{
                background:
                  "linear-gradient(135deg,#f09433,#e6683c,#dc2743,#cc2366,#bc1888)",
                color: "#fff",
              }}
            >
              Instagram
            </a>
          )}
          {storefront.contactAddress && (
            <span
              className="px-4 py-2 rounded-full text-xs"
              style={{
                background: "var(--surface-2)",
                color: "var(--muted)",
                border: "1px solid var(--border)",
              }}
            >
              {storefront.contactAddress}
            </span>
          )}
        </div>

        {/* Productos */}
        <div className="flex items-center justify-between mb-4">
          <h2
            className="text-sm font-semibold tracking-widest uppercase"
            style={{ color: "var(--muted)" }}
          >
            {products.length} productos
          </h2>
        </div>

        {products.length === 0 ? (
          <p style={{ color: "var(--muted)" }}>
            No hay productos publicados todavía.
          </p>
        ) : (
          <ProductGrid products={products} />
        )}
      </div>
    </>
  );
}
