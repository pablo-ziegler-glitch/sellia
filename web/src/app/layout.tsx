import type { Metadata } from "next";
import { Inter, Plus_Jakarta_Sans } from "next/font/google";
import Link from "next/link";
import SiteHeader from "./SiteHeader";
import { getStorefront, VALKIRJA_TENANT_ID } from "@/lib/catalog";
import "./globals.css";

const inter = Inter({
  variable: "--font-body",
  subsets: ["latin"],
});

const plusJakarta = Plus_Jakarta_Sans({
  variable: "--font-plus-jakarta",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "VALKIRJA - Tienda",
  description: "Descubrí nuestra colección. Calidad y estilo en cada producto.",
  openGraph: {
    siteName: "VALKIRJA",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const storefrontPromise = getStorefront(VALKIRJA_TENANT_ID);
  return (
    <html lang="es">
      <body
        className={`${inter.variable} ${plusJakarta.variable} antialiased`}
      >
        <LayoutContent storefrontPromise={storefrontPromise}>{children}</LayoutContent>
      </body>
    </html>
  );
}

async function LayoutContent({
  children,
  storefrontPromise,
}: Readonly<{
  children: React.ReactNode;
  storefrontPromise: ReturnType<typeof getStorefront>;
}>) {
  const storefront = await storefrontPromise;
  const storeName = storefront?.storeName || "VALKIRJA";
  const whatsapp =
    storefront?.contactWhatsapp && storefront.contactWhatsapp.trim()
      ? `https://wa.me/${storefront.contactWhatsapp.replace(/\D/g, "")}`
      : null;

  return (
    <>
      <SiteHeader storeName={storeName} whatsappHref={whatsapp} />
      <main className="pt-20">{children}</main>
      <footer className="mt-16 bg-[var(--surface-low)]">
        <div className="mx-auto grid max-w-7xl gap-8 px-6 py-12 md:grid-cols-3">
          <div className="space-y-3">
            <p className="font-plus-jakarta text-xl font-bold text-[var(--primary-container)]">
              {storeName}
            </p>
            <p className="text-sm text-[var(--muted)]">
              © {new Date().getFullYear()} {storeName}. El comercio local a tu alcance.
            </p>
          </div>
          <div className="md:col-span-2 grid grid-cols-2 gap-4 text-sm">
            <Link href="/" className="text-[var(--primary-container)] hover:underline">
              Inicio
            </Link>
            <Link href="/catalogo" className="text-[var(--muted)] hover:text-[var(--primary-container)]">
              Catálogo
            </Link>
            <Link href="/#redes" className="text-[var(--muted)] hover:text-[var(--primary-container)]">
              Redes
            </Link>
            <Link href="/#contacto" className="text-[var(--muted)] hover:text-[var(--primary-container)]">
              Contacto
            </Link>
          </div>
        </div>
      </footer>
    </>
  );
}
