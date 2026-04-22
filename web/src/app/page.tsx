import Image from "next/image";
import Link from "next/link";
import type { Metadata } from "next";
import {
  VALKIRJA_TENANT_ID,
  getCatalogConfig,
  getPublicProducts,
  getStorefront,
} from "@/lib/catalog";
import { formatPrice } from "@/lib/format";

export const revalidate = 300;

export async function generateMetadata(): Promise<Metadata> {
  const storefront = await getStorefront(VALKIRJA_TENANT_ID);
  const storeName = storefront?.storeName || "VALKIRJA";
  return {
    title: `${storeName} - Tienda`,
    description:
      storefront?.tagline ||
      "Descubre productos únicos y apoya comercios de tu comunidad.",
    openGraph: {
      title: `${storeName} - Tienda`,
      description:
        storefront?.tagline ||
        "Descubre productos únicos y apoya comercios de tu comunidad.",
      ...(storefront?.bannerImageUrl
        ? { images: [{ url: storefront.bannerImageUrl }] }
        : {}),
    },
  };
}

export default async function HomePage() {
  const [storefront, products, catalogConfig] = await Promise.all([
    getStorefront(VALKIRJA_TENANT_ID),
    getPublicProducts(VALKIRJA_TENANT_ID),
    getCatalogConfig(VALKIRJA_TENANT_ID),
  ]);

  const storeName = storefront?.storeName || "VALKIRJA";
  const featuredSet = new Set(catalogConfig.featuredProductIds || []);
  const featuredProducts = (
    products.filter((p) => featuredSet.has(p.id)).length
      ? products.filter((p) => featuredSet.has(p.id))
      : products
  ).slice(0, 8);

  const whatsappHref =
    storefront?.contactWhatsapp && storefront.contactWhatsapp.trim()
      ? `https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}`
      : "/catalogo";

  return (
    <div className="mx-auto max-w-7xl space-y-12 px-4 pb-10 md:px-8">
      <section className="flex flex-col items-center pb-4 pt-8 text-center">
        <h1 className="max-w-3xl font-plus-jakarta text-4xl font-extrabold leading-tight tracking-tight text-[var(--foreground)] md:text-6xl">
          Descubre lo mejor de tu comunidad local.
        </h1>
        <p className="mt-4 max-w-2xl text-base text-[var(--muted)] md:text-xl">
          {storefront?.tagline ||
            "Encuentra productos únicos y apoya a los comercios de tu zona. Calidad y cercanía a un clic de distancia."}
        </p>
        <div className="mt-6 w-full sm:w-auto">
          <Link
            href="/catalogo"
            className="brand-gradient inline-flex w-full items-center justify-center rounded-full px-8 py-4 font-plus-jakarta text-lg font-bold text-white shadow-[0_8px_32px_rgba(0,68,170,0.15)] transition-opacity hover:opacity-95 sm:w-auto"
          >
            Ver catálogo
          </Link>
        </div>

        <div className="relative mt-10 aspect-[4/3] w-full max-w-5xl overflow-hidden rounded-xl bg-[var(--surface-low)] soft-pop sm:aspect-video">
          {storefront?.bannerImageUrl ? (
            <Image
              src={storefront.bannerImageUrl}
              alt={storeName}
              fill
              priority
              className="object-cover"
            />
          ) : (
            <div className="absolute inset-0 bg-[var(--surface-high)]" />
          )}
          <div className="absolute right-4 top-4 flex items-center gap-2 rounded-full bg-[var(--secondary-container)] px-3 py-1 text-sm font-medium text-[var(--on-secondary-container)]">
            <span className="h-2 w-2 animate-pulse rounded-full bg-[var(--secondary)]" />
            Abierto ahora
          </div>
        </div>
      </section>

      <section className="space-y-5 overflow-hidden">
        <div className="flex items-center justify-between">
          <h2 className="font-plus-jakarta text-2xl font-bold text-[var(--foreground)] md:text-3xl">
            {catalogConfig.featuredTitle || "Productos destacados"}
          </h2>
          <Link
            href="/catalogo"
            className="inline-flex items-center gap-1 text-sm font-medium text-[var(--primary-container)] hover:underline"
          >
            Ver todos
            <span className="material-symbols-outlined text-base">
              arrow_forward
            </span>
          </Link>
        </div>

        <div className="no-scrollbar -mx-4 flex snap-x snap-mandatory gap-5 overflow-x-auto px-4 pb-4 sm:mx-0 sm:px-0">
          {featuredProducts.map((product) => (
            <Link
              key={product.id}
              href={`/catalogo/producto/${product.id}`}
              className="flex w-[280px] flex-none snap-start flex-col overflow-hidden rounded-xl bg-[var(--surface-pop)] soft-pop transition-transform hover:-translate-y-0.5 sm:w-[320px]"
            >
              <div className="relative aspect-square bg-[var(--surface-low)]">
                {product.imageUrl ? (
                  <Image
                    src={product.imageUrl}
                    alt={product.name}
                    fill
                    className="object-cover"
                    sizes="320px"
                  />
                ) : (
                  <div className="absolute inset-0 bg-[var(--surface-high)]" />
                )}
                {product.category && (
                  <span className="absolute left-3 top-3 rounded-md bg-[var(--primary-fixed)] px-2 py-1 text-xs font-semibold text-[var(--on-primary-fixed)]">
                    {product.category}
                  </span>
                )}
              </div>
              <div className="space-y-2 p-4">
                <h3 className="font-plus-jakarta text-lg font-bold text-[var(--foreground)]">
                  {product.name}
                </h3>
                <p className="text-sm text-[var(--muted)]">{storeName}</p>
                {catalogConfig.showPrices && product.listPrice != null && (
                  <div className="flex items-center justify-between pt-2">
                    <span className="font-plus-jakarta font-bold text-[var(--primary)]">
                      {formatPrice(product.listPrice)}
                    </span>
                    <span className="material-symbols-outlined rounded-full bg-[var(--surface-high)] p-2 text-[18px] text-[var(--foreground)]">
                      add
                    </span>
                  </div>
                )}
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section
        id="redes"
        className="rounded-2xl bg-[var(--surface-low)] px-6 py-8 md:px-10"
      >
        <h2 className="font-plus-jakarta text-2xl font-bold text-[var(--foreground)]">
          Seguinos y mantenete cerca
        </h2>
        <p className="mt-2 text-[var(--muted)]">
          Novedades de productos, reposiciones y promociones de la tienda.
        </p>
        <div className="mt-5 flex flex-wrap gap-3">
          {storefront?.contactInstagram && (
            <a
              href={`https://instagram.com/${storefront.contactInstagram.replace("@", "")}`}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-full bg-[var(--surface-pop)] px-4 py-2 text-sm font-medium text-[var(--foreground)] soft-pop"
            >
              Instagram
            </a>
          )}
          <a
            href={whatsappHref}
            target={whatsappHref.startsWith("https://wa.me") ? "_blank" : undefined}
            rel={whatsappHref.startsWith("https://wa.me") ? "noopener noreferrer" : undefined}
            className="rounded-full bg-[#25D366] px-4 py-2 text-sm font-medium text-white"
          >
            WhatsApp
          </a>
        </div>
      </section>

      <section id="contacto" className="rounded-2xl bg-[var(--surface)] px-6 py-8">
        <h2 className="font-plus-jakarta text-2xl font-bold text-[var(--foreground)]">
          Contacto
        </h2>
        <div className="mt-3 space-y-2 text-[var(--muted)]">
          {storefront?.contactAddress && <p>{storefront.contactAddress}</p>}
          {storefront?.contactWhatsapp && <p>{storefront.contactWhatsapp}</p>}
        </div>
      </section>
    </div>
  );
}
