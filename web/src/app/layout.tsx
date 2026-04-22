import type { Metadata } from "next";
import { Inter, Plus_Jakarta_Sans } from "next/font/google";
import Link from "next/link";
import SiteHeader from "./SiteHeader";
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
  return (
    <html lang="es">
      <body
        className={`${inter.variable} ${plusJakarta.variable} antialiased`}
      >
        <LayoutContent>{children}</LayoutContent>
      </body>
    </html>
  );
}

function LayoutContent({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const storeName = "VALKIRJA";
  const whatsapp = null;

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
