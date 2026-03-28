import Image from "next/image";
import Link from "next/link";
import {
  VALKIRJA_TENANT_ID,
  getStorefront,
  getPublicProducts,
  getCatalogConfig,
} from "@/lib/catalog";
import { formatPrice } from "@/lib/format";
import type { Metadata } from "next";

export const revalidate = 300;

export async function generateMetadata(): Promise<Metadata> {
  const storefront = await getStorefront(VALKIRJA_TENANT_ID);
  return {
    title: storefront?.storeName
      ? `${storefront.storeName} — Tienda`
      : "VALKIRJA — Tienda",
    description:
      storefront?.tagline ||
      "Descubrí nuestra colección. Calidad y estilo en cada producto.",
    openGraph: {
      ...(storefront?.bannerImageUrl
        ? { images: [{ url: storefront.bannerImageUrl }] }
        : {}),
    },
  };
}

export default async function LandingPage() {
  const [storefront, allProducts, catalogConfig] = await Promise.all([
    getStorefront(VALKIRJA_TENANT_ID),
    getPublicProducts(VALKIRJA_TENANT_ID),
    getCatalogConfig(VALKIRJA_TENANT_ID),
  ]);

  const featuredSet = new Set(catalogConfig?.featuredProductIds || []);
  const configuredFeatured = allProducts.filter((product) => featuredSet.has(product.id));
  const previewProducts = (configuredFeatured.length ? configuredFeatured : allProducts).slice(0, 6);
  const storeName = storefront?.storeName || "VALKIRJA";

  return (
    <div>
      {/* ── HERO ─────────────────────────────────────────────── */}
      <section
        className="relative min-h-[90vh] flex items-center justify-center overflow-hidden"
        style={{ background: "var(--background)" }}
      >
        {/* Banner image de fondo */}
        {storefront?.bannerImageUrl && (
          <div className="absolute inset-0">
            <Image
              src={storefront.bannerImageUrl}
              alt={storeName}
              fill
              className="object-cover"
              priority
              style={{ opacity: 0.25 }}
            />
          </div>
        )}

        {/* Gradiente sobre imagen */}
        <div
          className="absolute inset-0"
          style={{
            background:
              "linear-gradient(to bottom, transparent 40%, var(--background) 100%)",
          }}
        />

        {/* Contenido hero */}
        <div className="relative z-10 text-center px-4 max-w-3xl mx-auto">
          {storefront?.logoUrl && (
            <div className="flex justify-center mb-6">
              <Image
                src={storefront.logoUrl}
                alt={`Logo ${storeName}`}
                width={100}
                height={100}
                className="rounded-full"
                style={{ border: "2px solid var(--gold)" }}
              />
            </div>
          )}

          <h1
            className="text-6xl sm:text-8xl font-black tracking-[0.15em] uppercase mb-4"
            style={{ color: "var(--gold)" }}
          >
            {storeName}
          </h1>

          {storefront?.tagline && (
            <p
              className="text-lg sm:text-xl mb-2 font-light tracking-wide"
              style={{ color: "var(--foreground)", opacity: 0.8 }}
            >
              {storefront.tagline}
            </p>
          )}

          {storefront?.bannerSubtitle && (
            <p
              className="text-sm mb-8"
              style={{ color: "var(--muted)" }}
            >
              {storefront.bannerSubtitle}
            </p>
          )}

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mt-8">
            <Link
              href="/catalogo"
              className="px-8 py-3 text-sm font-semibold tracking-widest uppercase rounded transition-all"
              style={{
                background: "var(--gold)",
                color: "#0a0a0a",
              }}
            >
              Ver Catálogo
            </Link>
            <a
              href="#historia"
              className="px-8 py-3 text-sm font-semibold tracking-widest uppercase rounded transition-all border"
              style={{
                borderColor: "var(--gold)",
                color: "var(--gold)",
              }}
            >
              Nuestra Historia
            </a>
          </div>
        </div>

        {/* Indicador scroll */}
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 animate-bounce">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            style={{ color: "var(--gold)" }}
          >
            <path d="M12 5v14M5 12l7 7 7-7" />
          </svg>
        </div>
      </section>

      {/* ── HISTORIA ─────────────────────────────────────────── */}
      <section
        id="historia"
        className="py-24 px-4"
        style={{ background: "var(--surface)" }}
      >
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-16">
            <p
              className="text-xs tracking-[0.3em] uppercase mb-3"
              style={{ color: "var(--gold)" }}
            >
              Nuestra Historia
            </p>
            <h2
              className="text-3xl sm:text-4xl font-bold"
              style={{ color: "var(--foreground)" }}
            >
              Quiénes somos
            </h2>
          </div>

          <div
            className="grid grid-cols-1 md:grid-cols-2 gap-12 items-center"
          >
            <div className="space-y-5" style={{ color: "var(--foreground)", opacity: 0.85 }}>
              <p className="text-base leading-relaxed">
                <strong style={{ color: "var(--gold)" }}>VALKIRJA</strong> nació
                de la pasión por reunir en un solo lugar productos de calidad
                excepcional, seleccionados con criterio y dedicación. Nuestro
                nombre rinde homenaje a las valkirias nórdicas: guerreras que
                elegían lo mejor, lo más valioso, lo más auténtico.
              </p>
              <p className="text-base leading-relaxed">
                Desde nuestros comienzos, nuestra misión fue clara: ofrecerle a
                cada cliente una experiencia de compra cuidada, con productos que
                superen las expectativas y un servicio que deja huella.
              </p>
              <p className="text-base leading-relaxed">
                Cada artículo de nuestro catálogo pasa por una selección rigurosa
                antes de llegar a tus manos. Porque creemos que lo que elegís
                dice quién sos.
              </p>
            </div>

            <div
              className="rounded-xl p-8 text-center"
              style={{
                background: "var(--surface-2)",
                border: "1px solid var(--border)",
              }}
            >
              <div className="space-y-6">
                <Stat label="Productos disponibles" value={`${allProducts.length}+`} />
                <div style={{ height: "1px", background: "var(--border)" }} />
                <Stat label="Compromiso" value="100%" />
                <div style={{ height: "1px", background: "var(--border)" }} />
                <Stat label="Calidad garantizada" value="Siempre" />
              </div>

              {/* Contacto */}
              {(storefront?.contactWhatsapp || storefront?.contactInstagram) && (
                <div className="mt-8 space-y-3">
                  {storefront.contactWhatsapp && (
                    <a
                      href={`https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-center gap-2 w-full py-2.5 rounded text-sm font-medium transition-opacity hover:opacity-80"
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
                      className="flex items-center justify-center gap-2 w-full py-2.5 rounded text-sm font-medium transition-opacity hover:opacity-80"
                      style={{
                        background:
                          "linear-gradient(135deg,#f09433,#e6683c,#dc2743,#cc2366,#bc1888)",
                        color: "#fff",
                      }}
                    >
                      @{storefront.contactInstagram.replace("@", "")}
                    </a>
                  )}
                  {storefront.contactAddress && (
                    <p
                      className="text-xs mt-2"
                      style={{ color: "var(--muted)" }}
                    >
                      {storefront.contactAddress}
                    </p>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* ── VISTA PREVIA CATÁLOGO ────────────────────────────── */}
      {previewProducts.length > 0 && (
        <section className="py-24 px-4" style={{ background: "var(--background)" }}>
          <div className="max-w-6xl mx-auto">
            <div className="text-center mb-12">
              <p
                className="text-xs tracking-[0.3em] uppercase mb-3"
                style={{ color: "var(--gold)" }}
              >
                Catálogo
              </p>
              <h2
                className="text-3xl sm:text-4xl font-bold mb-4"
                style={{ color: "var(--foreground)" }}
              >
                Algunos de nuestros productos
              </h2>
              <p style={{ color: "var(--muted)" }} className="text-sm">
                Una pequeña muestra de lo que tenemos para vos.
              </p>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 sm:gap-6">
              {previewProducts.map((product) => (
                <Link
                  key={product.id}
                  href={`/catalogo/producto/${product.id}`}
                  className="group rounded-xl overflow-hidden transition-all"
                  style={{
                    background: "var(--surface)",
                    border: "1px solid var(--border)",
                  }}
                >
                  {product.imageUrl ? (
                    <div className="relative w-full aspect-square overflow-hidden">
                      <Image
                        src={product.imageUrl}
                        alt={product.name}
                        fill
                        className="object-cover transition-transform duration-500 group-hover:scale-105"
                        sizes="(max-width: 640px) 50vw, 33vw"
                      />
                    </div>
                  ) : (
                    <div
                      className="w-full aspect-square flex items-center justify-center text-xs"
                      style={{
                        background: "var(--surface-2)",
                        color: "var(--muted)",
                      }}
                    >
                      Sin imagen
                    </div>
                  )}
                  <div className="p-3">
                    <h3
                      className="text-sm font-medium line-clamp-2"
                      style={{ color: "var(--foreground)" }}
                    >
                      {product.name}
                    </h3>
                    {product.category && (
                      <p className="text-xs mt-0.5" style={{ color: "var(--muted)" }}>
                        {product.category}
                      </p>
                    )}
                    {catalogConfig?.showPrices && product.listPrice != null && (
                      <p
                        className="text-sm font-bold mt-2"
                        style={{ color: "var(--gold)" }}
                      >
                        {formatPrice(product.listPrice)}
                      </p>
                    )}
                  </div>
                </Link>
              ))}
            </div>

            {/* CTA al catálogo completo */}
            <div className="text-center mt-12">
              <Link
                href="/catalogo"
                className="inline-flex items-center gap-2 px-10 py-4 rounded text-sm font-semibold tracking-widest uppercase transition-all"
                style={{
                  background: "var(--gold)",
                  color: "#0a0a0a",
                }}
              >
                Ver catálogo completo
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </Link>
              <p className="text-xs mt-3" style={{ color: "var(--muted)" }}>
                {allProducts.length} productos disponibles
              </p>
              <p className="text-xs mt-2">
                <Link href="/productos-destacados" style={{ color: "var(--gold)" }}>
                  Ver página de destacados
                </Link>
              </p>
            </div>
          </div>
        </section>
      )}

      {/* ── CTA FINAL ────────────────────────────────────────── */}
      <section
        className="py-24 px-4 text-center"
        style={{ background: "var(--surface)" }}
      >
        <div className="max-w-2xl mx-auto">
          <h2
            className="text-3xl sm:text-4xl font-black tracking-wide uppercase mb-4"
            style={{ color: "var(--gold)" }}
          >
            {storeName}
          </h2>
          <p className="text-base mb-8" style={{ color: "var(--muted)" }}>
            Explorá el catálogo completo y encontrá lo que estabas buscando.
          </p>
          <Link
            href="/catalogo"
            className="inline-block px-10 py-4 rounded text-sm font-semibold tracking-widest uppercase border transition-all"
            style={{
              borderColor: "var(--gold)",
              color: "var(--gold)",
            }}
          >
            Acceder al Catálogo
          </Link>
        </div>
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p
        className="text-3xl font-black"
        style={{ color: "var(--gold)" }}
      >
        {value}
      </p>
      <p className="text-xs mt-1 tracking-wide uppercase" style={{ color: "var(--muted)" }}>
        {label}
      </p>
    </div>
  );
}
