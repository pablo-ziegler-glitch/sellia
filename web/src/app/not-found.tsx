import Link from "next/link";

export default function NotFound() {
  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center px-4 text-center">
      <p
        className="text-8xl font-black tracking-widest mb-2"
        style={{ color: "var(--gold)", opacity: 0.3 }}
      >
        404
      </p>
      <h1
        className="text-2xl font-bold mb-2"
        style={{ color: "var(--foreground)" }}
      >
        Página no encontrada
      </h1>
      <p className="text-sm mb-8" style={{ color: "var(--muted)" }}>
        La página que buscás no existe o fue movida.
      </p>
      <div className="flex flex-col sm:flex-row gap-3">
        <Link
          href="/"
          className="px-6 py-3 rounded text-sm font-semibold tracking-widest uppercase transition-all"
          style={{ background: "var(--gold)", color: "#0a0a0a" }}
        >
          Ir al inicio
        </Link>
        <Link
          href="/catalogo"
          className="px-6 py-3 rounded text-sm font-semibold tracking-widest uppercase border transition-all"
          style={{ borderColor: "var(--gold)", color: "var(--gold)" }}
        >
          Ver catálogo
        </Link>
      </div>
    </div>
  );
}
