import type { Metadata } from "next";
import { Geist } from "next/font/google";
import Link from "next/link";
import "./globals.css";

const geist = Geist({
  variable: "--font-geist",
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
        className={`${geist.variable} font-sans antialiased`}
        style={{ background: "var(--background)", color: "var(--foreground)" }}
      >
        <header
          style={{
            background: "var(--surface)",
            borderBottom: "1px solid var(--border)",
          }}
        >
          <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
            <Link
              href="/"
              className="text-xl font-bold tracking-[0.2em] uppercase"
              style={{ color: "var(--gold)" }}
            >
              VALKIRJA
            </Link>
            <nav className="flex items-center gap-6 text-sm">
              <Link
                href="/catalogo"
                className="transition-colors"
                style={{ color: "var(--muted)" }}
                onMouseEnter={(e) =>
                  ((e.currentTarget as HTMLAnchorElement).style.color =
                    "var(--gold)")
                }
                onMouseLeave={(e) =>
                  ((e.currentTarget as HTMLAnchorElement).style.color =
                    "var(--muted)")
                }
              >
                Catálogo
              </Link>
            </nav>
          </div>
        </header>

        <main>{children}</main>

        <footer
          style={{
            borderTop: "1px solid var(--border)",
            background: "var(--surface)",
          }}
          className="mt-20 py-8"
        >
          <div className="max-w-6xl mx-auto px-4 text-center text-sm" style={{ color: "var(--muted)" }}>
            <p
              className="text-base font-bold tracking-widest mb-2"
              style={{ color: "var(--gold)" }}
            >
              VALKIRJA
            </p>
            <p>&copy; {new Date().getFullYear()} — Todos los derechos reservados</p>
          </div>
        </footer>
      </body>
    </html>
  );
}
