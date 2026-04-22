import Image from "next/image";
import Link from "next/link";
import { Suspense } from "react";
import { notFound } from "next/navigation";
import {
  VALKIRJA_TENANT_ID,
  getStorefront,
  getPublicProducts,
  getCatalogConfig,
} from "@/lib/catalog";
import type { Metadata } from "next";
import ProductGrid from "./ProductGrid";
import ProductGridSkeleton from "./ProductGridSkeleton";

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
  const [storefront, products, catalogConfig] = await Promise.all([
    getStorefront(VALKIRJA_TENANT_ID),
    getPublicProducts(VALKIRJA_TENANT_ID),
    getCatalogConfig(VALKIRJA_TENANT_ID),
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

      <div className="mx-auto max-w-7xl px-4 pb-16 pt-6 sm:px-6">
        <nav className="mb-6 flex items-center gap-1 text-sm text-[var(--muted)]">
          <Link href="/" className="hover:text-[var(--primary-container)]">
            Inicio
          </Link>
          <span className="material-symbols-outlined text-sm">chevron_right</span>
          <span className="font-medium text-[var(--foreground)]">Catálogo</span>
        </nav>

        {storefront.bannerImageUrl ? (
          <div className="relative mb-8 h-52 w-full overflow-hidden rounded-xl bg-[var(--surface-low)] md:h-64">
            <Image
              src={storefront.bannerImageUrl}
              alt={storefront.storeName}
              fill
              className="object-cover"
              priority
              style={{ opacity: 0.62 }}
            />
            <div
              className="absolute inset-0 flex items-end p-6"
              style={{
                background: "linear-gradient(to top, rgba(26,28,27,0.68) 0%, transparent 62%)",
              }}
            >
              <div>
                <h1
                  className="font-plus-jakarta text-3xl font-extrabold tracking-tight text-white md:text-4xl"
                >
                  {storefront.storeName}
                </h1>
                {storefront.tagline && (
                  <p className="mt-1 text-sm text-white/90">
                    {storefront.tagline}
                  </p>
                )}
              </div>
            </div>
          </div>
        ) : (
          <div className="mb-8">
            <h1 className="font-plus-jakarta text-4xl font-extrabold tracking-tight text-[var(--foreground)]">
              {storefront.storeName}
            </h1>
            {storefront.tagline && (
              <p className="mt-1 text-[var(--muted)]">{storefront.tagline}</p>
            )}
          </div>
        )}

        {(storefront.contactWhatsapp ||
          storefront.contactInstagram ||
          storefront.contactAddress) && (
          <div className="mb-8 flex flex-wrap gap-2">
            {storefront.contactWhatsapp && (
              <a
                href={`https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}`}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-full bg-[#25D366] px-4 py-2 text-xs font-medium text-white transition-opacity hover:opacity-85"
              >
                WhatsApp
              </a>
            )}
            {storefront.contactInstagram && (
              <a
                href={`https://instagram.com/${storefront.contactInstagram.replace("@", "")}`}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-full px-4 py-2 text-xs font-medium text-white transition-opacity hover:opacity-85"
                style={{
                  background:
                    "linear-gradient(135deg,#f09433,#e6683c,#dc2743,#cc2366,#bc1888)",
                }}
              >
                Instagram
              </a>
            )}
            {storefront.contactAddress && (
              <span
                className="rounded-full bg-[var(--surface-low)] px-4 py-2 text-xs text-[var(--muted)]"
                style={{
                  border: "1px solid color-mix(in srgb, var(--border) 25%, transparent)",
                }}
              >
                {storefront.contactAddress}
              </span>
            )}
          </div>
        )}

        {products.length === 0 ? (
          <EmptyCatalog storefront={storefront} />
        ) : (
          <Suspense fallback={<ProductGridSkeleton count={Math.min(products.length, 12)} />}>
            <ProductGrid
              products={products}
              showPrices={catalogConfig.showPrices}
              showCashPrice={catalogConfig.showCashPrice}
              storeName={storefront.storeName}
            />
          </Suspense>
        )}
      </div>
    </>
  );
}

function EmptyCatalog({ storefront }: { storefront: { contactWhatsapp?: string } }) {
  return (
    <div className="rounded-2xl bg-[var(--surface-low)] px-6 py-20 text-center">
      <p className="mb-4 font-plus-jakarta text-2xl font-bold text-[var(--foreground)]">
        El catálogo se está preparando
      </p>
      <p className="mb-6 text-sm text-[var(--muted)]">
        Pronto vas a encontrar todos nuestros productos aquí.
      </p>
      {storefront.contactWhatsapp && (
        <a
          href={`https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}?text=${encodeURIComponent("Hola, quiero consultar sobre sus productos")}`}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 rounded-full bg-[#25D366] px-6 py-3 text-sm font-medium text-white transition-opacity hover:opacity-85"
        >
          <span className="material-symbols-outlined text-[18px]">chat</span>
          Consultar por WhatsApp
        </a>
      )}
    </div>
  );
}
