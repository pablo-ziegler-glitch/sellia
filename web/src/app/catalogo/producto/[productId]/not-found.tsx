import Link from "next/link";

export default function ProductNotFound() {
  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center px-4 text-center">
      <p
        className="text-6xl font-black tracking-widest mb-4"
        style={{ color: "var(--primary-container)", opacity: 0.2 }}
      >
        ◈
      </p>
      <h1
        className="text-xl font-bold mb-2"
        style={{ color: "var(--foreground)" }}
      >
        Producto no encontrado
      </h1>
      <p className="text-sm mb-8" style={{ color: "var(--muted)" }}>
        Este producto no está disponible o fue dado de baja.
      </p>
      <Link
        href="/catalogo"
        className="brand-gradient rounded-full px-6 py-3 text-sm font-semibold tracking-widest uppercase text-white transition-all"
      >
        Ver catálogo
      </Link>
    </div>
  );
}
