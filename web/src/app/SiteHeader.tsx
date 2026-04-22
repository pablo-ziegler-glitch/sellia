"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

type Props = {
  storeName: string;
  whatsappHref: string | null;
};

const NAV_ITEMS = [
  { href: "/", label: "Inicio" },
  { href: "/catalogo", label: "Catálogo" },
  { href: "/#redes", label: "Redes" },
  { href: "/#contacto", label: "Contacto" },
];

export default function SiteHeader({ storeName, whatsappHref }: Props) {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  const ctaHref = whatsappHref || "/catalogo";

  return (
    <>
      <header className="fixed top-0 z-50 w-full glass-header">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6">
          <div className="flex items-center gap-3">
            <button
              type="button"
              aria-label="Abrir menú"
              className="rounded-full p-2 text-[var(--primary)] transition-colors hover:bg-[var(--surface-low)] md:hidden"
              onClick={() => setMenuOpen(true)}
            >
              <span className="material-symbols-outlined">menu</span>
            </button>
            <Link
              href="/"
              className="font-plus-jakarta text-xl font-black tracking-tight text-[var(--primary-container)]"
            >
              {storeName}
            </Link>
          </div>

          <nav className="hidden items-center gap-5 md:flex">
            {NAV_ITEMS.map((item) => {
              const active =
                item.href === "/"
                  ? pathname === "/"
                  : item.href.startsWith("/#")
                    ? false
                    : pathname.startsWith(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`rounded-lg px-3 py-2 font-plus-jakarta text-base font-bold transition-colors ${
                    active
                      ? "text-[var(--primary-container)] border-b-2 border-[var(--primary-container)]"
                      : "text-[var(--muted)] hover:bg-[var(--surface-low)] hover:text-[var(--primary-container)]"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <a
            href={ctaHref}
            target={whatsappHref ? "_blank" : undefined}
            rel={whatsappHref ? "noopener noreferrer" : undefined}
            className="inline-flex items-center gap-2 rounded-full bg-[#25D366] px-4 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90"
          >
            <span className="material-symbols-outlined text-[19px]">chat</span>
            <span className="hidden sm:inline">WhatsApp</span>
          </a>
        </div>
      </header>

      {menuOpen && (
        <div className="fixed inset-0 z-[60] flex flex-col bg-[color:rgba(250,249,247,0.95)] backdrop-blur-md">
          <div className="flex items-center justify-between px-6 py-4">
            <span className="font-plus-jakarta text-2xl font-black text-[var(--primary)]">
              {storeName}
            </span>
            <button
              type="button"
              aria-label="Cerrar menú"
              className="rounded-full p-2 text-[var(--foreground)] transition-colors hover:bg-[var(--surface-low)]"
              onClick={() => setMenuOpen(false)}
            >
              <span className="material-symbols-outlined text-2xl">close</span>
            </button>
          </div>

          <div className="flex flex-1 flex-col gap-6 px-6 py-8">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMenuOpen(false)}
                className="font-plus-jakarta text-3xl font-semibold text-[var(--muted)] transition-colors hover:text-[var(--primary)]"
              >
                {item.label}
              </Link>
            ))}
          </div>

          <div className="mt-auto bg-gradient-to-t from-[var(--surface)] to-transparent px-6 pb-10 pt-6">
            <a
              href={ctaHref}
              target={whatsappHref ? "_blank" : undefined}
              rel={whatsappHref ? "noopener noreferrer" : undefined}
              className="brand-gradient flex w-full items-center justify-center gap-2 rounded-full px-6 py-4 font-plus-jakarta text-lg font-bold text-white shadow-[0_8px_32px_rgba(0,68,170,0.2)]"
              onClick={() => setMenuOpen(false)}
            >
              <span className="material-symbols-outlined">chat</span>
              Consultar por WhatsApp
            </a>
            <p className="mt-6 text-center text-xs text-[var(--outline)]">
              © {new Date().getFullYear()} {storeName}
            </p>
          </div>
        </div>
      )}
    </>
  );
}
